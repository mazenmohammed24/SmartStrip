package com.powerk.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.AppSpacing
import com.powerk.app.EnergyReport
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.powerk.app.TariffInfo
import com.powerk.app.StripsViewModel
import com.powerk.app.components.ConfirmDialog
import com.powerk.app.components.EmptyState
import com.powerk.app.components.Mono
import com.powerk.app.components.Pill
import com.powerk.app.components.Privacy
import com.powerk.app.components.Refreshable
import com.powerk.app.components.iconGlyph

import com.powerk.app.components.ScreenHeader
import com.powerk.app.components.SectionLabel
import com.powerk.app.components.SegTabs
import com.powerk.app.components.Shimmer
import com.powerk.app.components.Tone
import com.powerk.app.components.VCard
import com.powerk.app.components.VTextField
import com.powerk.app.components.SocketFace
import com.powerk.app.components.tap
import com.powerk.app.components.fmtKwh
import com.powerk.app.components.fmtNum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private val RANGES = listOf(7, 30, 90)

@Composable
fun EnergyScreen(vm: StripsViewModel, initialStrip: String?, onBack: (() -> Unit)?) {
    val strips = vm.strips
    var picked by rememberSaveable { mutableStateOf(initialStrip) }
    val mac = strips.firstOrNull { it.mac == picked }?.mac ?: strips.firstOrNull()?.mac
    var rangeIndex by rememberSaveable { mutableStateOf(0) }
    val days = RANGES[rangeIndex]
    var tariff by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    // Pull-to-refresh: reload the report for the strip and range currently on screen.
    fun refreshEnergy() {
        val m = mac ?: return
        scope.launch {
            refreshing = true
            try { vm.loadEnergy(m, days).join() } finally { refreshing = false }
        }
    }

    LaunchedEffect(mac, days) {
        if (mac == null) return@LaunchedEffect
        while (true) {
            vm.loadEnergy(mac, days)
            delay(30_000)
        }
    }
    val report = mac?.let { vm.energy["$days:$it"] }

    Refreshable(refreshing, { refreshEnergy() }, Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.listGap),
        ) {
            item(key = "header") { ScreenHeader(strips.firstOrNull { it.mac == mac }?.let { Privacy.name(it) } ?: "Consumption", onBack = onBack, eyebrow = "Energy") }
            if (mac == null) {
                item { EmptyState("No strips", "Add a strip to see its consumption.") }
                return@LazyColumn
            }
            if (strips.size > 1) {
                item(key = "strips") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        strips.forEach { s -> FilterChip(selected = s.mac == mac, onClick = { picked = s.mac }, label = { Text(Privacy.name(s), maxLines = 1) }) }
                    }
                }
            }
            item(key = "range") { SegTabs(listOf("7 days", "30 days", "90 days"), rangeIndex, { rangeIndex = it }) }
            if (report == null) {
                item(key = "sk1") { Shimmer(Modifier.fillMaxWidth().height(130.dp)) }
                item(key = "sk2") { Shimmer(Modifier.fillMaxWidth().height(200.dp)) }
            } else {
                item(key = "total") { TotalCard(report, onTariff = { tariff = true }) }
                item(key = "chart") {
                    var sel by remember(report) { mutableStateOf(report.daily.lastIndex) }
                    val dim = MaterialTheme.colorScheme.onSurfaceVariant
                    VCard(padding = 18.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Daily usage", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("kWh / day", fontSize = 14.sp, color = dim)
                        }
                        report.daily.getOrNull(sel)?.let {
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Text(it.date.takeLast(5), fontFamily = Mono, fontSize = 15.sp, color = dim)
                                Text("  —  ", fontFamily = Mono, fontSize = 15.sp, color = dim)
                                Text("${fmtKwh(it.kwh)} kWh", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        BarChart(report.daily.map { it.kwh }, sel, { sel = it })
                        if (report.daily.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val labelEvery = if (report.daily.size <= 8) 1 else report.daily.size / 6
                            Row(Modifier.fillMaxWidth()) {
                                report.daily.forEachIndexed { i, d ->
                                    Text(
                                        if (i % labelEvery == 0 || i == report.daily.lastIndex && report.daily.size <= 8) d.date.takeLast(5) else "",
                                        Modifier.weight(1f), fontFamily = Mono, fontSize = 10.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                                        color = if (i == sel) MaterialTheme.colorScheme.primary else dim,
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "outlets") {
                    val dim = MaterialTheme.colorScheme.onSurfaceVariant
                    VCard(padding = 18.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("By outlet", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("last $days days", fontSize = 14.sp, color = dim)
                        }
                        Spacer(Modifier.height(10.dp))
                        val max = (report.outlets.maxOfOrNull { it.kwh } ?: 0.0).coerceAtLeast(0.0001)
                        val sum = report.outlets.sumOf { it.kwh }.coerceAtLeast(0.0001)
                        report.outlets.forEachIndexed { i, o ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                val live = strips.firstOrNull { it.mac == mac }?.outlets?.firstOrNull { it.n == o.relayIndex + 1 }
                                val glyph = live?.icon?.takeIf { it.isNotBlank() && it != "plug" }
                                if (glyph == null) SocketFace(true, 38.dp) else Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Text(iconGlyph(glyph), fontSize = 26.sp) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(live?.name?.ifBlank { null } ?: o.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        Modifier.fillMaxWidth((o.kwh / max).toFloat().coerceIn(0.015f, 1f)).height(6.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${fmtKwh(o.kwh)} kWh", fontFamily = Mono, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("${fmtNum(o.kwh / sum * 100, 0)}%", fontFamily = Mono, fontSize = 12.sp, color = dim)
                                }
                            }
                        }
                        if (report.outlets.isEmpty()) Text("No data yet", color = dim)
                    }
                }
                item(key = "reset") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                        Text(
                            "↻  Reset consumption",
                            Modifier.tap { resetting = true }.padding(12.dp),
                            fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }        }
        }
    }

    if (tariff && report != null) TariffDialog(vm, report.tariff, report.currency) { tariff = false }
    if (resetting && mac != null) ConfirmDialog("Reset consumption?", "Clears the recorded energy history for this strip.", "Reset", { vm.resetEnergy(mac) }, { resetting = false })
}

@Composable
private fun TotalCard(report: EnergyReport, onTariff: () -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    VCard(padding = 18.dp) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Total used", fontSize = 15.sp, color = dim)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtKwh(report.totalKwh), fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(" kWh", Modifier.padding(bottom = 7.dp), fontFamily = Mono, fontSize = 14.sp, color = dim)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Estimated cost", fontSize = 15.sp, color = dim)
                val priced = report.cost > 0
                Text(
                    if (priced) fmtNum(report.cost, 2) else "—",
                    fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = if (priced) MaterialTheme.colorScheme.onSurface else dim,
                )
                if (priced) Text(report.currency, fontFamily = Mono, fontSize = 12.sp, color = dim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (report.previousKwh > 0) {
                val pct = (report.totalKwh - report.previousKwh) / report.previousKwh * 100
                Pill("${if (pct >= 0) "▲" else "▼"} ${fmtNum(abs(pct), 0)}% vs previous", if (pct > 0) Tone.BAD else Tone.GOOD, outlined = true)
            }
            Text(
                if (report.tariff.mode == "egypt") "Egypt tariff ✎" else "Set tariff ✎",
                Modifier.tap(onClick = onTariff).padding(vertical = 6.dp),
                fontSize = 14.sp, color = dim,
            )
        }
    }
}
@Composable
private fun BarChart(values: List<Double>, selected: Int, onSelect: (Int) -> Unit) {
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(500)) }
    val bar = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outline
    Canvas(
        Modifier.fillMaxWidth().height(170.dp).pointerInput(values.size) {
            detectTapGestures { o -> if (values.isNotEmpty()) onSelect((o.x / size.width * values.size).toInt().coerceIn(0, values.lastIndex)) }
        },
    ) {
        if (values.isEmpty()) return@Canvas
        val max = (values.max()).coerceAtLeast(0.001)
        val gap = if (values.size > 40) 1.dp.toPx() else 6.dp.toPx()
        val bw = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val y = size.height - (values.average() / max).toFloat() * size.height
        values.forEachIndexed { i, v ->
            val h = (v / max).toFloat() * size.height * progress.value
            val color = if (i == selected) bar else bar.copy(alpha = 0.5f)
            drawRoundRect(color, Offset(i * (bw + gap), size.height - h), Size(bw, h.coerceAtLeast(2.dp.toPx())), CornerRadius(min(bw / 2, 6.dp.toPx())))
        }
        drawLine(line.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
    }
}
@Composable
private fun TariffDialog(vm: StripsViewModel, info: TariffInfo, currency: String, onDismiss: () -> Unit) {
    val current = vm.settings
    var egypt by remember { mutableStateOf(info.mode == "egypt") }
    var rate by remember { mutableStateOf(current?.ratePerKwh?.toString() ?: "") }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val end = TextAlign.End
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Electricity tariff") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SegTabs(listOf("Egypt residential", "Flat rate"), if (egypt) 0 else 1, { egypt = it == 0 })
                if (egypt) {
                    Row {
                        Text("kWh / month", Modifier.weight(1.3f), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text("EGP/kWh", Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, textAlign = end)
                        Text("Service", Modifier.weight(0.8f), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, textAlign = end)
                    }
                    info.levels.forEachIndexed { i, l ->
                        val on = i == info.currentLevel
                        val bg = if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        Row(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(8.dp)).padding(vertical = 4.dp, horizontal = 4.dp)) {
                            val w = if (on) FontWeight.Bold else FontWeight.Normal
                            Text(l.label, Modifier.weight(1.3f), fontFamily = Mono, fontSize = 13.sp, fontWeight = w)
                            Text(fmtNum(l.rate, 2), Modifier.weight(1f), fontFamily = Mono, fontSize = 13.sp, fontWeight = w, textAlign = end)
                            Text(fmtNum(l.service, 0), Modifier.weight(0.8f), fontFamily = Mono, fontSize = 13.sp, fontWeight = w, textAlign = end)
                        }
                    }
                    Text(info.note, fontSize = 11.sp, color = dim)
                    Text("Used this month: ${fmtKwh(info.monthToDateKwh)} kWh", fontSize = 11.sp, color = dim)
                } else {
                    VTextField(rate, { rate = it }, "Price per kWh", numeric = true, suffix = current?.currency ?: currency)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = current != null && (egypt || rate.toDoubleOrNull() != null), onClick = {
                current?.let { vm.saveSettings(if (egypt) it.copy(tariffMode = "egypt") else it.copy(tariffMode = "flat", ratePerKwh = rate.toDouble())) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}