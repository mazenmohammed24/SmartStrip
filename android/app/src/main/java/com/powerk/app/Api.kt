package com.powerk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Log
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

private const val MTTL_SETUP_HOST = "192.168.1.1"
private const val MTTL_SETUP_PORT = 30300

data class Outlet(
    val n: Int,
    val on: Boolean,
    val powerW: Double,
    val energyKwh: Double,
    val tempC: Int,
    val icon: String,
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
    val outlets: List<Outlet>,
)

data class Snapshot(val serverIp: String, val strips: List<Strip>)
data class DiscoveredStrip(val driverStripId: String, val model: String, val suggestedName: String, val ipAddress: String?, val relayCount: Int)
data class ProvisionResult(val registered: Boolean, val stripName: String?, val message: String)
data class ServerAddress(val host: String, val ip: String)
data class PowerSummary(val todayKwh: Double, val todayCost: Double, val currency: String, val liveWatts: Double)
data class PowerPoint(val bucketStart: String, val avgW: Double, val minW: Double, val maxW: Double, val kwh: Double)
data class PowerHistory(val range: String, val points: List<PowerPoint>)
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
)
data class AppSettings(
    val theme: String,
    val energyUnit: String,
    val currency: String,
    val ratePerKwh: Double,
    val overloadThresholdWatts: Int,
    val timeFormat: String,
)


/** Outlet by its number (1..4) - never by list position. */
fun Strip.outletOn(n: Int): Boolean = outlets.firstOrNull { it.n == n }?.on ?: false

fun Strip.outlet(n: Int): Outlet? = outlets.firstOrNull { it.n == n }

class ServerException(message: String) : Exception(message)

/** Talks to our self-hosted SmartStrip API. */
object Api {

    suspend fun snapshot(base: String, token: String): Snapshot = withContext(Dispatchers.IO) {
        val stripsRoot = JSONObject(request("$base/api/strips", null, token))
        val telemetryByDriverId = runCatching { telemetryMap(base, token) }.getOrDefault(emptyMap())
        val array = stripsRoot.optJSONArray("strips")
        val strips = (0 until (array?.length() ?: 0)).map { parseStrip(array!!.getJSONObject(it), telemetryByDriverId) }
        Snapshot(serverIp(base), strips)
    }

    suspend fun setOutlet(base: String, token: String, stripId: String, outlet: Int, on: Boolean) = withContext(Dispatchers.IO) {
        val path = if (outlet == 0) {
            "/api/strips/$stripId/relays/${if (on) "all-on" else "all-off"}"
        } else {
            "/api/strips/$stripId/relays/${outlet - 1}"
        }
        val body = if (outlet == 0) "{}" else JSONObject().put("state", if (on) "on" else "off").toString()
        request("$base$path", body, token)
    }


    suspend fun updateStripIcon(base: String, token: String, stripId: String, icon: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("icon", icon).toString()
        request("$base/api/strips/$stripId", body, token, "PATCH")
    }

    suspend fun updateOutletIcon(base: String, token: String, stripId: String, outlet: Int, icon: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("icon", icon).toString()
        request("$base/api/strips/$stripId/relays/${outlet - 1}", body, token, "PATCH")
    }



    suspend fun powerSummary(base: String, token: String, stripId: String): PowerSummary = withContext(Dispatchers.IO) {

        val o = JSONObject(request("$base/api/power/summary?stripId=$stripId", null, token))

        PowerSummary(

            todayKwh = o.optDouble("todayKwh", 0.0),

            todayCost = o.optDouble("todayCost", 0.0),

            currency = o.optString("currency", "USD"),

            liveWatts = o.optDouble("liveWatts", 0.0),

        )

    }



    suspend fun powerHistory(base: String, token: String, stripId: String, range: String = "7d"): PowerHistory = withContext(Dispatchers.IO) {

        val o = JSONObject(request("$base/api/power/history?stripId=$stripId&range=$range", null, token))

        val arr = o.optJSONArray("points")

        val points = (0 until (arr?.length() ?: 0)).map { i ->

            val x = arr!!.getJSONObject(i)

            PowerPoint(

                bucketStart = x.optString("bucketStart"),

                avgW = x.optDouble("avgW", 0.0),

                minW = x.optDouble("minW", 0.0),

                maxW = x.optDouble("maxW", 0.0),

                kwh = x.optDouble("kwh", 0.0),

            )

        }

        PowerHistory(o.optString("range", range), points)

    }



