package com.powerk.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.powerk.app.Api
import com.powerk.app.PowerHistory
import com.powerk.app.PowerSummary
import com.powerk.app.R
import com.powerk.app.Server
import com.powerk.app.Snapshot
import com.powerk.app.components.EmptyState
import com.powerk.app.components.MessageCard
import com.powerk.app.components.MetricPill
import com.powerk.app.components.ScreenTitle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PowerScreen(snapshot: Snapshot?) {
    val strips = snapshot?.strips.orEmpty()
    var selectedId by remember(strips) { mutableStateOf(strips.firstOrNull()?.mac.orEmpty()) }
    var range by remember { mutableStateOf("7d") }
    var summary by remember { mutableStateOf<PowerSummary?>(null) }
    var history by remember { mutableStateOf<PowerHistory?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selected = strips.firstOrNull { it.mac == selectedId } ?: strips.firstOrNull()

    fun load() {
        val id = selected?.mac ?: return
        scope.launch {
            try {
                summary = Api.powerSummary(Server.base, Server.token, id)
                history = Api.powerHistory(Server.base, Server.token, id, range)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "power failed"
            }
        }
    }

    LaunchedEffect(selected?.mac, range) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle(stringResource(R.string.power_title), stringResource(R.string.power_body)) }
        if (strips.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_strip_title), stringResource(R.string.power_empty), stringResource(R.string.refresh), onAction = { load() }) }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("day", "7d", "30d", "90d").forEach { opt ->
                        FilledTonalButton(onClick = { range = opt }, modifier = Modifier.weight(1f)) { Text(opt, maxLines = 1) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryMetric(stringResource(R.string.power_today), "${summary?.todayKwh ?: selected?.energyKwh ?: 0.0}", "kWh", Modifier.weight(1f))
                    SummaryMetric(stringResource(R.string.power_live), "${(summary?.liveWatts ?: selected?.powerW ?: 0.0).roundToInt()}", "W", Modifier.weight(1f))
                }
            }
            item { SummaryMetric(stringResource(R.string.power_cost), "${summary?.todayCost ?: 0.0}", summary?.currency ?: "USD", Modifier.fillMaxWidth()) }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.power_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val points = history?.points.orEmpty()
                        if (points.isEmpty()) {
                            Text(stringResource(R.string.power_no_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            PowerHistoryChart(points = points.takeLast(12).map { it.avgW })
                            points.takeLast(12).forEach { point ->
                                val watts = point.avgW.roundToInt()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(point.bucketStart.take(16), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    Box(Modifier.weight(2f).height(8.dp).padding(horizontal = 4.dp)) {
                                        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)))
                                        Box(Modifier.fillMaxWidth((watts / 2000f).coerceIn(0f, 1f)).height(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                                    }
                                    Text("$watts W", Modifier.width(64.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.power_strip_select), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        selected?.outlets?.forEach { outlet ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.outlet_n, outlet.n), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                MetricPill("${outlet.powerW} W")
                            }
                        }
                    }
                }
            }
        }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PowerHistoryChart(points: List<Double>) {
    if (points.isEmpty()) return
    val maxW = points.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(80.dp).padding(vertical = 8.dp)) {
        val barW = size.width / points.size
        val pad = barW * 0.2f
        points.forEachIndexed { i, w ->
            val frac = (w / maxW).toFloat().coerceIn(0f, 1f)
            val barH = frac * size.height
            val left = i * barW + pad
            val right = (i + 1) * barW - pad
            drawRect(color = surface, topLeft = Offset(left, 0f), size = Size(right - left, size.height))
            drawRect(color = primary, topLeft = Offset(left, size.height - barH), size = Size(right - left, barH))
        }
    }
}
