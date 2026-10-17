package com.powerk.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

enum class Link { CONNECTING, LIVE, OFFLINE }

private const val LIVE_RESYNC_MS = 60_000L
private const val OFFLINE_POLL_MS = 8_000L
private const val LIST_CAP = 200

/**
 * Single app-wide store. State arrives over the WebSocket; HTTP is only used for the initial load,
 * reconnect catch-up, commands, and a slow safety resync. Nothing here ever blanks the UI on error.
 */
class StripsViewModel : ViewModel() {
    var strips by mutableStateOf<List<Strip>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var link by mutableStateOf(Link.CONNECTING)
        private set
    var notifications by mutableStateOf<List<NotificationItem>>(emptyList())
        private set
    var events by mutableStateOf<List<EventItem>>(emptyList())
        private set
    var schedules by mutableStateOf<List<ScheduleItem>>(emptyList())
        private set
    var rules by mutableStateOf<List<RuleItem>>(emptyList())
        private set
    var settings by mutableStateOf<AppSettings?>(null)
        private set
    val unread by derivedStateOf { notifications.count { it.readAt == null } }
    val snackbar = SnackbarHostState()
    val energy = mutableStateMapOf<String, EnergyReport>()
    val relayTimers = mutableStateMapOf<String, RelayTimer>()

    private val syncLock = Mutex()
    private val realtime = Realtime(
        scope = viewModelScope,
        onConnected = { viewModelScope.launch { updateLink(Link.LIVE); sync(full = true) } },
        onDisconnected = { viewModelScope.launch { updateLink(Link.OFFLINE) } },
        onEvent = { message -> viewModelScope.launch { apply(message) } },
    )

    init {
        realtime.start()
        viewModelScope.launch {
            sync(full = true)
            while (true) {
                if (link == Link.LIVE) {
                    delay(LIVE_RESYNC_MS)
                    sync(full = true)
                } else {
                    delay(OFFLINE_POLL_MS)
                    sync(full = false)
                }
            }
        }
    }

    /** Called when the app returns to the foreground. */
    fun onResume() {
        if (link != Link.LIVE) realtime.reconnect()
        viewModelScope.launch { sync(full = false) }
    }

    /** Called after the server address or token changes. */
    fun restart() {
        link = Link.CONNECTING
        realtime.reconnect()
        viewModelScope.launch { sync(full = true) }
    }

    fun refresh() {
        viewModelScope.launch { sync(full = true) }
    }

    private fun updateLink(next: Link) {
        if (link == next) return
        link = next
        Diag.linkState = next.name
        Diag.i("link", next.name)
    }

    private suspend fun sync(full: Boolean) {
        syncLock.withLock {
            val fresh = runCatching { Api.snapshot(Server.base, Server.token) }.onFailure { Diag.w("sync", "snapshot failed: ${it.message}") }.getOrNull()
            if (fresh != null) {
                if (fresh != strips) strips = fresh
                loaded = true
            } else if (link == Link.CONNECTING) {
                updateLink(Link.OFFLINE)
            }
            if (full && fresh != null) loadExtras()
        }
    }

    private suspend fun loadExtras() {
        val base = Server.base
        val token = Server.token
        runCatching { Api.notifications(base, token) }.onSuccess { notifications = it }
        runCatching { Api.events(base, token) }.onSuccess { events = it }
        runCatching { Api.schedules(base, token) }.onSuccess { schedules = it }
        runCatching { Api.rules(base, token) }.onSuccess { rules = it }
        runCatching { Api.settings(base, token) }.onSuccess { applySettings(it) }
        runCatching { Api.activeTimers(base, token) }.onSuccess { list ->
            val keep = list.associateBy { timerKey(it.stripId, it.relayIndex + 1) }
            relayTimers.keys.filterNot { it in keep }.forEach { relayTimers.remove(it) }
            relayTimers.putAll(keep)
        }
    }

    private fun apply(m: JSONObject) {
        when (m.optString("type")) {
            "relay-state" -> patch(m.optString("stripId")) { s ->
                val at = Api.parseIsoMs(m.optString("changedAt")).takeIf { it > 0 } ?: System.currentTimeMillis()
                s.withOutlet(m.optInt("relayIndex") + 1) { o ->
                    val on = m.optString("state") == "on"
                    if (o.on == on) o else o.copy(on = on, changedAtMs = at)
                }
            }
            "power-tick" -> applyTick(m)
            "settings-changed" -> applySettings(Api.parseSettings(m.getJSONObject("settings")))
            "relay-timer" -> {
                val key = timerKey(m.optString("stripId"), m.optInt("relayIndex") + 1)
                if (m.isNull("restoreAt")) relayTimers.remove(key)
                else relayTimers[key] = Api.parseTimer(m)
            }
            "strip-connectivity" -> patch(m.optString("stripId")) { it.copy(online = m.optString("connectivity") == "online") }
            "strip-added", "strip-updated" -> upsert(Api.parseStrip(m.getJSONObject("strip")))
            "strip-removed" -> strips = strips.filterNot { it.mac == m.optString("stripId") }
            "notification" -> {
                val item = Api.parseNotification(m.getJSONObject("notification"))
                if (notifications.none { it.id == item.id }) notifications = (listOf(item) + notifications).take(LIST_CAP)
            }
            "event-logged" -> {
                val item = Api.parseEvent(m.getJSONObject("event"))
                if (events.none { it.id == item.id }) events = (listOf(item) + events).take(LIST_CAP)
            }
            "schedule-fired" -> viewModelScope.launch { runCatching { Api.schedules(Server.base, Server.token) }.onSuccess { schedules = it } }
            "rules-changed" -> viewModelScope.launch { runCatching { Api.rules(Server.base, Server.token) }.onSuccess { rules = it } }
        }
    }

