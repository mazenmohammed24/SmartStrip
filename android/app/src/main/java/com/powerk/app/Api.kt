package com.powerk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
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
    val outlets: List<Outlet>,
)

data class Snapshot(val serverIp: String, val strips: List<Strip>)
data class DiscoveredStrip(val driverStripId: String, val model: String, val suggestedName: String, val ipAddress: String?, val relayCount: Int)
data class ProvisionResult(val registered: Boolean, val stripName: String?, val message: String)

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

    suspend fun provisionAndRegister(base: String, token: String, serverIp: String, ssid: String, password: String): ProvisionResult = withContext(Dispatchers.IO) {
        validateProvisionInput(serverIp, ssid, password)
        val ipReply = setupCommand("up:ip:$serverIp", "up:ip", false)
        if (!ipReply.contains("up:ip:ip_ok")) throw ServerException("The strip rejected the server IP: ${ipReply.ifBlank { "no reply" }}")
        val connectReply = setupCommand("up:connect:$ssid:$password", "up:connect", true)
        if (!connectReply.contains("up:connect:connect_ok")) throw ServerException("The strip rejected the Wi-Fi settings")

        val discovered = waitForDiscoveredStrip(base, token)
        val strip = discovered.firstOrNull()
            ?: return@withContext ProvisionResult(
                registered = false,
                stripName = null,
                message = "Wi-Fi was sent. Reconnect this phone to your normal network, wait for the strip to dial the server, then scan/register from the web UI.",
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

    internal fun request(url: String, body: String?, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 4000
            connection.readTimeout = 8000
            connection.requestMethod = if (body == null) "GET" else "POST"
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
            Socket(MTTL_SETUP_HOST, MTTL_SETUP_PORT).use { socket ->
                socket.soTimeout = 8000
                socket.getOutputStream().write("$command\r\n".toByteArray(Charsets.UTF_8))
                val bytes = ByteArray(1024)
                val count = socket.getInputStream().read(bytes)
                return if (count > 0) String(bytes, 0, count, Charsets.UTF_8).trim() else ""
            }
        } catch (e: Exception) {
            val target = "$MTTL_SETUP_HOST:$MTTL_SETUP_PORT"
            val hint = "Join this phone to the strip setup Wi-Fi (TONLY_TAP_*, password LGU_*) and try again."
            throw ServerException("Could not send $label to $target. $hint")
        }
    }

    private fun validateProvisionInput(serverIp: String, ssid: String, password: String) {
        val parts = serverIp.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull()?.let { n -> n !in 0..255 } ?: true }) {
            throw ServerException("Server IP must be an IPv4 address reachable by the strip")
        }
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
