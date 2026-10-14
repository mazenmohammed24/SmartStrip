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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds the Wi-Fi provisioning wizard's state and runs the connect+send+wait flow in
 * [viewModelScope] instead of a `SettingsScreen`-composable-scoped `rememberCoroutineScope()`.
 *
 * This fixes a real bug: Jetpack Navigation-Compose disposes a destination's composition (and
 * cancels any coroutine launched via `rememberCoroutineScope()`) the instant the user navigates
 * to another route — even a single stray tap on another bottom-nav tab while the strip-AP
 * connect/send/wait sequence is in flight. That silently aborted the wizard with no error shown,
 * which is what made the "connect" dialog look like it "didn't actually connect and disconnected
 * right after". Because this ViewModel is obtained via `viewModel()` it is scoped to the Settings
 * destination's back-stack entry, which survives tab switches (the entry is only cleared when
 * popped off the back stack), so the in-flight work now keeps running even if the user briefly
 * looks at another tab.
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

    private val ctx get() = getApplication<Application>()
    private var keepAliveJob: Job? = null

    /** Prefills [provisionSsid] from the currently-connected home Wi-Fi, if any. */
    fun prefillSsidFromCurrentWifi() {
        val ssid = WifiHelper.getCurrentSsid(ctx)
        if (ssid.isNotBlank() && ssid != "MTTL-W01-SETUP" && !ssid.startsWith("TONLY_TAP_")) {
            provisionSsid = ssid
        }
    }

    fun doScan(hasPermissions: Boolean, onNeedPermission: () -> Unit) {
        if (!hasPermissions) {
            onNeedPermission()
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
        foundStripIp = null
        showWizard = false
    }

    // Keep the strip AP connection alive while the wizard needs it (steps 1-2): periodically
    // re-affirm connectivity/binding so Android/OEM power management doesn't silently drop it.
    // Runs in viewModelScope so it keeps going even if the Settings screen briefly leaves
    // composition (e.g. the user taps another bottom-nav tab).
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

    // Sends the home Wi-Fi credentials and waits for registration. Called directly (not via a
    // separate viewModelScope.launch) from the connect success path so it runs in the same
    // coroutine with no extra scheduling gap — every millisecond matters here, since some OEM
    // Wi-Fi managers (e.g. Huawei's WifiPro) silently roam the radio back off the strip AP within
    // ~100-900ms of it becoming "available", regardless of any user-visible Wi-Fi+ toggle.
    private suspend fun sendWifiAndWait() {
        provisionBusy = true
        provisionStep = 2
        provisionStatus = ctx.getString(R.string.provision_sending)
        val standalone = selectedStrip?.type == StripApType.STANDALONE
        try {
            // Keep process-wide binding as a fallback, but configureStripWifi now also binds its
            // own socket/connection directly to boundStripNetwork, which is the part that
            // actually matters if an OEM Wi-Fi manager changes the process-wide default mid-race.
            boundStripNetwork?.let { WifiHelper.bindProcessToNetwork(ctx, it) }
            Api.configureStripWifi(
                ssid = provisionSsid.trim(),
                password = provisionPassword,
                standalone = standalone,
                network = boundStripNetwork,
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

    // Connects to the selected strip AP and immediately sends the already-collected Wi-Fi
    // credentials. Extracted so both the step-1 button and the step-3 "Retry" button (which
    // keeps all entered data and just re-attempts the race) can trigger the exact same flow.
    fun attemptConnectAndSend() {
        val strip = selectedStrip ?: return
        connectingAp = true
        connectAttempted = true
        reconnecting = false
        provisionOk = false
        provisionStatus = ctx.getString(R.string.provision_connecting_strip, strip.ssid)

        // Shared between the "already connected" fast path and the normal connect-request path
        // below, so both react to a dropped connection the same way.
        val onLost: (Boolean) -> Unit = { fastRoamBack ->
            // Only react while still in the "connecting" sub-phase (provisionStep == 1, before
            // sendWifiAndWait has moved us to step 2). Once sending has started, ignore onLost
            // entirely and let the send's own try/catch decide success/failure — resetting here
            // too would race with it.
            if (provisionStep == 1) {
                if (fastRoamBack) {
                    // The OS instantly roamed back to a known network (e.g. Huawei Wi-Fi+).
                    // Retrying immediately would just pop another system dialog in a loop —
                    // surface guidance and let the user retry manually instead. Unregister the
                    // callback now: leaving it registered keeps the system "connect to network"
                    // dialog reappearing to try to re-satisfy the dead request.
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
                            // Reconnected — send immediately, same as
                            // the initial success path, to stay inside
                            // the brief connectivity window.
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
            // If the phone is already connected to the strip AP — e.g. connected manually via
            // system Wi-Fi settings, or left over from a previous attempt — reuse that network
            // instead of issuing a brand-new connect request. A fresh WifiNetworkSpecifier
            // request always pops the system "connect to this network?" dialog even when
            // already associated, and accepting it tears down and re-establishes the link —
            // which is exactly what made Connect/Retry look like it "asks to connect, then
            // disconnects right away" when the phone was already on the strip's hotspot.
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
                // Send the already-collected home Wi-Fi credentials right away,
                // in the same coroutine, to minimize the gap between the strip
                // AP becoming available and the setup request reaching it.
                sendWifiAndWait()
            }
        }
    }

    override fun onCleared() {
        stopKeepAlive()
        WifiHelper.releaseStripNetwork(ctx)
        super.onCleared()
    }
}
