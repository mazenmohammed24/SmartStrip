package com.powerk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets

// ASCII colon-delimited setup-mode protocol, confirmed by three independent sources against the
// same hardware/firmware (0.1.54-1.0.66): AmrEmad0/MTTL-W01_APP's hardware-tested protocol guide,
// ttaengz/mttl-w01-matterbridge, and the shipping WattQ app's decompiled strings.
private const val MTTL_SETUP_HOST = "192.168.1.1"
private const val MTTL_SETUP_PORT = 30300

data class Outlet(
    val n: Int,
    val on: Boolean,
    val powerW: Double,
    val energyKwh: Double,
    val tempC: Int,
    val icon: String,
    val name: String = "",
    val changedAtMs: Long = 0L,
)

data class Strip(
    val mac: String,
    val name: String,
    val model: String,
    val fw: String,
    val online: Boolean,
    val on: Boolean,
    val powerW: Double,
    val energyKwh: Double,
    val voltage: Double?,
    val currentA: Double?,
    val rssi: Int?,
    val icon: String,
    val location: String? = null,
    val outlets: List<Outlet>,
)

data class RelayTimer(val stripId: String, val relayIndex: Int, val mode: String, val state: String, val restoreAtMs: Long)
data class DiscoveredStrip(val driverStripId: String, val model: String, val suggestedName: String, val ipAddress: String?, val relayCount: Int)
data class ProvisionResult(val registered: Boolean, val stripName: String?, val message: String)
data class ServerAddress(val host: String, val ip: String)
data class PowerSummary(val todayKwh: Double, val todayCost: Double, val currency: String, val liveWatts: Double)
data class DayEnergy(val date: String, val kwh: Double, val cost: Double)
data class OutletEnergy(val relayIndex: Int, val name: String, val kwh: Double, val cost: Double)
data class TariffLevel(val label: String, val rate: Double, val service: Double)
data class TariffInfo(val mode: String, val name: String, val monthToDateKwh: Double, val currentLevel: Int, val levels: List<TariffLevel>, val note: String)
data class EnergyReport(val days: Int, val totalKwh: Double, val previousKwh: Double, val cost: Double, val currency: String, val ratePerKwh: Double, val tariff: TariffInfo, val daily: List<DayEnergy>, val outlets: List<OutletEnergy>)
data class RuleItem(val id: String, val type: String, val stripId: String, val relayIndex: Int?, val name: String, val enabled: Boolean, val params: JSONObject, val status: String? = null)
data class EventItem(val id: String, val type: String, val message: String, val stripName: String?, val createdAt: String)
data class NotificationItem(val id: String, val title: String, val body: String, val severity: String, val readAt: String?, val createdAt: String)
data class ScheduleItem(
    val id: String,
    val name: String,
    val stripId: String,
    val relayIndex: Int?,
    val action: String,
    val time: String,
    val daysOfWeek: List<Int>,
    val enabled: Boolean,
    val timezone: String = "Africa/Cairo",
    val repeat: String = "weekly",
)
data class AppSettings(
    val theme: String,
    val energyUnit: String,
    val currency: String,
    val ratePerKwh: Double,
    val tariffMode: String,
    val overloadThresholdWatts: Int,
    val timeFormat: String,
    val accentColor: String = AppTheme.DEFAULT_ACCENT,
)


/** Outlet by its number (1..4) - never by list position. */
fun Strip.outletOn(n: Int): Boolean = outlets.firstOrNull { it.n == n }?.on ?: false

fun Strip.outlet(n: Int): Outlet? = outlets.firstOrNull { it.n == n }

class ServerException(message: String) : Exception(message)

/** Talks to our self-hosted SmartStrip API. */
object Api {