    suspend fun events(base: String, token: String, stripId: String? = null): List<EventItem> = withContext(Dispatchers.IO) {

        val suffix = stripId?.let { "?stripId=$it" } ?: ""

        val root = JSONObject(request("$base/api/events$suffix", null, token))

        val arr = root.optJSONArray("events")

        (0 until (arr?.length() ?: 0)).map { i ->

            val x = arr!!.getJSONObject(i)

            EventItem(

                id = x.optString("id"),

                type = x.optString("type"),

                message = x.optString("message"),

                stripName = x.optString("stripName").ifBlank { null },

                createdAt = x.optString("createdAt"),

            )

        }

    }



    suspend fun notifications(base: String, token: String): List<NotificationItem> = withContext(Dispatchers.IO) {

        val root = JSONObject(request("$base/api/notifications", null, token))

        val arr = root.optJSONArray("notifications")

        (0 until (arr?.length() ?: 0)).map { i ->

            val x = arr!!.getJSONObject(i)

            NotificationItem(

                id = x.optString("id"),

                title = x.optString("title"),

                body = x.optString("body"),

                severity = x.optString("severity"),

                readAt = if (x.isNull("readAt")) null else x.optString("readAt"),

                createdAt = x.optString("createdAt"),

            )

        }

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



    suspend fun schedules(base: String, token: String): List<ScheduleItem> = withContext(Dispatchers.IO) {

        val root = JSONObject(request("$base/api/schedules", null, token))

        val arr = root.optJSONArray("schedules")

        (0 until (arr?.length() ?: 0)).map { i -> parseSchedule(arr!!.getJSONObject(i)) }

    }



    suspend fun saveSchedule(base: String, token: String, schedule: ScheduleItem): ScheduleItem = withContext(Dispatchers.IO) {

        val body = JSONObject()

            .put("name", schedule.name)

            .put("stripId", schedule.stripId)

            .put("relayIndex", schedule.relayIndex)

            .put("action", schedule.action)

            .put("time", schedule.time)

            .put("daysOfWeek", org.json.JSONArray(schedule.daysOfWeek))

            .put("enabled", schedule.enabled)

            .toString()

        val url = if (schedule.id.isBlank()) "$base/api/schedules" else "$base/api/schedules/${schedule.id}"

        val method = if (schedule.id.isBlank()) "POST" else "PATCH"

        parseSchedule(JSONObject(request(url, body, token, method)).getJSONObject("schedule"))

    }



    suspend fun deleteSchedule(base: String, token: String, id: String) = withContext(Dispatchers.IO) {

        request("$base/api/schedules/$id", null, token, "DELETE")

    }



    suspend fun runScheduleNow(base: String, token: String, id: String) = withContext(Dispatchers.IO) {

        request("$base/api/schedules/$id/run-now", "{}", token, "POST")

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

            .put("overloadThresholdWatts", settings.overloadThresholdWatts)

            .put("timeFormat", settings.timeFormat)

            .toString()

        parseSettings(JSONObject(request("$base/api/settings", body, token, "PATCH")).getJSONObject("settings"))

    }



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
    ): String = withContext(Dispatchers.IO) {
        if (standalone) {
            configureStandaloneWifi(ssid, password)
        } else {
            configureStockWifi(ssid, password)
        }
    }

