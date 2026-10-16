package com.powerk.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.EventItem
import com.powerk.app.Outlet
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel
import com.powerk.app.outlet
import com.powerk.app.timerKey

private val DURATIONS = listOf(15, 30, 45, 60, 120, 180, 360, 720)

private fun durationLabel(min: Int) = if (min < 60) "$min min" else "${min / 60} h"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) { content() }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        border = BorderStroke(1.dp, if (selected) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant),
        modifier = modifier.tap(onClick = onClick),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.StatBox(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.weight(1f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun OnForText(outlet: Outlet, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val text = if (outlet.on && outlet.changedAtMs > 0) fmtDuration(Ticker.now - outlet.changedAtMs) else if (outlet.on) "On" else "Off"
    Text(text, modifier, fontSize = 11.sp, color = color, maxLines = 1)
}

@Composable
fun TimerCountdown(restoreAtMs: Long): String = fmtDuration((restoreAtMs - Ticker.now).coerceAtLeast(0))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutletSheet(mac: String, n: Int, vm: StripsViewModel, onDismiss: () -> Unit) {
    val strip = vm.strips.firstOrNull { it.mac == mac }
    val outlet = strip?.outlet(n)
    if (strip == null || outlet == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    var tab by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme

    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(size = 42.dp, container = if (outlet.on) scheme.primaryContainer else scheme.surfaceContainerHigh) {
                Text(iconGlyph(outlet.icon), fontSize = 20.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(outlet.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Privacy.name(strip), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("  ·  ", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    OnForText(outlet, color = if (outlet.on) scheme.primary else scheme.onSurfaceVariant)
                }
            }
            Switch(checked = outlet.on, enabled = strip.online, onCheckedChange = { vm.command(mac, n, it) })
        }
        Spacer(Modifier.size(10.dp))
        SegTabs(listOf("Details", "Icon", "Timer", "History"), tab, { tab = it })
        Spacer(Modifier.size(10.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
            when (tab) {
                0 -> DetailsTab(strip, outlet, vm)
                1 -> IconGrid(outlet.icon, { vm.updateIcon(mac, n, it) }, maxHeight = 340.dp)
                2 -> TimerTab(strip, outlet, vm)
                else -> HistoryTab(mac, n, vm)
            }
        }
    }
}

@Composable
private fun DetailsTab(strip: Strip, outlet: Outlet, vm: StripsViewModel) {
    var name by remember(outlet.n, strip.mac) { mutableStateOf(outlet.name) }
    val dismiss = rememberDismissKeyboard()
    val amps = strip.voltage?.takeIf { it > 0 && outlet.on }?.let { outlet.powerW / it } ?: 0.0
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VTextField(name, { name = it }, "Name", Modifier.weight(1f))
            Button(
                enabled = name.isNotBlank() && name.trim() != outlet.name,
                onClick = { dismiss(); vm.renameOutlet(strip.mac, outlet.n, name.trim()) },
            ) { Text("Save") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Power", if (outlet.on) fmtW(outlet.powerW) else "—")
            StatBox("Current", if (outlet.on) "${fmtNum(amps, 2)} A" else "—")
            StatBox("Temp", "${outlet.tempC}°C")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Energy", "${fmtKwh(outlet.energyKwh)} kWh")
            StatBox("State", if (outlet.on) "On" else "Off")
            val dur = if (outlet.changedAtMs > 0) fmtDuration(Ticker.now - outlet.changedAtMs) else "—"
            StatBox(if (outlet.on) "On for" else "Off for", dur)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerTab(strip: Strip, outlet: Outlet, vm: StripsViewModel) {
    val timer = vm.relayTimers[timerKey(strip.mac, outlet.n)]
    var mode by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme
    val word = if (mode == 0) "for" else "after"
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (timer != null) {
            Surface(shape = RoundedCornerShape(14.dp), color = scheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (timer.mode == "after") "Turns ${timer.state} in" else "Back ${if (timer.state == "on") "off" else "on"} in",
                            fontSize = 11.sp,
                            color = scheme.onPrimaryContainer,
                        )
                        Text(TimerCountdown(timer.restoreAtMs), fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    }
                    OutlinedButton(onClick = { vm.cancelTimer(strip.mac, outlet.n) }) { Text("Cancel") }
                }
            }
        }
        SegTabs(listOf("For a while", "After a delay"), mode, { mode = it })
        Text(
            if (mode == 0) "Switch now, then restore automatically." else "Wait, then switch.",
            fontSize = 11.sp,
            color = scheme.onSurfaceVariant,
        )
        for (state in listOf("on", "off")) {
            SectionLabel("Turn $state $word", color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DURATIONS.forEach { m ->
                    Chip(durationLabel(m)) { vm.startTimer(strip.mac, outlet.n, state == "on", m, if (mode == 0) "for" else "after") }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(mac: String, n: Int, vm: StripsViewModel) {
    val items by produceState<List<EventItem>?>(null, mac, n) { value = vm.outletHistory(mac, n) }
    val list = items
    when {
        list == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(4) { Shimmer(Modifier.fillMaxWidth().height(40.dp)) } }
        list.isEmpty() -> Text("No events yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e.message, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(ago(e.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun StripSheet(mac: String, vm: StripsViewModel, onDismiss: () -> Unit, onOpenDetails: () -> Unit) {
    val strip = vm.strips.firstOrNull { it.mac == mac }
    if (strip == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    var name by remember(mac) { mutableStateOf(strip.name) }
    var room by remember(mac) { mutableStateOf(strip.location.orEmpty()) }
    var tab by remember { mutableIntStateOf(0) }
    val dismiss = rememberDismissKeyboard()
    val rooms = vm.strips.mapNotNull { it.location?.trim()?.ifBlank { null } }.distinct().sorted()

    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(size = 42.dp) { Text(iconGlyph(strip.icon), fontSize = 20.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Privacy.name(strip), Modifier.weight(1f, fill = false), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    NameEye(strip, 16.dp)
                }
                Text("${strip.model} · ${Privacy.id(strip, strip.mac.takeLast(6))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            WifiBars(strip.rssi, active = strip.online)
        }
        Spacer(Modifier.size(10.dp))
        SegTabs(listOf("Details", "Icon"), tab, { tab = it })
        Spacer(Modifier.size(10.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
            if (tab == 0) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VTextField(name, { name = it }, "Strip name")
                    VTextField(room, { room = it }, "Room")
                    if (rooms.isNotEmpty()) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rooms.forEach { r -> Chip(r, r.equals(room.trim(), true)) { room = r } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = name.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                dismiss()
                                if (name.trim() != strip.name) vm.renameStrip(mac, name.trim())
                                if (room.trim() != strip.location.orEmpty()) vm.setLocation(mac, room.trim().ifBlank { null })
                                onDismiss()
                            },
                        ) { Text("Save") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onDismiss(); onOpenDetails() }) { Text("Full settings") }
                    }
                }
            } else {
                IconGrid(strip.icon, { vm.updateIcon(mac, null, it) }, maxHeight = 340.dp)
            }
        }
    }
}
