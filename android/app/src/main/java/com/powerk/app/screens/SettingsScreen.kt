package com.powerk.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.powerk.app.Api
import com.powerk.app.AppTheme
import com.powerk.app.Lang
import com.powerk.app.R
import com.powerk.app.Server
import com.powerk.app.StripApType
import com.powerk.app.WifiHelper
import com.powerk.app.WifiScanResult
import com.powerk.app.components.ProvisionWizardSteps
import kotlinx.coroutines.launch

/** Draws a Wi-Fi arc icon with 1–3 arcs lit based on [rssi] (dBm), matching Android's signal levels. */
@Composable
private fun WifiStrengthIcon(rssi: Int, modifier: Modifier = Modifier) {
    val bars = when {
        rssi >= -55 -> 3
        rssi >= -70 -> 2
        else        -> 1
    }
    val activeColor   = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.85f
        val strokeW = size.width * 0.13f
        val startAngle = 210f
        val sweepAngle = 120f
        // 3 arc radii, smallest → largest
        val radii = listOf(size.width * 0.22f, size.width * 0.42f, size.width * 0.62f)
        radii.forEachIndexed { i, r ->
            val color = if (i < bars) activeColor else inactiveColor
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
        }
        // centre dot
        drawCircle(color = activeColor, radius = strokeW * 0.6f, center = Offset(cx, cy))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen() {
    var serverUrl by remember { mutableStateOf(Server.serverUrl) }
    var token by remember { mutableStateOf(Server.token) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(Lang.code) }
    var accentColor by remember { mutableStateOf(AppTheme.accentColor) }

    // Provision wizard state lives in ProvisionViewModel (scoped to this destination's
    // back-stack entry) so the in-flight connect+send+wait coroutine survives navigating away
    // to another bottom-nav tab, instead of being silently cancelled with the old
    // composable-scoped rememberCoroutineScope(). See ProvisionViewModel's doc comment.
    // All wizard-state reads/writes below go through `provisionVm.*` directly.
    val provisionVm: ProvisionViewModel = viewModel()

    val isStandalone = provisionVm.selectedStrip?.type == StripApType.STANDALONE

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }
    var hasNearbyWifiPermission by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < 33 ||
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                    context.checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        granted[android.Manifest.permission.ACCESS_FINE_LOCATION]?.let { hasLocationPermission = it }
        granted[android.Manifest.permission.NEARBY_WIFI_DEVICES]?.let { hasNearbyWifiPermission = it }
    }

    fun requestWifiPermissions() {
        val perms = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 33) perms += android.Manifest.permission.NEARBY_WIFI_DEVICES
        permissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            provisionVm.prefillSsidFromCurrentWifi()
        }
    }

    fun doScan() = provisionVm.doScan(hasLocationPermission && hasNearbyWifiPermission) { requestWifiPermissions() }

    fun openLocationSettings() = provisionVm.openLocationSettings()

    fun commit() = Server.save(serverUrl, token)

    fun resetWizard() = provisionVm.resetWizard()

    fun attemptConnectAndSend() = provisionVm.attemptConnectAndSend()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        // Language card
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val options = listOf(null to stringResource(R.string.lang_system), "en" to stringResource(R.string.lang_english), "ar" to stringResource(R.string.lang_arabic))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (value, label) ->
                        SegmentedButton(selected = lang == value, onClick = { lang = value; Lang.set(context, value) }, shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)) { Text(label) }
                    }
                }
            }
        }

        // Theme card
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.theme_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val themeOptions = listOf(AppTheme.SYSTEM to stringResource(R.string.theme_system), AppTheme.LIGHT to stringResource(R.string.theme_light), AppTheme.DARK to stringResource(R.string.theme_dark))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    themeOptions.forEachIndexed { i, (value, label) ->
                        SegmentedButton(selected = AppTheme.mode == value, onClick = { AppTheme.set(value) }, shape = SegmentedButtonDefaults.itemShape(index = i, count = themeOptions.size)) { Text(label) }
                    }
                }
                Text(stringResource(R.string.theme_accent), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("#1B6B45", "#176F91", "#7C3AED", "#C2410C", "#BE123C").forEach { color ->
                        OutlinedButton(onClick = { accentColor = color; AppTheme.setAccent(color) }, modifier = Modifier.weight(1f)) {
                            Text(if (accentColor.equals(color, ignoreCase = true)) "●" else " ")
                        }
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(value = accentColor, onValueChange = { accentColor = it; AppTheme.setAccent(it) }, label = { Text(stringResource(R.string.theme_custom_color)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Server card
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.server_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.server_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text(stringResource(R.string.field_server_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(stringResource(R.string.field_token)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { commit(); status = context.getString(R.string.saved); statusOk = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                    FilledTonalButton(enabled = !busy, onClick = {
                        commit(); busy = true; status = null
                        scope.launch {
                            try {
                                val snap = Api.snapshot(Server.base, Server.token)
                                status = context.getString(R.string.connected_summary, snap.strips.size, snap.strips.count { it.online })
                                statusOk = true
                            } catch (e: Exception) { status = e.message ?: "connection failed"; statusOk = false }
                            busy = false
                        }
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.test)) }
                }
                if (busy) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                AnimatedVisibility(visible = status != null, enter = fadeIn(), exit = fadeOut()) {
                    Surface(shape = MaterialTheme.shapes.medium, color = if (statusOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, contentColor = if (statusOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(status ?: "", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedButton(onClick = { commit(); context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Server.base))) }, modifier = Modifier.fillMaxWidth(), enabled = Server.configured) {
                    Text(stringResource(R.string.open_web_ui))
                }
            }
        }

        // Provision wizard card
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.provision_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.provision_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!provisionVm.showWizard) {
                    FilledTonalButton(onClick = { provisionVm.showWizard = true; doScan() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.provision_start))
                    }
                } else {
                    if (!hasLocationPermission || !hasNearbyWifiPermission) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Info, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.provision_need_location), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FilledTonalButton(onClick = { requestWifiPermissions() }) { Text(stringResource(R.string.grant_permission)) }
                            }
                        }
                    }

                    ProvisionWizardSteps(currentStep = provisionVm.provisionStep, busy = provisionVm.provisionBusy)

                    // Step 0: Enter home Wi-Fi credentials first. Collecting these before we ever
                    // touch the strip AP lets the connect-and-send that follows be atomic, which
                    // matters because some OEM Wi-Fi managers (e.g. Huawei's WifiPro) silently
                    // roam the radio back off a no-internet AP within ~100-900ms of it connecting.
                    if (provisionVm.provisionStep == 0) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.provision_home_wifi_hint),
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = provisionVm.provisionSsid,
                                onValueChange = { provisionVm.provisionSsid = it },
                                label = { Text(stringResource(R.string.field_wifi_ssid)) },
                                singleLine = true,
                                // SSIDs are case-sensitive and can be anything the router admin
                                // typed; autocorrect/autocapitalize "fixing" it behind the user's
                                // back silently sends the strip a home network name that doesn't
                                // exist, which it then can never join.
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Ascii,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = provisionVm.provisionPassword,
                                onValueChange = { provisionVm.provisionPassword = it },
                                label = { Text(stringResource(R.string.field_wifi_password)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                // KeyboardType.Password (not just the visual mask above) is what
                                // actually tells the IME to turn off autocorrect/autocapitalize/
                                // predictive suggestions. Without it the field *looks* like a
                                // password box but Gboard etc. still "autocorrects" the typed
                                // passphrase — the strip then gets a plausible-but-wrong password,
                                // can never join the home network, and is left endlessly retrying
                                // (the panicking/flashing behaviour reported from real strips).
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Button(
                            onClick = { provisionVm.provisionStep = 1 },
                            enabled = provisionVm.provisionSsid.isNotBlank() && provisionVm.provisionPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.provision_next)) }
                    }

                    // Step 1: Scan, select the strip AP, then connect & send Wi-Fi atomically —
                    // in one tap, with no separate "confirm" step, to minimize the time the strip
                    // AP connection needs to survive.
                    else if (provisionVm.provisionStep == 1) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.provision_step_0_hint),
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!provisionVm.locationEnabled) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.provision_location_off), style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { openLocationSettings() }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.provision_open_location_settings))
                                    }
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { doScan() },
                            enabled = !provisionVm.scanning && !provisionVm.provisionBusy && !provisionVm.connectingAp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.provision_step_1_action))
                        }
                        if (provisionVm.scanning || provisionVm.connectingAp || provisionVm.reconnecting) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                        if (provisionVm.reconnecting) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Lost AP connection — reconnecting…",
                                    Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.provision_step_1_found, provisionVm.stripNetworks.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        provisionVm.stripNetworks.forEach { network ->
                            val isStandaloneNet = network.type == StripApType.STANDALONE
                            val typeTag = if (isStandaloneNet) "standalone" else "stock"
                            val selected = provisionVm.selectedStrip?.ssid == network.ssid
                            OutlinedButton(
                                onClick = {
                                    provisionVm.selectedStrip = network
                                    provisionVm.connectAttempted = false
                                    provisionVm.provisionStatus = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                WifiStrengthIcon(network.rssi, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    (if (selected) "✓  " else "") + network.ssid,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    typeTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Firmware type info banner for the selected strip
                        provisionVm.selectedStrip?.let { strip ->
                            val bannerColor = if (strip.type == StripApType.STANDALONE)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                            val bannerText = if (strip.type == StripApType.STANDALONE)
                                "✅  Standalone firmware detected — full local control available."
                            else
                                "⚠️  Stock firmware detected — the strip will be provisioned to your home Wi-Fi and registered on this server."
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = bannerColor,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(bannerText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.provision_will_send, provisionVm.provisionSsid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!provisionVm.connectAttempted) {
                        Button(
                            onClick = { attemptConnectAndSend() },
                            enabled = provisionVm.selectedStrip != null && !provisionVm.scanning && !provisionVm.provisionBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.provision_step_1_button))
                        }
                        }
                    }

                    // Step 2: Sending / waiting
                    else if (provisionVm.provisionStep == 2) {
                        if (provisionVm.provisionBusy) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                    }

                    // Step 3: Done
                    else {
                        provisionVm.provisionStatus?.let { msg ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (provisionVm.provisionOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (provisionVm.provisionOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(msg, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        provisionVm.foundStripIp?.let { ip ->
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("http://$ip"))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Open strip web UI (http://$ip)") }
                        }
                        if (!provisionVm.provisionBusy) {
                            // On failure, keep the already-entered Wi-Fi/strip selection and let
                            // the user re-race the connect+send in one tap, instead of forcing a
                            // full "Start over" (re-type SSID/password) for what is often just a
                            // timing loss against the OS. Both are available.
                            if (!provisionVm.provisionOk) {
                                Button(
                                    onClick = {
                                        provisionVm.connectAttempted = false
                                        provisionVm.reconnecting = false
                                        WifiHelper.releaseStripNetwork(context)
                                        provisionVm.boundStripNetwork = null
                                        provisionVm.provisionStep = 1
                                        attemptConnectAndSend()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.provision_retry)) }
                            }
                            OutlinedButton(
                                onClick = { resetWizard() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.provision_done)) }
                        }
                    }

                    // Status banner (shown during steps 0–2)
                    if (provisionVm.provisionStep < 3) {
                        AnimatedVisibility(visible = provisionVm.provisionStatus != null, enter = fadeIn(), exit = fadeOut()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (provisionVm.provisionOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (provisionVm.provisionOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(provisionVm.provisionStatus ?: "", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        OutlinedButton(onClick = { resetWizard() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.provision_start_over))
                        }
                    }
                }
            }
        }
    }
}