    private fun configureStandaloneWifi(ssid: String, password: String): String {
        val url = URL("http://192.168.4.1/wifi")
        val body = "ssid=${java.net.URLEncoder.encode(ssid, "UTF-8")}&password=${java.net.URLEncoder.encode(password, "UTF-8")}"
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
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

    private fun configureStockWifi(ssid: String, password: String): String {
        Log.i("SmartStripProvision", "configureStockWifi ssid=$ssid")
        validateProvisionInput(ssid, password)
        val ipReply = setupCommand("up:ip:", "up:ip", false)
        Log.i("SmartStripProvision", "ipReply=$ipReply")
        val connectReply = setupCommand("up:connect:$ssid:$password", "up:connect", true)
        Log.i("SmartStripProvision", "connectReply=$connectReply")
        setupCommand("up:reboot:0", "up:reboot", false)
        if (!connectReply.contains("up:connect:connect_ok")) throw ServerException("The strip rejected the Wi-Fi settings")
        return "Strip accepted Wi-Fi settings. Waiting for it to reconnect…"
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
        configureStripWifi(ssid, password, false)
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

    internal fun request(url: String, body: String?, token: String, method: String? = null): String {
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
        } catch (e: ServerException) {
            throw e
        } catch (e: Exception) {
            throw ServerException(e.message ?: "connection failed")
        } finally {
            connection.disconnect()
        }
    }

    private fun telemetryMap(base: String, token: String): Map<String, JSONObject> {
        val root = JSONObject(request("$base/api/mttl/status", null, token))
        val array = root.optJSONArray("strips")
        return (0 until (array?.length() ?: 0)).associate { i ->
            val telemetry = array!!.getJSONObject(i)
            telemetry.optString("driverStripId") to telemetry
        }
    }

    private fun parseStrip(o: JSONObject, telemetryByDriverId: Map<String, JSONObject>): Strip {
        val relays = o.optJSONArray("relays")
        val driverId = o.optString("driverStripId")
        val telemetry = telemetryByDriverId[driverId]
        val telemetryOutlets = telemetry?.optJSONArray("outlets")
        val energyByIndex = (0 until (telemetryOutlets?.length() ?: 0)).associate { i ->
            val outlet = telemetryOutlets!!.getJSONObject(i)
            outlet.optInt("relayIndex") to outlet
        }
        val outlets = (0 until (relays?.length() ?: 0)).map { i ->
            val x = relays!!.getJSONObject(i)
            val relayIndex = x.optInt("index")
            val extra = energyByIndex[relayIndex]
            Outlet(
                n = relayIndex + 1,
                on = x.optString("state") == "on",
                powerW = x.optDouble("watts", 0.0),
                energyKwh = extra?.optDouble("energyKwh", 0.0) ?: 0.0,
                tempC = extra?.optInt("temperatureC") ?: 0,
                icon = x.optString("icon", "plug"),
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
            energyKwh = outlets.sumOf { it.energyKwh },
            voltage = telemetry?.optDoubleOrNull("voltageV"),
            currentA = telemetry?.optDoubleOrNull("currentA"),
            rssi = telemetry?.optIntOrNull("rssi"),
            icon = o.optString("icon", "power-strip"),
            outlets = outlets,
        )
    }

    private suspend fun waitForDiscoveredStrip(base: String, token: String): List<DiscoveredStrip> {
        repeat(12) {
            val scanId = JSONObject(request("$base/api/strips/scan", "{}", token)).optString("scanId")
            repeat(8) {
                delay(1000)
                val record = JSONObject(request("$base/api/strips/scan/$scanId", null, token))
                if (record.optString("status") == "done") return parseDiscovered(record)
                if (record.optString("status") == "error") return emptyList()
            }
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

        )

    }



    private fun parseSettings(x: JSONObject): AppSettings = AppSettings(

        theme = x.optString("theme", "system"),

        energyUnit = x.optString("energyUnit", "kWh"),

        currency = x.optString("currency", "USD"),

        ratePerKwh = x.optDouble("ratePerKwh", 0.16),

        overloadThresholdWatts = x.optInt("overloadThresholdWatts", 1800),

        timeFormat = x.optString("timeFormat", "24h"),

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

    private fun setupCommand(command: String, label: String, sensitive: Boolean): String {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(MTTL_SETUP_HOST, MTTL_SETUP_PORT), 8_000)
                socket.soTimeout = 12_000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                out.write("$command\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
                socket.shutdownOutput()
                val bytes = ByteArray(1024)
                val count = input.read(bytes)
                return if (count > 0) String(bytes, 0, count, Charsets.UTF_8).trim() else ""
            }
        } catch (e: Exception) {
            Log.e("SmartStripProvision", "setupCommand failed label=$label command=${if (sensitive) "***" else command}", e)
            val target = "$MTTL_SETUP_HOST:$MTTL_SETUP_PORT"
            val hint = "Join this phone to the strip setup Wi-Fi (TONLY_TAP_*, password LGU_*) and try again."
            throw ServerException("Could not send $label to $target. ${e.message ?: ""} $hint")
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