    private fun applySettings(s: AppSettings) {
        if (s != settings) settings = s
        AppTheme.applyRemote(s.theme, s.accentColor)
    }

    private fun applyTick(m: JSONObject) {
        val relays = m.optJSONArray("relays")
        val telemetry = m.optJSONObject("telemetry")
        val extra = telemetry?.optJSONArray("outlets")
        patch(m.optString("stripId")) { s ->
            val outlets = s.outlets.map { o ->
                var next = o
                for (i in 0 until (relays?.length() ?: 0)) {
                    val r = relays!!.getJSONObject(i)
                    if (r.optInt("relayIndex") + 1 == o.n) next = next.copy(powerW = r.optDouble("watts", 0.0))
                }
                for (i in 0 until (extra?.length() ?: 0)) {
                    val e = extra!!.getJSONObject(i)
                    if (e.optInt("relayIndex") + 1 == o.n) next = next.copy(energyKwh = e.optDouble("energyKwh", 0.0), tempC = e.optInt("temperatureC"))
                }
                next
            }
            s.copy(
                outlets = outlets,
                powerW = m.optDouble("totalWatts", s.powerW),
                energyKwh = telemetry?.optDouble("totalEnergyKwh", s.energyKwh) ?: s.energyKwh,
                voltage = if (telemetry != null && !telemetry.isNull("voltageV")) telemetry.optDouble("voltageV") else s.voltage,
                currentA = if (telemetry != null && !telemetry.isNull("currentA")) telemetry.optDouble("currentA") else s.currentA,
                rssi = if (telemetry != null && !telemetry.isNull("rssi")) telemetry.optInt("rssi") else s.rssi,
                fw = telemetry?.optString("fw")?.ifBlank { null } ?: s.fw,
            )
        }
    }

    private fun upsert(strip: Strip) {
        val i = strips.indexOfFirst { it.mac == strip.mac }
        strips = if (i < 0) strips + strip else strips.toMutableList().also { it[i] = strip }
    }

    private fun patch(id: String, change: (Strip) -> Strip) {
        val current = strips
        val i = current.indexOfFirst { it.mac == id }
        if (i < 0) return
        val next = change(current[i]).let { s -> s.copy(on = s.outlets.any { it.on }) }
        if (next != current[i]) strips = current.toMutableList().also { it[i] = next }
    }

    /** Outlet 0 means every outlet. Applied instantly, rolled back if the server rejects it. */
    fun command(mac: String, outlet: Int, on: Boolean) {
        val before = strips.firstOrNull { it.mac == mac } ?: return
        val now = System.currentTimeMillis()
        // Flip the UI immediately; the server confirmation arrives later over the live link.
        patch(mac) { s ->
            s.copy(outlets = s.outlets.map { o -> if ((outlet == 0 || o.n == outlet) && o.on != on) o.copy(on = on, changedAtMs = now) else o })
        }
        viewModelScope.launch {
            try {
                Api.setOutlet(Server.base, Server.token, mac, outlet, on)
            } catch (e: Exception) {
                Diag.w("api", "setOutlet failed: ${e.message}")
                patch(mac) { s -> s.copy(outlets = s.outlets.map { o -> o.copy(on = before.outlet(o.n)?.on ?: o.on) }) }
                snackbar.showSnackbar(e.message ?: "Command failed")
            }
        }
    }

    fun updateIcon(mac: String, outlet: Int?, icon: String) {
        patch(mac) { s -> if (outlet == null) s.copy(icon = icon) else s.withOutlet(outlet) { it.copy(icon = icon) } }
        launchApi("Icon update failed") {
            if (outlet == null) Api.updateStripIcon(Server.base, Server.token, mac, icon)
            else Api.updateOutletIcon(Server.base, Server.token, mac, outlet, icon)
        }
    }

    fun renameOutlet(mac: String, outlet: Int, name: String) {
        patch(mac) { s -> s.withOutlet(outlet) { it.copy(name = name) } }
        launchApi("Rename failed") { Api.updateOutletName(Server.base, Server.token, mac, outlet, name) }
    }

