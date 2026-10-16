package com.powerk.app.screens

import android.app.Application
import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powerk.app.Api
import com.powerk.app.R
import com.powerk.app.Server
import com.powerk.app.StripApType
import com.powerk.app.WifiHelper
import com.powerk.app.WifiScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the Wi-Fi provisioning wizard's state and runs the connect+send+wait flow in
 * [viewModelScope] (not a composable-scoped `rememberCoroutineScope()`) so it survives
 * Navigation-Compose disposing the Settings destination on a stray tab switch mid-flow.
 */
class ProvisionViewModel(application: Application) : AndroidViewModel(application) {
    var showWizard by mutableStateOf(false)
    var provisionStep by mutableIntStateOf(0)
    var provisionBusy by mutableStateOf(false)
    var provisionSsid by mutableStateOf("")
    var provisionPassword by mutableStateOf("")
    var selectedStrip by mutableStateOf<WifiScanResult?>(null)
    var connectingAp by mutableStateOf(false)
    var connectAttempted by mutableStateOf(false)
    var boundStripNetwork by mutableStateOf<Network?>(null)
    var provisionStatus by mutableStateOf<String?>(null)
    var provisionOk by mutableStateOf(false)
    var stripNetworks by mutableStateOf<List<WifiScanResult>>(emptyList())
    var scanning by mutableStateOf(false)
    var foundStripIp by mutableStateOf<String?>(null)
    var reconnecting by mutableStateOf(false)

    /** False when location services are off, which makes Android report every Wi-Fi scan as empty. */
    var locationEnabled by mutableStateOf(true)

    private val ctx get() = getApplication<Application>()
    private var keepAliveJob: Job? = null

    // Persists home Wi-Fi SSID/password across app restarts; cleared only by resetWizard().
    private val provisionPrefs get() = ctx.getSharedPreferences("powerk_provision", android.content.Context.MODE_PRIVATE)

    init {
        provisionSsid = provisionPrefs.getString(PREF_SSID, "").orEmpty()
        provisionPassword = provisionPrefs.getString(PREF_PASSWORD, "").orEmpty()
    }

    private fun persistWifiCredentials() {
        provisionPrefs.edit()
            .putString(PREF_SSID, provisionSsid)
            .putString(PREF_PASSWORD, provisionPassword)
            .apply()
    }

    private fun clearPersistedWifiCredentials() {
        provisionPrefs.edit().remove(PREF_SSID).remove(PREF_PASSWORD).apply()
    }

    // Controller IP the strip reports back to on port 10086; must be resolved while still on
    // home Wi-Fi, before joining the strip's isolated setup AP.
    private var resolvedControllerIp: String? = null

    /** Prefills [provisionSsid] from the currently-connected home Wi-Fi, if any. */
    fun prefillSsidFromCurrentWifi() {
        val ssid = WifiHelper.getCurrentSsid(ctx)
        if (ssid.isNotBlank() && ssid != "MTTL-W01-SETUP" && !ssid.startsWith("TONLY_TAP_")) {
            provisionSsid = ssid
        }
    }

    // Leaves step 0 for step 1, kicking off controller IP resolution now while still on normal Wi-Fi.
    fun advanceToStripSelection() {
        persistWifiCredentials()
        provisionStep = 1
        if (resolvedControllerIp == null) {
            viewModelScope.launch {
                resolvedControllerIp = runCatching {
                    withContext(Dispatchers.IO) { Api.provisionAddressFromServer(Server.base, Server.token).ip }
                }.getOrNull()
            }
        }
    }

    fun doScan(hasPermissions: Boolean, onNeedPermission: () -> Unit) {
        if (!hasPermissions) {
            onNeedPermission()
            return
        }
        // Android reports an empty scan list (not an error) when location services are off, so
        // surface the real cause instead of a misleading "no strip APs found".
        locationEnabled = WifiHelper.isLocationEnabled(ctx)
        if (!locationEnabled) {
            stripNetworks = emptyList()
            selectedStrip = null
            provisionOk = false
            provisionStatus = ctx.getString(R.string.provision_location_off)
            return
        }
        viewModelScope.launch {
            scanning = true
            provisionStatus = ctx.getString(R.string.provision_scanning)
            prefillSsidFromCurrentWifi()
            stripNetworks = WifiHelper.scanForStripNetworks(ctx)
            selectedStrip = stripNetworks.firstOrNull()
            provisionStatus = if (stripNetworks.isEmpty()) ctx.getString(R.string.provision_no_strips_found) else null
            provisionOk = stripNetworks.isNotEmpty()
            scanning = false
        }
    }

    /** Opens the system location-source page, mirroring the vendor app's `openLocationSettings`. */
    fun openLocationSettings() {
        runCatching { ctx.startActivity(WifiHelper.locationSettingsIntent()) }
    }

    fun resetWizard() {
        stopKeepAlive()
        WifiHelper.autoReconnectEnabled = false
        WifiHelper.releaseStripNetwork(ctx)
        boundStripNetwork = null
        reconnecting = false
        connectingAp = false
        connectAttempted = false
        provisionStep = 0
        provisionStatus = null
        provisionOk = false
        provisionPassword = ""
        clearPersistedWifiCredentials()
        foundStripIp = null
        resolvedControllerIp = null
        showWizard = false
    }

