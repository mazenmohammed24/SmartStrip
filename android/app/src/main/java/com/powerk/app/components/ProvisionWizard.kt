package com.powerk.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.powerk.app.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProvisionWizard(
    currentStep: Int,
    busy: Boolean,
    connectingAp: Boolean,
    hasLocationPermission: Boolean,
    scanning: Boolean,
    selectedStrip: String?,
    provisionSsid: String,
    provisionPassword: String,
    serverUrl: String,
    provisionIp: String,
    provisionStatus: String?,
    provisionOk: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onShowStripDropdown: () -> Unit,
    stripDropdown: @Composable () -> Unit,
    onConnectStrip: () -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNextStep1: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onProvisionIpChange: (String) -> Unit,
    onNextStep2: () -> Unit,
    onFinish: () -> Unit,
    onStartOver: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProvisionWizardSteps(currentStep = currentStep, busy = busy || connectingAp)

        if (!hasLocationPermission) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.provision_need_location), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = onRequestPermission) { Text(stringResource(R.string.grant_permission)) }
                }
            }
        }

        when (currentStep) {
            0 -> {
                FilledTonalButton(
                    onClick = onScan,
                    enabled = hasLocationPermission && !scanning && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.provision_step_1_action))
                }
                if (scanning) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                stripDropdown()
                Button(
                    enabled = selectedStrip != null && !connectingAp,
                    onClick = onConnectStrip,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.provision_step_1_button)) }
            }
            1 -> {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(value = provisionSsid, onValueChange = onSsidChange, label = { Text(stringResource(R.string.field_wifi_ssid)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = provisionPassword, onValueChange = onPasswordChange, label = { Text(stringResource(R.string.field_wifi_password)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Button(enabled = provisionPassword.isNotBlank(), onClick = onNextStep1, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provision_next)) }
            }
            2 -> {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(value = serverUrl, onValueChange = onServerUrlChange, label = { Text(stringResource(R.string.field_server_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = provisionIp, onValueChange = onProvisionIpChange, label = { Text(stringResource(R.string.field_server_ip_for_strip)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Button(enabled = serverUrl.isNotBlank() && provisionIp.isNotBlank(), onClick = onNextStep2, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provision_next)) }
            }
            3 -> {
                Button(enabled = !busy && !connectingAp, onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provision_step_4_button)) }
            }
            4 -> {
                Button(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provision_start_over)) }
            }
        }

        if (busy || connectingAp) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
        AnimatedVisibility(visible = provisionStatus != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (provisionOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (provisionOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(provisionStatus ?: "", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ProvisionWizardSteps(currentStep: Int, busy: Boolean) {
    val labels = listOf(
        stringResource(R.string.provision_wizard_1),
        stringResource(R.string.provision_wizard_2),
        stringResource(R.string.provision_wizard_3),
        stringResource(R.string.provision_wizard_4),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val done = currentStep > index
                val active = currentStep == index
                Surface(
                    shape = CircleShape,
                    color = when {
                        done -> MaterialTheme.colorScheme.primary
                        active -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = when {
                        done -> MaterialTheme.colorScheme.onPrimary
                        active -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (done) "✓" else "${index + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    label + if (busy && active) "…" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
