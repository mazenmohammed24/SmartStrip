package com.powerk.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powerk.app.Api
import com.powerk.app.DiscoveredStrip
import com.powerk.app.R
import com.powerk.app.Server
import com.powerk.app.components.BrandScanRadar
import com.powerk.app.components.EmptyState
import com.powerk.app.components.MessageCard
import com.powerk.app.components.MetricPill
import com.powerk.app.components.ScreenTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScanScreen(onStripAdopted: () -> Unit) {
    var scanning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<DiscoveredStrip>>(emptyList()) }
    var adopting by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun startScan() {
        scope.launch {
            scanning = true; results = emptyList(); error = null; progress = 0f
            try {
                val scanId = Api.startScan(Server.base, Server.token)
                // Poll until done
                var done = false
                var attempts = 0
                while (!done && attempts < 30) {
                    attempts++
                    progress = (attempts / 30f).coerceIn(0f, 1f)
                    val scanResult = Api.getScanResult(Server.base, Server.token, scanId)
                    if (scanResult.done) { results = scanResult.strips; done = true }
                    else delay(1000)
                }
            } catch (e: Exception) { error = e.message }
            scanning = false; progress = 1f
        }
    }

    LaunchedEffect(Unit) { startScan() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FilledTonalButton(onClick = { startScan() }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, null)
                Text(stringResource(R.string.scan_start))
            }
        }
        if (scanning) {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BrandScanRadar(scanning = true)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.scan_in_progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!scanning && results.isEmpty() && error == null) {
            item { EmptyState(stringResource(R.string.scan_empty_title), stringResource(R.string.scan_empty_body), stringResource(R.string.scan_start), onAction = { startScan() }) }
        }
        items(results, key = { it.driverStripId }) { strip ->
            DiscoveredStripCard(
                strip = strip,
                adopting = adopting == strip.driverStripId,
                onAdopt = {
                    scope.launch {
                        adopting = strip.driverStripId
                        try {
                            Api.adoptStrip(Server.base, Server.token, strip)
                            onStripAdopted()
                        } catch (e: Exception) { error = e.message }
                        adopting = null
                    }
                },
            )
        }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoveredStripCard(strip: DiscoveredStrip, adopting: Boolean, onAdopt: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strip.suggestedName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MetricPill(strip.model)
            }
            HorizontalDivider()
            Text("${strip.ipAddress ?: "?"} · ${strip.relayCount} outlets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (adopting) {
                CircularWavyProgressIndicator(Modifier.fillMaxWidth())
            } else {
                FilledTonalButton(onClick = onAdopt, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.scan_adopt))
                }
            }
        }
    }
}