    suspend fun snapshot(base: String, token: String): List<Strip> = withContext(Dispatchers.IO) {
        val array = JSONObject(request("$base/api/strips", null, token)).optJSONArray("strips")
        (0 until (array?.length() ?: 0)).map { parseStrip(array!!.getJSONObject(it)) }
    }
    suspend fun setOutlet(base: String, token: String, stripId: String, outlet: Int, on: Boolean) = withContext(Dispatchers.IO) {
        val path = if (outlet == 0) {
            "/api/strips/$stripId/relays/${if (on) "all-on" else "all-off"}"
        } else {
            "/api/strips/$stripId/relays/${outlet - 1}"
        }
        val body = if (outlet == 0) "{}" else JSONObject().put("state", if (on) "on" else "off").toString()
        val method = if (outlet == 0) null else "PATCH"
        request("$base$path", body, token, method)
    }

    suspend fun updateStripIcon(base: String, token: String, stripId: String, icon: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("icon", icon).toString()
        request("$base/api/strips/$stripId", body, token, "PATCH")
    }

    suspend fun updateOutletIcon(base: String, token: String, stripId: String, outlet: Int, icon: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("icon", icon).toString()
        request("$base/api/strips/$stripId/relays/${outlet - 1}", body, token, "PATCH")
    }



    suspend fun updateOutletName(base: String, token: String, stripId: String, outlet: Int, name: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("name", name).toString()
        request("$base/api/strips/$stripId/relays/${outlet - 1}", body, token, "PATCH")
    }

    suspend fun startOutletTimer(base: String, token: String, stripId: String, outlet: Int, on: Boolean, minutes: Int, mode: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("state", if (on) "on" else "off").put("durationMinutes", minutes).put("mode", mode).toString()
        parseTimer(JSONObject(request("$base/api/strips/$stripId/relays/${outlet - 1}/timer", body, token)).getJSONObject("timer"))
    }

    suspend fun cancelOutletTimer(base: String, token: String, stripId: String, outlet: Int) = withContext(Dispatchers.IO) {
        request("$base/api/strips/$stripId/relays/${outlet - 1}/timer", null, token, "DELETE")
    }

    suspend fun activeTimers(base: String, token: String): List<RelayTimer> = withContext(Dispatchers.IO) {
        JSONObject(request("$base/api/strips/timers/active", null, token)).optJSONArray("timers").toList(::parseTimer)
    }

    suspend fun outletEvents(base: String, token: String, stripId: String, outlet: Int): List<EventItem> = withContext(Dispatchers.IO) {
        JSONObject(request("$base/api/events?stripId=$stripId&relayIndex=${outlet - 1}&limit=40", null, token)).optJSONArray("events").toList(::parseEvent)
    }

    fun parseTimer(x: JSONObject) = RelayTimer(
        stripId = x.optString("stripId"),
        relayIndex = x.optInt("relayIndex"),
        mode = x.optString("mode", "for"),
        state = x.optString("state", "off"),
        restoreAtMs = parseIsoMs(x.optString("restoreAt")),
    )

    suspend fun powerSummary(base: String, token: String, stripId: String): PowerSummary = withContext(Dispatchers.IO) {
        val o = JSONObject(request("$base/api/power/summary?stripId=$stripId", null, token))
        PowerSummary(o.optDouble("todayKwh", 0.0), o.optDouble("todayCost", 0.0), o.optString("currency", "EGP"), o.optDouble("liveWatts", 0.0))
    }

    suspend fun energy(base: String, token: String, stripId: String, days: Int): EnergyReport = withContext(Dispatchers.IO) {
        val o = JSONObject(request("$base/api/power/energy?stripId=$stripId&days=$days", null, token))
        EnergyReport(
            days = o.optInt("days", days),
            totalKwh = o.optDouble("totalKwh", 0.0),
            previousKwh = o.optDouble("previousKwh", 0.0),
            cost = o.optDouble("cost", 0.0),
            currency = o.optString("currency", "EGP"),
            ratePerKwh = o.optDouble("ratePerKwh", 0.0),
            tariff = o.optJSONObject("tariff").let { t ->
                TariffInfo(
                    mode = t?.optString("mode", "flat") ?: "flat",
                    name = t?.optString("name") ?: "",
                    monthToDateKwh = t?.optDouble("monthToDateKwh", 0.0) ?: 0.0,
                    currentLevel = t?.optInt("currentLevel", -1) ?: -1,
                    levels = t?.optJSONArray("levels").toList { TariffLevel(it.optString("label"), it.optDouble("rate", 0.0), it.optDouble("service", 0.0)) },
                    note = t?.optString("note") ?: "",
                )
            },
            daily = o.optJSONArray("daily").toList { DayEnergy(it.optString("date"), it.optDouble("kwh", 0.0), it.optDouble("cost", 0.0)) },
            outlets = o.optJSONArray("outlets").toList { OutletEnergy(it.optInt("relayIndex"), it.optString("name"), it.optDouble("kwh", 0.0), it.optDouble("cost", 0.0)) },
        )
    }

