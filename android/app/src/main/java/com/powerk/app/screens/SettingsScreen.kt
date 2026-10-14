package com.powerk.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.powerk.app.Api
import com.powerk.app.AppTheme
import com.powerk.app.Lang
import com.powerk.app.R
import com.powerk.app.Server
import com.powerk.app.WifiHelper
import com.powerk.app.WifiScanResult
import com.powerk.app.components.ProvisionWizardSteps
import kotlinx.coroutines.launch

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

    // Provision wizard state
    var showWizard by remember { mutableStateOf(false) }
    var provisionStep by remember { mutableIntStateOf(0) }
    var provisionBusy by remember { mutableStateOf(false) }
    var provisionSsid by remember { mutableStateOf("") }
    var provisionStatus by remember { mutableStateOf<String?>(null) }
    var provisionOk by remember { mutableStateOf(false) }
    var stripNetworks by remember { mutableStateOf<List<WifiScanResult>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val ssid = WifiHelper.getCurrentSsid(context)
            if (ssid.isNotBlank() && !ssid.startsWith("TONLY_TAP_")) provisionSsid = ssid
        }
    }

    fun doScan() {
        scope.launch {
            scanning = true
            stripNetworks = WifiHelper.scanForStripNetworks(context)
            scanning = false
        }
    }

    fun commit() = Server.save(serverUrl, token)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
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
                if (!showWizard) {
                    FilledTonalButton(onClick = { showWizard = true; doScan() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.provision_start))
                    }
                } else {
                    if (!hasLocationPermission) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Info, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.provision_need_location), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FilledTonalButton(onClick = { permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) }) { Text(stringResource(R.string.grant_permission)) }
                            }
                        }
                    }
                    ProvisionWizardSteps(currentStep = provisionStep, busy = provisionBusy)
                    if (provisionStep == 0) {
                        FilledTonalButton(onClick = { doScan() }, enabled = hasLocationPermission && !scanning && !provisionBusy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.provision_step_1_action))
                        }
                        if (scanning) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.provision_step_1_found, stripNetworks.size), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            provisionStep = 1
                        }, enabled = !scanning && !provisionBusy && stripNetworks.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.provision_next))
                        }
                    } else if (provisionStep == 1) {
                        OutlinedTextField(value = provisionSsid, onValueChange = { provisionSsid = it }, label = { Text(stringResource(R.string.provision_ssid)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { provisionStep = 2 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provision_next)) }
                    } else if (provisionStep == 2) {
                        Text(stringResource(R.string.provision_step_3_body), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            provisionStep = 3; provisionBusy = true
                            scope.launch {
                                try {
                                    val firstSsid = stripNetworks.firstOrNull()?.ssid ?: ""
                                    val password = firstSsid.removePrefix("TONLY_TAP_").let { mac ->
                                        "mttl" + mac.replace(":", "").lowercase().takeLast(6)
                                    }
                                    Api.provisionAndRegister(Server.base, Server.token, Server.host, provisionSsid, password)
                                    provisionStatus = context.getString(R.string.provision_ok); provisionOk = true
                                } catch (e: Exception) {
                                    provisionStatus = e.message ?: "provision failed"; provisionOk = false
                                }
                                provisionBusy = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provision_send)) }
                    } else {
                        if (provisionBusy) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                        provisionStatus?.let { msg ->
                            Surface(shape = MaterialTheme.shapes.medium, color = if (provisionOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                                Text(msg, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (!provisionBusy) {
                            OutlinedButton(onClick = { provisionStep = 0; provisionStatus = null; provisionOk = false; showWizard = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provision_done)) }
                        }
                    }
                }
            }
        }
    }
}