    fun startTimer(mac: String, outlet: Int, on: Boolean, minutes: Int, mode: String) {
        launchApi("Timer failed") {
            val t = Api.startOutletTimer(Server.base, Server.token, mac, outlet, on, minutes, mode)
            relayTimers[timerKey(mac, outlet)] = t
        }
    }

    fun cancelTimer(mac: String, outlet: Int) {
        relayTimers.remove(timerKey(mac, outlet))
        launchApi("Cancel failed") { Api.cancelOutletTimer(Server.base, Server.token, mac, outlet) }
    }

    suspend fun outletHistory(mac: String, outlet: Int): List<EventItem> = runCatching { Api.outletEvents(Server.base, Server.token, mac, outlet) }.getOrDefault(emptyList())

    fun renameStrip(mac: String, name: String) {
        patch(mac) { it.copy(name = name) }
        launchApi("Rename failed") { Api.updateStrip(Server.base, Server.token, mac, name) }
    }

    fun setLocation(mac: String, location: String?) {
        patch(mac) { it.copy(location = location) }
        launchApi("Move failed") { Api.updateStripLocation(Server.base, Server.token, mac, location) }
    }

    fun deleteStrip(mac: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                Api.deleteStrip(Server.base, Server.token, mac)
                strips = strips.filterNot { it.mac == mac }
                onDone()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Delete failed")
            }
        }
    }

    fun markRead(id: String) {
        notifications = notifications.map { if (it.id == id && it.readAt == null) it.copy(readAt = "now") else it }
        launchApi("Failed") { Api.markNotificationRead(Server.base, Server.token, id) }
    }

    fun markAllRead() {
        notifications = notifications.map { if (it.readAt == null) it.copy(readAt = "now") else it }
        launchApi("Failed") { Api.markAllNotificationsRead(Server.base, Server.token) }
    }

    fun deleteNotification(id: String) {
        notifications = notifications.filterNot { it.id == id }
        launchApi("Failed") { Api.deleteNotification(Server.base, Server.token, id) }
    }

    fun clearNotifications() {
        notifications = emptyList()
        launchApi("Failed") { Api.clearNotifications(Server.base, Server.token) }
    }

    fun clearEvents() {
        events = emptyList()
        launchApi("Failed") { Api.clearEvents(Server.base, Server.token) }
    }

    fun saveSchedule(item: ScheduleItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val saved = Api.saveSchedule(Server.base, Server.token, item)
                schedules = if (schedules.any { it.id == saved.id }) schedules.map { if (it.id == saved.id) saved else it } else schedules + saved
                onDone()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Save failed")
            }
        }
    }

    fun toggleSchedule(item: ScheduleItem) {
        val next = item.copy(enabled = !item.enabled)
        schedules = schedules.map { if (it.id == item.id) next else it }
        launchApi("Failed") { Api.saveSchedule(Server.base, Server.token, next) }
    }

    fun deleteSchedule(id: String) {
        schedules = schedules.filterNot { it.id == id }
        launchApi("Delete failed") { Api.deleteSchedule(Server.base, Server.token, id) }
    }

    fun saveRule(item: RuleItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val saved = Api.saveRule(Server.base, Server.token, item)
                rules = if (rules.any { it.id == saved.id }) rules.map { if (it.id == saved.id) saved else it } else rules + saved
                onDone()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Save failed")
            }
        }
    }

    fun toggleRule(item: RuleItem) {
        val next = item.copy(enabled = !item.enabled)
        rules = rules.map { if (it.id == item.id) next else it }
        launchApi("Failed") { Api.saveRule(Server.base, Server.token, next) }
    }

    fun deleteRule(id: String) {
        rules = rules.filterNot { it.id == id }
        launchApi("Delete failed") { Api.deleteRule(Server.base, Server.token, id) }
    }

    fun loadEnergy(stripId: String, days: Int) {
        viewModelScope.launch {
            runCatching { Api.energy(Server.base, Server.token, stripId, days) }.onSuccess { energy["$days:$stripId"] = it }
        }
    }

    private fun reloadEnergy() {
        energy.keys.toList().forEach { key -> loadEnergy(key.substringAfter(':'), key.substringBefore(':').toInt()) }
    }

    fun resetEnergy(stripId: String) {
        launchApi("Reset failed") {
            Api.resetEnergy(Server.base, Server.token, stripId)
            reloadEnergy()
        }
    }
    fun saveSettings(next: AppSettings) {
        settings = next
        launchApi("Save failed") { settings = Api.saveSettings(Server.base, Server.token, next); reloadEnergy() }
    }

    private fun launchApi(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Diag.w("api", "$failure: ${e.message}")
                snackbar.showSnackbar(e.message ?: failure)
                sync(full = true)
            }
        }
    }

    override fun onCleared() {
        realtime.stop()
    }
}

fun timerKey(mac: String, outlet: Int) = "$mac:$outlet"

fun Strip.withOutlet(n: Int, change: (Outlet) -> Outlet): Strip =
    copy(outlets = outlets.map { if (it.n == n) change(it) else it })
