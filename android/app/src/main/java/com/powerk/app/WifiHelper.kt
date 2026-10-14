package com.powerk.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference

private const val STRIP_SSID_PREFIX = "TONLY_TAP_"
private const val STANDALONE_SSID = "MTTL-W01-SETUP"
private const val STRIP_AP_PASSWORD_PREFIX = "LGU_"

enum class StripApType { STOCK, STANDALONE }

data class WifiScanResult(val ssid: String, val rssi: Int, val type: StripApType = StripApType.STOCK)

/** True if this scan result is the standalone firmware setup AP. */
val WifiScanResult.isStandalone: Boolean get() = type == StripApType.STANDALONE

/** Password for TONLY_TAP_XXXXXXX is LGU_XXXXXXX; standalone AP is open (no password). */
fun stripApPassword(ssid: String): String =
    if (ssid == STANDALONE_SSID) "" else STRIP_AP_PASSWORD_PREFIX + ssid.removePrefix(STRIP_SSID_PREFIX)

/** Holds the active strip AP connection so it is not released prematurely. */
data class StripConnection(
    val network: Network,
    val callback: ConnectivityManager.NetworkCallback,
    val cm: ConnectivityManager,
)

object WifiHelper {

    /** Persists the active strip connection across wizard steps. Set by [connectToStripAp], cleared by [releaseStripNetwork]. */
    private val activeConnection = AtomicReference<StripConnection?>(null)

    /** If true, auto-reconnect loop should retry on [onLost]. */
    @Volatile var autoReconnectEnabled = false

    /** Returns the SSID of the currently connected Wi-Fi (empty string if not connected or no permission). */
    fun getCurrentSsid(context: Context): String {
        fun clean(value: String?): String {
            val ssid = value.orEmpty().removeSurrounding("\"").trim()
            return when {
                ssid.isBlank() -> ""
                ssid.equals("<unknown ssid>", ignoreCase = true) -> ""
                ssid == "0x" -> ""
                else -> ssid
            }
        }

        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val net = cm.activeNetwork
                val caps = net?.let { cm.getNetworkCapabilities(it) }
                val fromCaps = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    clean((caps.transportInfo as? android.net.wifi.WifiInfo)?.ssid)
                } else ""
                if (fromCaps.isNotBlank()) return fromCaps
            }

            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            clean(wm.connectionInfo?.ssid)
        } catch (_: Exception) {
            ""
        }
    }

    /** Scans for nearby strip APs: TONLY_TAP_* (stock) and MTTL-W01-SETUP (standalone). */
    suspend fun scanForStripNetworks(context: Context, attempts: Int = 4): List<WifiScanResult> {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            val found = readStripScanResults(wm)
            if (found.isNotEmpty()) return found
            @Suppress("DEPRECATION")
            runCatching { wm.startScan() }
            delay(if (attempt == 0) 900L else 1_800L)
        }
        return readStripScanResults(wm)
    }

    private fun readStripScanResults(wm: WifiManager): List<WifiScanResult> = try {
        @Suppress("DEPRECATION")
        wm.scanResults.orEmpty()
            .asSequence()
            .filter { it.SSID.startsWith(STRIP_SSID_PREFIX) || it.SSID == STANDALONE_SSID }
            .map {
                val type = if (it.SSID == STANDALONE_SSID) StripApType.STANDALONE else StripApType.STOCK
                WifiScanResult(it.SSID, it.level, type)
            }
            .distinctBy { it.ssid }
            .sortedByDescending { it.rssi }
            .toList()
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Connects the phone to the strip AP (Android 10+) and returns the bound Network,
     * or null on failure.  The [NetworkCallback] is kept registered for the entire wizard
     * lifetime so Android does not drop the connection.  Call [releaseStripNetwork] when done.
     *
     * [onLost] is invoked on the callback thread whenever Android tears down the link so the
     * caller can trigger a reconnect attempt.
     */
    suspend fun connectToStripAp(
        context: Context,
        ssid: String,
        onConnecting: () -> Unit = {},
        onLost: (() -> Unit)? = null,
    ): Network? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        // Release any previous connection first.
        releaseStripNetwork(context)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val password = stripApPassword(ssid)
        val specifier = if (password.isEmpty()) {
            WifiNetworkSpecifier.Builder().setSsid(ssid).build()
        } else {
            WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(password).build()
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val deferred = CompletableDeferred<Network?>()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i("WifiHelper", "onAvailable: $network")
                activeConnection.set(StripConnection(network, this, cm))
                cm.bindProcessToNetwork(network)
                // Only arm auto-reconnect after a real connection is established.
                autoReconnectEnabled = true
                deferred.complete(network)
            }
            override fun onUnavailable() {
                Log.w("WifiHelper", "onUnavailable")
                // Never completed means the system dialog was cancelled or timed out.
                deferred.complete(null)
            }
            override fun onLost(network: Network) {
                Log.w("WifiHelper", "onLost: $network autoReconnect=$autoReconnectEnabled")
                // Only notify if we had a real connection first.
                if (autoReconnectEnabled) onLost?.invoke()
            }
        }

        return try {
            cm.requestNetwork(request, cb)
            onConnecting()
            withTimeout(30_000L) { deferred.await() }
        } catch (e: Exception) {
            Log.e("WifiHelper", "connectToStripAp failed: ${e.message}")
            runCatching { cm.unregisterNetworkCallback(cb) }
            null
        }
    }

    fun bindProcessToNetwork(context: Context, network: Network?) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.bindProcessToNetwork(network)
    }

    /** Unregisters the active callback and unbinds the process from the strip network. */
    fun releaseStripNetwork(context: Context) {
        autoReconnectEnabled = false
        val conn = activeConnection.getAndSet(null) ?: return
        runCatching { conn.cm.unregisterNetworkCallback(conn.callback) }
        runCatching { conn.cm.bindProcessToNetwork(null) }
    }
}