    // Keeps the strip AP connection alive during steps 1-2 so Android/OEM power management
    // doesn't silently drop it; runs in viewModelScope to survive tab switches.
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = viewModelScope.launch {
            while (boundStripNetwork != null && provisionStep in 1..2) {
                delay(4_000L)
                if (!WifiHelper.keepAlive()) break
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    // Runs inline (not a separate launch) so no scheduling gap delays sending credentials, since
    // some OEM Wi-Fi managers roam off the strip AP within ~100-900ms of it connecting.
    private suspend fun sendWifiAndWait() {
        provisionBusy = true
        provisionStep = 2
        provisionStatus = ctx.getString(R.string.provision_sending)
        val standalone = selectedStrip?.type == StripApType.STANDALONE
        try {
            // configureStripWifi also binds its own socket directly to boundStripNetwork, which
            // matters more than this process-wide fallback.
            boundStripNetwork?.let { WifiHelper.bindProcessToNetwork(ctx, it) }
            // Prefer the background-resolved address from advanceToStripSelection; fall back to
            // the local computation if that probe hasn't finished.
            val controllerIp = resolvedControllerIp
                ?: withContext(Dispatchers.IO) { Api.resolveServerAddressForStrip(Server.base).ip }
            Api.configureStripWifi(
                ssid = provisionSsid.trim(),
                password = provisionPassword,
                standalone = standalone,
                network = boundStripNetwork,
                controllerIp = controllerIp,
            )
            stopKeepAlive()
            WifiHelper.releaseStripNetwork(ctx)
            boundStripNetwork = null
            provisionStatus = ctx.getString(R.string.provision_waiting_registration)
            if (standalone) {
                // Strip will reconnect to home Wi-Fi; scan LAN to find it
                val ip = Api.waitForStandaloneStrip()
                if (ip != null) {
                    foundStripIp = ip
                    provisionStatus = "Strip online at http://$ip — open its web UI to control outlets."
                    provisionOk = true
                } else {
                    provisionStatus = "Wi-Fi sent. Strip should appear on your home network shortly. Check your router for MTTL-W01."
                    provisionOk = true
                }
            } else {
                val result = Api.waitForRegistration(Server.base, Server.token)
                provisionStatus = result.message
                provisionOk = result.registered
            }
        } catch (e: Exception) {
            stopKeepAlive()
            WifiHelper.releaseStripNetwork(ctx)
            boundStripNetwork = null
            provisionStatus = e.message ?: "Provision failed"
            provisionOk = false
        }
        provisionBusy = false
        provisionStep = 3
    }

    // Connects to the selected strip AP and sends Wi-Fi immediately; shared by the step-1 button
    // and the step-3 "Retry" button.
    fun attemptConnectAndSend() {
        val strip = selectedStrip ?: return
        // Reject credentials the strip could never join before spending the brief connectivity window.
        WifiHelper.homeWifiValidationError(provisionSsid, provisionPassword)?.let { problem ->
            provisionStatus = problem
            provisionOk = false
            return
        }
        connectingAp = true
        connectAttempted = true
        reconnecting = false
        provisionOk = false
        provisionStatus = ctx.getString(R.string.provision_connecting_strip, strip.ssid)

        // Shared by the "already connected" fast path and the normal connect-request path below.
        val onLost: (Boolean) -> Unit = { fastRoamBack ->
            // Ignore once sendWifiAndWait has moved us past step 1 - its own try/catch owns success/failure then.
            if (provisionStep == 1) {
                if (fastRoamBack) {
                    // OS instantly roamed back to a known network; unregister and let the user retry
                    // manually instead of looping another system dialog.
                    stopKeepAlive()
                    WifiHelper.releaseStripNetwork(ctx)
                    reconnecting = false
                    connectAttempted = false
                    boundStripNetwork = null
                    provisionOk = false
                    provisionStatus = WifiHelper.fastRoamBackMessage()
                } else {
                    reconnecting = true
                    viewModelScope.launch {
                        val recon = WifiHelper.connectToStripAp(
                            context = ctx,
                            ssid = strip.ssid,
                        )
                        reconnecting = false
                        if (recon == null) {
                            stopKeepAlive()
                            WifiHelper.releaseStripNetwork(ctx)
                            connectAttempted = false
                            boundStripNetwork = null
                            provisionOk = false
                            provisionStatus = "Reconnection to strip AP failed. Move closer and try again."
                        } else {
                            // Reconnected — send immediately to stay inside the connectivity window.
                            boundStripNetwork = recon
                            provisionOk = true
                            startKeepAlive()
                            sendWifiAndWait()
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            // Reuse the network if already connected to the strip AP - a fresh connect request
            // pops the system dialog and tears down/re-establishes the link even when already associated.
            val network = WifiHelper.reuseIfAlreadyConnected(ctx, strip.ssid, onLost)
                ?: WifiHelper.connectToStripAp(context = ctx, ssid = strip.ssid, onLost = onLost)
            connectingAp = false
            if (network == null) {
                provisionStatus = ctx.getString(R.string.provision_connect_strip_failed)
                provisionOk = false
                connectAttempted = false
            } else {
                boundStripNetwork = network
                provisionOk = true
                startKeepAlive()
                // Send immediately, same coroutine, to minimize the gap before the strip AP drops.
                sendWifiAndWait()
            }
        }
    }

    override fun onCleared() {
        stopKeepAlive()
        WifiHelper.releaseStripNetwork(ctx)
        super.onCleared()
    }

    private companion object {
        const val PREF_SSID = "last_wifi_ssid"
        const val PREF_PASSWORD = "last_wifi_password"
    }
}