    suspend fun resetEnergy(base: String, token: String, stripId: String) = withContext(Dispatchers.IO) {
        request("$base/api/power/energy?stripId=$stripId", null, token, "DELETE")
    }

    suspend fun events(base: String, token: String): List<EventItem> = withContext(Dispatchers.IO) {
        JSONObject(request("$base/api/events", null, token)).optJSONArray("events").toList(::parseEvent)
    }

    suspend fun notifications(base: String, token: String): List<NotificationItem> = withContext(Dispatchers.IO) {
        JSONObject(request("$base/api/notifications", null, token)).optJSONArray("notifications").toList(::parseNotification)
    }

    suspend fun markNotificationRead(base: String, token: String, id: String) = withContext(Dispatchers.IO) {
        request("$base/api/notifications/$id/read", "{}", token, "PATCH")
    }

    suspend fun markAllNotificationsRead(base: String, token: String) = withContext(Dispatchers.IO) {
        request("$base/api/notifications/read-all", "{}", token, "POST")
    }

    suspend fun deleteNotification(base: String, token: String, id: String) = withContext(Dispatchers.IO) {
        request("$base/api/notifications/$id", null, token, "DELETE")
    }

    suspend fun clearNotifications(base: String, token: String) = withContext(Dispatchers.IO) {
        request("$base/api/notifications", null, token, "DELETE")
    }

    suspend fun clearEvents(base: String, token: String) = withContext(Dispatchers.IO) {
        request("$base/api/events", null, token, "DELETE")
    }

    suspend fun schedules(base: String, token: String): List<ScheduleItem> = withContext(Dispatchers.IO) {
        JSONObject(request("$base/api/schedules", null, token)).optJSONArray("schedules").toList(::parseSchedule)
    }

