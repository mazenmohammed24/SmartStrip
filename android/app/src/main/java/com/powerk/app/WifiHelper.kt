package com.powerk.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

private const val STRIP_SSID_PREFIX = "TONLY_TAP_"
private const val STRIP_AP_PASSWORD_PREFIX = "LGU_"

data class WifiScanResult(val ssid: String, val rssi: Int)

/** Password for TONLY_TAP_XXXXXXX is LGU_XXXXXXX (same trailing chars). */
fun stripApPassword(ssid: String): String =
    STRIP_AP_PASSWORD_PREFIX + ssid.removePrefix(STRIP_SSID_PREFIX)

object WifiHelper {

    /** Returns the SSID of the currently connected Wi-Fi (empty string if not connected or no permission). */
    fun getCurrentSsid(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val net = cm.activeNetwork ?: return ""
                val caps = cm.getNetworkCapabilities(net) ?: return ""
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ""
                val wifiInfo = caps.transportInfo as? android.net.wifi.WifiInfo ?: return ""
                wifiInfo.ssid.removeSurrounding("\"")
            } else {
                @Suppress("DEPRECATION")
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val info = wm.connectionInfo ?: return ""
                info.ssid.removeSurrounding("\"")
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** Scans for nearby TONLY_TAP_* strip APs and returns them sorted by signal strength. */
    fun scanForStripNetworks(context: Context): List<WifiScanResult> {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val results = wm.scanResults ?: return emptyList()
            results
                .filter { it.SSID.startsWith(STRIP_SSID_PREFIX) }
                .map { WifiScanResult(it.SSID, it.level) }
                .distinctBy { it.ssid }
                .sortedByDescending { it.rssi }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Connects the phone to the strip AP (Android 10+) and returns the bound Network,
     * or null on failure. The caller must use [bindProcessToNetwork] while communicating
     * with the strip, then call [releaseStripNetwork] when done.
     *
     * [onConnecting] is called when the request is accepted but not yet established.
     */
    suspend fun connectToStripAp(
        context: Context,
        ssid: String,
        onConnecting: () -> Unit = {},
    ): Network? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val password = stripApPassword(ssid)
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        return try {
            withTimeout(30_000L) {
                callbackFlow {
                    val cb = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(network)
                        }
                        override fun onUnavailable() {
                            trySend(null as Network?)
                        }
                    }
                    cm.requestNetwork(request, cb)
                    onConnecting()
                    awaitClose { cm.unregisterNetworkCallback(cb) }
                }.first()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun bindProcessToNetwork(context: Context, network: Network?) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.bindProcessToNetwork(network)
    }

    fun releaseStripNetwork(context: Context) {
        bindProcessToNetwork(context, null)
    }
}
