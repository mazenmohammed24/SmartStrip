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
        val appContext = context.applicationContext

        repeat(attempts.coerceAtLeast(1)) { attempt ->
            val found = readStripScanResults(wm)
            if (found.isNotEmpty()) return found

            // Android 9+ throttles foreground scans to 4 per 2 minutes; startScan() returning
            // false means the request was dropped, so don't just blindly delay — wait for the
            // SCAN_RESULTS_AVAILABLE_ACTION broadcast (fired for scans triggered by anyone,
            // including the system itself) instead of polling on a fixed timer.
            val deferred = CompletableDeferred<Unit>()
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                    if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                        deferred.complete(Unit)
                    }
                }
            }
            runCatching {
                androidx.core.content.ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    android.content.IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }

            @Suppress("DEPRECATION")
            val started = runCatching { wm.startScan() }.getOrDefault(false)
            try {
                withTimeout(if (started) 4_000L else 2_000L) { deferred.await() }
            } catch (_: Exception) {
                // Timed out waiting for the broadcast (throttled / no new scan) — fall through
                // and re-check cached scanResults anyway before the next attempt.
            } finally {
                runCatching { appContext.unregisterReceiver(receiver) }
            }
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
     * [onLost] is invoked on the callback thread whenever Android tears down the link, with
     * [fastRoamBack]=true if the loss happened within [FAST_ROAM_BACK_MS] of [onAvailable]. That
     * fast pattern is not the normal "no internet, deprioritize after idle" teardown (which takes
     * many seconds) — it is the OS (observed on Huawei EMUI "Wi-Fi+"/smart dual-network switch,
     * and reported for Samsung/MIUI too) immediately roaming back to a known internet-providing
     * network. Blindly auto-reconnecting in that case just re-triggers another system connect
     * dialog a moment later (the "infinite loop asking me to connect" failure mode), so callers
     * should surface actionable guidance instead of silently retrying when fastRoamBack is true.
     */
    private const val FAST_ROAM_BACK_MS = 2_000L

    suspend fun connectToStripAp(
        context: Context,
        ssid: String,
        onConnecting: () -> Unit = {},
        onLost: ((fastRoamBack: Boolean) -> Unit)? = null,
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
            // The strip AP has no internet; tell Android not to deprioritize/reject it for that.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .setNetworkSpecifier(specifier)
            .build()

        val deferred = CompletableDeferred<Network?>()
        var availableAt = 0L

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i("WifiHelper", "onAvailable: $network")
                availableAt = System.currentTimeMillis()
                activeConnection.set(StripConnection(network, this, cm))
                cm.bindProcessToNetwork(network)
                // Tell the OS this no-internet network is working fine so it isn't torn down.
                runCatching { cm.reportNetworkConnectivity(network, true) }
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
                val elapsed = System.currentTimeMillis() - availableAt
                val fastRoamBack = availableAt != 0L && elapsed < FAST_ROAM_BACK_MS
                Log.w("WifiHelper", "onLost: $network autoReconnect=$autoReconnectEnabled elapsed=${elapsed}ms fastRoamBack=$fastRoamBack")
                // Only notify if we had a real connection first.
                if (autoReconnectEnabled) onLost?.invoke(fastRoamBack)
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

    /**
     * If the phone is already connected to [ssid] — e.g. the user connected manually via
     * system Wi-Fi settings before tapping Connect/Retry, or a previous attempt left the
     * connection in place — reuses that network directly instead of going through
     * [connectToStripAp]'s [WifiNetworkSpecifier] request.
     *
     * That request always pops the system "connect to this network?" dialog, even when the
     * phone is already associated with [ssid]; accepting it then tears down and immediately
     * re-establishes the link, which is exactly what made Connect/Retry look like it "asks to
     * connect, then disconnects right away" when the phone was, in fact, already on the strip's
     * hotspot. Registers a passive [ConnectivityManager.NetworkCallback] (no specifier, so no
     * dialog) for the existing network so [keepAlive]/[onLost] keep working the same way as the
     * [connectToStripAp] path. Returns null if not currently connected to [ssid].
     */
    fun reuseIfAlreadyConnected(
        context: Context,
        ssid: String,
        onLost: ((fastRoamBack: Boolean) -> Unit)? = null,
    ): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val currentSsid = (caps.transportInfo as? android.net.wifi.WifiInfo)?.ssid
            .orEmpty().removeSurrounding("\"")
        if (currentSsid.isBlank() || currentSsid != ssid) return null

        // Clear any stale registration before taking over bookkeeping for this network.
        releaseStripNetwork(context)

        val availableAt = System.currentTimeMillis()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (network != net) return
                val elapsed = System.currentTimeMillis() - availableAt
                val fastRoamBack = elapsed < FAST_ROAM_BACK_MS
                Log.w("WifiHelper", "onLost (reused): $network autoReconnect=$autoReconnectEnabled elapsed=${elapsed}ms fastRoamBack=$fastRoamBack")
                if (autoReconnectEnabled) onLost?.invoke(fastRoamBack)
            }
        }
        activeConnection.set(StripConnection(net, cb, cm))
        cm.bindProcessToNetwork(net)
        runCatching { cm.reportNetworkConnectivity(net, true) }
        autoReconnectEnabled = true
        runCatching { cm.registerNetworkCallback(request, cb) }
        Log.i("WifiHelper", "Reusing already-connected network for ssid=$ssid: $net")
        return net
    }

    fun bindProcessToNetwork(context: Context, network: Network?) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.bindProcessToNetwork(network)
    }

    /**
     * Keeps the strip AP connection alive. Android (and especially OEM skins such as
     * Huawei EMUI, Samsung One UI, MIUI) treats a validated-as-"no internet" Wi-Fi network as
     * low priority and silently tears it down after a short grace period, particularly once the
     * screen is off or another network briefly appears. Calling this periodically (every few
     * seconds) while the wizard holds the strip AP tells the OS the network is working fine and
     * re-affirms the process binding in case the OS cleared it. Call from a loop (e.g. every
     * 4s) for as long as the wizard needs the connection; stop when [releaseStripNetwork] is
     * called. Returns false if there is no active strip connection (loop should stop).
     */
    fun keepAlive(): Boolean {
        val conn = activeConnection.get() ?: return false
        runCatching { conn.cm.reportNetworkConnectivity(conn.network, true) }
        runCatching { conn.cm.bindProcessToNetwork(conn.network) }
        return true
    }

    /** Unregisters the active callback and unbinds the process from the strip network. */
    fun releaseStripNetwork(context: Context) {
        autoReconnectEnabled = false
        val conn = activeConnection.getAndSet(null) ?: return
        runCatching { conn.cm.unregisterNetworkCallback(conn.callback) }
        runCatching { conn.cm.bindProcessToNetwork(null) }
    }

    /** True on Huawei/Honor (EMUI/HarmonyOS), which ships an aggressive "Wi-Fi+" auto roam-back
     * feature that is the most common cause of [FAST_ROAM_BACK_MS]-class disconnects. */
    fun isHuaweiLikeDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        return manufacturer.contains("huawei") || manufacturer.contains("honor")
    }

    /** User-facing guidance for a fast roam-back disconnect (see [FAST_ROAM_BACK_MS]). */
    fun fastRoamBackMessage(): String = if (isHuaweiLikeDevice()) {
        "Your phone instantly reconnected to your known Wi-Fi (Huawei \"Wi-Fi+\" auto-switch). " +
            "Turn it off: Settings → Wi-Fi → More settings → Wi-Fi+ (or Smart Network Switch), then tap Connect again."
    } else {
        "Your phone instantly reconnected to a known Wi-Fi network instead of staying on the strip's hotspot. " +
            "Turn off Wi-Fi auto-switching / \"Smart network switch\" for your other networks, then tap Connect again."
    }
}
