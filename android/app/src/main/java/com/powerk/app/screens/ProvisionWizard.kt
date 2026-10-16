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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
fun ProvisionWizardPanel(provisionVm: ProvisionViewModel = viewModel()) {
    val isStandalone = provisionVm.selectedStrip?.type == StripApType.STANDALONE

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val wifiPasswordFocusRequester = remember { FocusRequester() }

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
    fun resetWizard() = provisionVm.resetWizard()

    fun attemptConnectAndSend() = provisionVm.attemptConnectAndSend()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                    // Step 0: collect home Wi-Fi credentials before touching the strip AP, so the
                    // connect-and-send that follows can be atomic.
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
                                // Case-sensitive; disable autocorrect/autocapitalize so the SSID isn't altered.
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(onNext = { wifiPasswordFocusRequester.requestFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = provisionVm.provisionPassword,
                                onValueChange = { provisionVm.provisionPassword = it },
                                label = { Text(stringResource(R.string.field_wifi_password)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                // KeyboardType.Password (not just the visual mask) disables IME autocorrect.
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(wifiPasswordFocusRequester),
                            )
                        }
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                provisionVm.advanceToStripSelection()
                            },
                            enabled = provisionVm.provisionSsid.isNotBlank() && provisionVm.provisionPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.provision_next)) }
                    }

                    // Step 1: scan, select the strip AP, then connect & send Wi-Fi atomically in one tap.
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
                            // On failure, keep entered data so the user can just retry instead of starting over.
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