    suspend fun saveSchedule(base: String, token: String, schedule: ScheduleItem): ScheduleItem = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", schedule.name)
            .put("stripId", schedule.stripId)
            .put("relayIndex", schedule.relayIndex ?: JSONObject.NULL)
            .put("action", schedule.action)
            .put("time", schedule.time)
            .put("daysOfWeek", org.json.JSONArray(schedule.daysOfWeek))
            .put("enabled", schedule.enabled)
            .put("timezone", schedule.timezone)
            .put("repeat", schedule.repeat)
            .toString()
        val isNew = schedule.id.isBlank()
        val url = if (isNew) "$base/api/schedules" else "$base/api/schedules/${schedule.id}"
        parseSchedule(JSONObject(request(url, body, token, if (isNew) "POST" else "PATCH")).getJSONObject("schedule"))
    }

    suspend fun deleteSchedule(base: String, token: String, id: String) = withContext(Dispatchers.IO) {
        request("$base/api/schedules/$id", null, token, "DELETE")
    }

    suspend fun runScheduleNow(base: String, token: String, id: String) = withContext(Dispatchers.IO) {
        request("$base/api/schedules/$id/run-now", "{}", token, "POST")
    }

    suspend fun rules(base: String, token: String): List<RuleItem> = withContext(Dispatchers.IO) {
        JSONObject(request("$base/api/rules", null, token)).optJSONArray("rules").toList(::parseRule)
    }

    suspend fun saveRule(base: String, token: String, rule: RuleItem): RuleItem = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("type", rule.type)
            .put("stripId", rule.stripId)
            .put("relayIndex", rule.relayIndex ?: JSONObject.NULL)
            .put("name", rule.name)
            .put("enabled", rule.enabled)
            .put("params", rule.params)
            .toString()
        val isNew = rule.id.isBlank()
        val url = if (isNew) "$base/api/rules" else "$base/api/rules/${rule.id}"
        parseRule(JSONObject(request(url, body, token, if (isNew) "POST" else "PUT")).getJSONObject("rule"))
    }

    suspend fun deleteRule(base: String, token: String, id: String) = withContext(Dispatchers.IO) {
        request("$base/api/rules/$id", null, token, "DELETE")
    }

    suspend fun settings(base: String, token: String): AppSettings = withContext(Dispatchers.IO) {
        parseSettings(JSONObject(request("$base/api/settings", null, token)).getJSONObject("settings"))
    }

    suspend fun saveSettings(base: String, token: String, settings: AppSettings): AppSettings = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("theme", settings.theme)
            .put("energyUnit", settings.energyUnit)
            .put("currency", settings.currency)
            .put("ratePerKwh", settings.ratePerKwh)
            .put("tariffMode", settings.tariffMode)
            .put("overloadThresholdWatts", settings.overloadThresholdWatts)
            .put("timeFormat", settings.timeFormat)
            .put("accentColor", settings.accentColor)
            .toString()
        parseSettings(JSONObject(request("$base/api/settings", body, token, "PATCH")).getJSONObject("settings"))
    }

    fun parseEvent(x: JSONObject) = EventItem(
        id = x.optString("id"),
        type = x.optString("type"),
        message = x.optString("message"),
        stripName = x.optString("stripName").ifBlank { null },
        createdAt = x.optString("createdAt"),
    )

    fun parseNotification(x: JSONObject) = NotificationItem(
        id = x.optString("id"),
        title = x.optString("title"),
        body = x.optString("body"),
        severity = x.optString("severity"),
        readAt = if (x.isNull("readAt")) null else x.optString("readAt"),
        createdAt = x.optString("createdAt"),
    )

    fun parseRule(x: JSONObject) = RuleItem(
        id = x.optString("id"),
        type = x.optString("type"),
        stripId = x.optString("stripId"),
        relayIndex = if (x.isNull("relayIndex")) null else x.optInt("relayIndex"),
        name = x.optString("name"),
        enabled = x.optBoolean("enabled", true),
        params = x.optJSONObject("params") ?: JSONObject(),
        status = if (x.isNull("status")) null else x.optString("status"),
    )

    private fun <T> org.json.JSONArray?.toList(map: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).map { map(getJSONObject(it)) }

    /**
     * Sends Wi-Fi credentials to the strip.
     *
     * Standalone firmware (MTTL-W01-SETUP AP): HTTP POST to http://192.168.4.1/wifi
     * Stock firmware (TONLY_TAP_* AP): TCP setup commands to 192.168.1.1:30300
     */
    suspend fun configureStripWifi(
        ssid: String,
        password: String,
        standalone: Boolean,
        controllerIp: String,
        network: android.net.Network? = null,
    ): String = withContext(Dispatchers.IO) {
        if (standalone) {
            configureStandaloneWifi(ssid, password, network)
        } else {
            configureStockWifi(ssid, password, network, controllerIp)
        }
    }

    private fun configureStandaloneWifi(ssid: String, password: String, network: android.net.Network?): String {
        val url = URL("http://192.168.4.1/wifi")
        val body = "ssid=${java.net.URLEncoder.encode(ssid, "UTF-8")}&password=${java.net.URLEncoder.encode(password, "UTF-8")}"
        // Use the strip's Network object directly so this connection can't be silently rerouted
        // by OEM Wi-Fi managers that override process-wide bindProcessToNetwork.
        val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        try {
            conn.connectTimeout = 3_000
            conn.readTimeout = 5_000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            Log.i("SmartStripProvision", "standalone wifi POST code=$code response=$response")
            if (code !in 200..299) throw ServerException("Strip setup page returned HTTP $code")
            return "Wi-Fi credentials sent to strip. Waiting for it to join your network…"
        } finally {
            conn.disconnect()
        }
    }

    /** Sends one `\r\n`-terminated setup-mode command line, e.g. "up:ip:192.168.1.50". */
    private fun mttlSendCommand(socket: Socket, command: String) {
        socket.getOutputStream().apply {
            write("$command\r\n".toByteArray(StandardCharsets.UTF_8))
            flush()
        }
    }

    /** Reads the strip's reply and reports whether it contains `token` (e.g. "ip_ok"). */
    private fun mttlReplyContains(socket: Socket, token: String): Boolean {
        val reply = String(mttlReadAvailable(socket.getInputStream()), StandardCharsets.UTF_8)
        Log.i("SmartStripProvision", "setup reply: $reply")
        return reply.contains(token)
    }

    /** Drains whatever the strip sends back within the socket's read timeout. */
    private fun mttlReadAvailable(input: InputStream): ByteArray {
        val data = ByteArrayOutputStream()
        val chunk = ByteArray(512)
        try {
            while (true) {
                val count = input.read(chunk)
                if (count <= 0) break
                data.write(chunk, 0, count)
                if (count < chunk.size) break
            }
        } catch (e: SocketTimeoutException) {
            // No more bytes coming, not a failure.
        }
        return data.toByteArray()
    }

    private fun mttlOpenSetupSocket(network: android.net.Network?): Socket {
        // Socket from Network#socketFactory stays bound to that network regardless of
        // process-wide default-network changes (observed unreliable on Huawei WifiPro).
        val socket = network?.socketFactory?.createSocket() ?: Socket()
        try {
            // Short connect timeout: a live strip (one Wi-Fi hop away) accepts well under a
            // second. Failing fast matters because the usual failure mode is the OS tearing down
            // the strip AP mid-request (e.g. Huawei WifiPro roam-back) — a long timeout just
            // wastes the user's time finding out, instead of letting them tap "Retry" quickly.
            socket.connect(InetSocketAddress(MTTL_SETUP_HOST, MTTL_SETUP_PORT), 2_500)
        } catch (e: Exception) {
            runCatching { socket.close() }
            val target = "$MTTL_SETUP_HOST:$MTTL_SETUP_PORT"
            val hint = "Join this phone to the strip setup Wi-Fi (TONLY_TAP_*, password LGU_*) and try again."
            throw ServerException("Could not reach $target. ${e.message ?: ""} $hint")
        }
        return socket
    }

    private fun configureStockWifi(ssid: String, password: String, network: android.net.Network?, controllerIp: String): String {
        Log.i("SmartStripProvision", "configureStockWifi ssid=$ssid controllerIp=$controllerIp")
        validateProvisionInput(ssid, password)

        val socket = mttlOpenSetupSocket(network)
        try {
            socket.soTimeout = 4_000

            // up:ip: tells the strip which address to phone home to (our server, port 10086)
            // after it joins the target Wi-Fi — without this it keeps whatever server it last had.
            mttlSendCommand(socket, "up:ip:$controllerIp")
            if (!mttlReplyContains(socket, "ip_ok")) {
                throw ServerException("Strip did not accept the server address. Make sure it's in setup mode and try again.")
            }

            mttlSendCommand(socket, "up:connect:$ssid:$password")
            val connected = mttlReplyContains(socket, "connect_ok")

            // Firmware applies the Wi-Fi and reboots on its own after connect_ok; this just
            // signals it to leave setup mode sooner. No reply is expected for it.
            runCatching { mttlSendCommand(socket, "up:reboot:0") }

            return if (connected) {
                "Strip accepted Wi-Fi settings. Waiting for it to reconnect…"
            } else {
                "Wi-Fi settings were sent to the strip. Waiting for it to reconnect…"
            }
        } catch (e: ServerException) {
            throw e
        } catch (e: Exception) {
            Log.e("SmartStripProvision", "configureStockWifi failed", e)
            throw ServerException("Could not send Wi-Fi settings to the strip: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { socket.close() }
        }
    }

    suspend fun waitForRegistration(base: String, token: String): ProvisionResult = withContext(Dispatchers.IO) {
        val discovered = waitForDiscoveredStrip(base, token)
        val strip = discovered.firstOrNull()
            ?: return@withContext ProvisionResult(
                registered = false,
                stripName = null,
                message = "Wi-Fi was sent, but the strip did not appear on the server yet. Keep the server reachable and scan again in a minute.",
            )

        val body = JSONObject()
            .put("driverStripId", strip.driverStripId)
            .put("model", strip.model)
            .put("relayCount", strip.relayCount)
            .put("ipAddress", strip.ipAddress)
            .put("suggestedName", strip.suggestedName)
            .put("name", strip.suggestedName)
            .toString()
        request("$base/api/strips", body, token)
        ProvisionResult(true, strip.suggestedName, "${strip.suggestedName} is provisioned and registered on your SmartStrip server.")
    }

    /**
     * Waits for a standalone-firmware strip to appear on the LAN.
     * Scans the strip's expected LAN address (192.168.x.x) by probing /api/status.
     * Returns the strip IP on success, or null after timeout.
     */
    suspend fun waitForStandaloneStrip(timeoutMs: Long = 60_000L): String? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val ip = probeStandaloneStrip()
            if (ip != null) return@withContext ip
            delay(3_000L)
        }
        null
    }

    private fun probeStandaloneStrip(): String? {
        // Try common 192.168.x.x ranges - the strip typically picks a DHCP address
        // We probe the gateway's neighbor range; for now we do a quick HEAD on /api/status
        // across a /24 subnet derived from local interface
        val ranges = listOf("192.168.1", "192.168.0")
        for (prefix in ranges) {
            for (i in 1..254) {
                val ip = "$prefix.$i"
                try {
                    val conn = URL("http://$ip/api/status").openConnection() as HttpURLConnection
                    conn.connectTimeout = 300
                    conn.readTimeout = 300
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code == 200) {
                        Log.i("SmartStripProvision", "Found standalone strip at $ip")
                        return ip
                    }
                } catch (_: Exception) { /* not reachable */ }
            }
        }
        return null
    }

    suspend fun provisionAndRegister(base: String, token: String, serverIp: String, ssid: String, password: String): ProvisionResult = withContext(Dispatchers.IO) {
        configureStripWifi(ssid, password, standalone = false, controllerIp = serverIp)
        waitForRegistration(base, token)
    }

    /** POST /api/strips/scan — starts a network scan, returns the scanId. */
    suspend fun startScan(base: String, token: String): String = withContext(Dispatchers.IO) {
        JSONObject(request("$base/api/strips/scan", "{}", token)).optString("scanId")
    }

    data class ScanResult(val done: Boolean, val strips: List<DiscoveredStrip>)

    /** GET /api/strips/scan/{scanId} — polls scan progress. */
    suspend fun getScanResult(base: String, token: String, scanId: String): ScanResult = withContext(Dispatchers.IO) {
        val record = JSONObject(request("$base/api/strips/scan/$scanId", null, token))
        val status = record.optString("status")
        ScanResult(done = status == "done" || status == "error", strips = parseDiscovered(record))
    }

    /** DELETE /api/strips/{id} — removes a strip. */
    suspend fun deleteStrip(base: String, token: String, stripId: String) = withContext(Dispatchers.IO) {
        request("$base/api/strips/$stripId", null, token, "DELETE")
    }

    /** PATCH /api/strips/{id} — update strip name. */
    suspend fun updateStrip(base: String, token: String, stripId: String, name: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("name", name).toString()
        request("$base/api/strips/$stripId", body, token, "PATCH")
    }

    suspend fun updateStripLocation(base: String, token: String, stripId: String, location: String?) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("location", location ?: JSONObject.NULL).toString()
        request("$base/api/strips/$stripId", body, token, "PATCH")
    }

    /** POST /api/strips — adopt a discovered strip. */
    suspend fun adoptStrip(base: String, token: String, strip: DiscoveredStrip) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("driverStripId", strip.driverStripId)
            .put("model", strip.model)
            .put("relayCount", strip.relayCount)
            .put("ipAddress", strip.ipAddress)
            .put("suggestedName", strip.suggestedName)
            .put("name", strip.suggestedName)
            .toString()
        request("$base/api/strips", body, token)
    }

    // Retry the same HTTP request on transient connection failures.
    // Only idempotent requests (no body) are retried so repeated POSTs cannot double-apply commands.
    internal fun request(url: String, body: String?, token: String, method: String? = null): String {
        val maxAttempts = if (body == null && method == null) 3 else 1
        var attempt = 0
        while (true) {
            try {
                return doRequest(url, body, token, method)
            } catch (e: ServerException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) throw ServerException(e.message ?: "connection failed")
                Thread.sleep(500L * (1L shl attempt.coerceAtMost(2)))
            }
        }
    }

    private fun doRequest(url: String, body: String?, token: String, method: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 4000
            connection.readTimeout = 8000
            connection.requestMethod = method ?: if (body == null) "GET" else "POST"
            if (token.isNotBlank()) connection.setRequestProperty("X-Token", token)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ServerException(errorOf(text, code))
            return text
        } finally {
            connection.disconnect()
        }
    }

    fun parseStrip(o: JSONObject): Strip {
        val relays = o.optJSONArray("relays")
        val telemetry = o.optJSONObject("telemetry")
        val telemetryOutlets = telemetry?.optJSONArray("outlets")
        val extraByIndex = (0 until (telemetryOutlets?.length() ?: 0)).associate { i ->
            val outlet = telemetryOutlets!!.getJSONObject(i)
            outlet.optInt("relayIndex") to outlet
        }
        val outlets = (0 until (relays?.length() ?: 0)).map { i ->
            val x = relays!!.getJSONObject(i)
            val relayIndex = x.optInt("index")
            val extra = extraByIndex[relayIndex]
            Outlet(
                n = relayIndex + 1,
                on = x.optString("state") == "on",
                powerW = x.optDouble("watts", 0.0),
                energyKwh = extra?.optDouble("energyKwh", 0.0) ?: 0.0,
                tempC = extra?.optInt("temperatureC") ?: 0,
                icon = x.optString("icon", "plug"),
                name = x.optString("name").ifBlank { "Outlet ${relayIndex + 1}" },
                changedAtMs = parseIsoMs(x.optString("lastChangedAt")),
            )
        }
        return Strip(
            mac = o.optString("id"),
            name = o.optString("name"),
            model = o.optString("model"),
            fw = telemetry?.optString("fw") ?: "",
            online = o.optString("connectivity") == "online",
            on = outlets.any { it.on },
            powerW = o.optDouble("totalWatts", 0.0),
            energyKwh = telemetry?.optDouble("totalEnergyKwh", 0.0) ?: 0.0,
            voltage = telemetry?.optDoubleOrNull("voltageV"),
            currentA = telemetry?.optDoubleOrNull("currentA"),
            rssi = telemetry?.optIntOrNull("rssi"),
            icon = o.optString("icon", "power-strip"),
            location = if (o.isNull("location")) null else o.optString("location").ifBlank { null },
            outlets = outlets,
        )
    }
    private suspend fun waitForDiscoveredStrip(base: String, token: String): List<DiscoveredStrip> {
        // Treat each attempt as retryable, not fatal: the phone briefly has no DNS/routing right
        // after leaving the strip's AP, which used to throw a raw UnknownHostException here.
        repeat(12) {
            val scanId = runCatching {
                JSONObject(request("$base/api/strips/scan", "{}", token)).optString("scanId")
            }.getOrNull()
            if (scanId.isNullOrBlank()) {
                delay(1000)
                return@repeat
            }
            for (poll in 1..8) {
                delay(1000)
                val record = runCatching { JSONObject(request("$base/api/strips/scan/$scanId", null, token)) }
                    .getOrNull() ?: continue
                val status = record.optString("status")
                // An empty "done" scan can finish just before the strip connects, so scan again.
                if (status == "done") {
                    val found = parseDiscovered(record)
                    if (found.isNotEmpty()) return found
                    break
                }
                if (status == "error") break
            }
            delay(2000)
        }
        return emptyList()
    }



    private fun parseSchedule(x: JSONObject): ScheduleItem {
        val days = x.optJSONArray("daysOfWeek")
        return ScheduleItem(
            id = x.optString("id"),
            name = x.optString("name"),
            stripId = x.optString("stripId"),
            relayIndex = if (x.isNull("relayIndex")) null else x.optInt("relayIndex"),
            action = x.optString("action", "on"),
            time = x.optString("time", "18:00"),
            daysOfWeek = (0 until (days?.length() ?: 0)).map { days!!.optInt(it) },
            enabled = x.optBoolean("enabled", true),
            timezone = x.optString("timezone", "Africa/Cairo").ifBlank { "Africa/Cairo" },
            repeat = x.optString("repeat", "weekly"),
        )
    }

    fun parseIsoMs(s: String?): Long = if (s.isNullOrBlank()) 0L else runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    fun parseSettings(x: JSONObject) = AppSettings(
        theme = x.optString("theme", "system"),
        energyUnit = x.optString("energyUnit", "kWh"),
        currency = x.optString("currency", "EGP"),
        ratePerKwh = x.optDouble("ratePerKwh", 0.16),
        tariffMode = x.optString("tariffMode", "egypt"),
        overloadThresholdWatts = x.optInt("overloadThresholdWatts", 1800),
        timeFormat = x.optString("timeFormat", "24h"),
        accentColor = x.optString("accentColor", AppTheme.DEFAULT_ACCENT),
    )
    private fun parseDiscovered(record: JSONObject): List<DiscoveredStrip> {
        val array = record.optJSONArray("discovered")
        return (0 until (array?.length() ?: 0)).map { i ->
            val x = array!!.getJSONObject(i)
            DiscoveredStrip(
                driverStripId = x.optString("driverStripId"),
                model = x.optString("model"),
                suggestedName = x.optString("suggestedName"),
                ipAddress = if (x.isNull("ipAddress")) null else x.optString("ipAddress"),
                relayCount = x.optInt("relayCount", 4),
            )
        }
    }

    fun resolveServerAddressForStrip(value: String): ServerAddress {
        val host = serverAddressHost(value)
        val ip = runCatching { java.net.InetAddress.getByName(host).hostAddress }
            .getOrNull()
            ?.takeIf { isIpv4(it) }
            ?: host
        return ServerAddress(host, ip)
    }

    fun provisionAddressFromServer(base: String, token: String): ServerAddress {
        val host = serverAddressHost(base)
        val fallback = resolveServerAddressForStrip(base)
        return runCatching {
            val root = JSONObject(request("$base/api/mttl/status", null, token))
            val advertised = root.optString("advertisedIp").takeIf { isIpv4(it) }
            val localIps = root.optJSONArray("localIps")
            val locals = (0 until (localIps?.length() ?: 0))
                .map { localIps!!.optString(it) }
                .filter { isPrivateIpv4(it) }
            val local = locals.firstOrNull { it.startsWith("192.168.") }
                ?: locals.firstOrNull { it.startsWith("10.") }
                ?: locals.firstOrNull { it.startsWith("172.") }
            ServerAddress(host, advertised ?: local ?: fallback.ip)
        }.getOrDefault(fallback)
    }

    private fun serverAddressHost(value: String): String {
        val trimmed = value.trim()
        return runCatching {
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) URL(trimmed).host else trimmed
        }.getOrDefault(trimmed).substringBefore('/').substringBefore(':')
    }

    private fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    }

    private fun isPrivateIpv4(value: String): Boolean {
        if (!isIpv4(value)) return false
        val p = value.split('.').map { it.toInt() }
        return p[0] == 10 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168)
    }

    private fun validateProvisionInput(ssid: String, password: String) {
        if (ssid.isBlank() || password.isBlank()) throw ServerException("Wi-Fi name and password are required")
        if (ssid.any { it == ':' || it == '\n' || it == '\r' } || password.any { it == ':' || it == '\n' || it == '\r' }) {
            throw ServerException("Wi-Fi name/password cannot contain ':' or newlines")
        }
    }

    private fun errorOf(body: String, code: Int): String =
        try {
            JSONObject(body).optString("error").ifBlank { "HTTP $code" }
        } catch (e: Exception) {
            "HTTP $code"
        }

    private fun serverIp(base: String): String = runCatching { URL(base).host }.getOrDefault("")

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)
}
