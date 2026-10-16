package com.powerk.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.Outlet
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel
import com.powerk.app.components.ConfirmDialog
import com.powerk.app.components.IconBox
import com.powerk.app.components.IconPickerMenu
import com.powerk.app.components.MasterButton
import com.powerk.app.components.Mono
import com.powerk.app.components.Pill
import com.powerk.app.components.ScreenHeader
import com.powerk.app.components.SectionLabel
import com.powerk.app.components.Stat
import com.powerk.app.components.Tone
import com.powerk.app.components.VCard
import com.powerk.app.components.VTextField
import com.powerk.app.components.WifiBars
import com.powerk.app.components.tap
import com.powerk.app.components.fmtKwh
import com.powerk.app.components.fmtNum
import com.powerk.app.components.fmtW
import com.powerk.app.components.iconGlyph

@Composable
fun StripDetailScreen(
    vm: StripsViewModel,
    mac: String,
    onBack: () -> Unit,
    onEnergy: () -> Unit,
    onSchedules: () -> Unit,
) {
    val strip = vm.strips.firstOrNull { it.mac == mac }
    if (strip == null) {
        LaunchedEffect(vm.loaded) { if (vm.loaded) onBack() }
        Box(Modifier.fillMaxSize())
        return
    }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var stripPicker by remember { mutableStateOf(false) }
    val schedules = vm.schedules.count { it.stripId == strip.mac }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                strip.name,
                strip.model.ifBlank { null },
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { stripPicker = true }) { Text(iconGlyph(strip.icon), fontSize = 20.sp) }
                        IconPickerMenu(stripPicker, strip.icon, { stripPicker = false }) { stripPicker = false; vm.updateIcon(strip.mac, null, it) }
                    }
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename") }
                },
            )
        }
        item(key = "power") {
            VCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Power now", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmtW(strip.powerW), fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Pill(if (strip.online) "Online" else "Offline", if (strip.online) Tone.GOOD else Tone.BAD)
                    Spacer(Modifier.width(10.dp))
                    MasterButton(strip.on, strip.online, { vm.command(strip.mac, 0, !strip.on) }, 48.dp)
                }
                Spacer(Modifier.height(14.dp))
                Row { Stat("Voltage", strip.voltage?.let { "${fmtNum(it, 0)} V" } ?: "—"); Stat("Current", strip.currentA?.let { "${fmtNum(it, 2)} A" } ?: "—"); Stat("Energy", "${fmtKwh(strip.energyKwh)} kWh") }
                Spacer(Modifier.height(10.dp))
                Row { Column(Modifier.weight(1f)) { Text("Signal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1); Spacer(Modifier.height(3.dp)); WifiBars(strip.rssi, active = strip.online) }; Stat("Firmware", strip.fw.ifBlank { "—" }); Stat("ID", "…" + strip.mac.takeLast(4)) }
            }
        }
        item(key = "outlets-label") { SectionLabel("Outlets") }
        items(strip.outlets, key = { "o${it.n}" }) { o -> OutletRow(strip, o, vm) }
        item(key = "auto-label") { SectionLabel("Automation & energy") }
        item(key = "energy") { LinkRow(Icons.Rounded.BarChart, "Consumption", "Daily energy and cost", onEnergy) }
        item(key = "schedules") { LinkRow(Icons.Rounded.AccessTime, "Schedules", if (schedules == 0) "None yet" else "$schedules active", onSchedules) }
        item(key = "danger") {
            VCard(onClick = { deleting = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text("Remove strip", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (renaming) RenameDialog(strip.name, { renaming = false }) { vm.renameStrip(strip.mac, it) }
    if (deleting) ConfirmDialog("Remove ${strip.name}?", "The strip and its schedules are deleted from the server.", "Remove", { vm.deleteStrip(strip.mac) { onBack() } }, { deleting = false })
}

@Composable
private fun OutletRow(strip: Strip, o: Outlet, vm: StripsViewModel) {
    var picker by remember { mutableStateOf(false) }
    VCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconBox(size = 40.dp) { Text(iconGlyph(o.icon), fontSize = 20.sp) }
                Box(Modifier.size(40.dp).tap { picker = true })
                IconPickerMenu(picker, o.icon, { picker = false }) { picker = false; vm.updateIcon(strip.mac, o.n, it) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(o.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString { append(if (o.on) fmtW(o.powerW) else "Off"); if (o.tempC > 0) append(" · ${o.tempC}°C"); if (o.energyKwh > 0) append(" · ${fmtKwh(o.energyKwh)} kWh") },
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(checked = o.on, onCheckedChange = { vm.command(strip.mac, o.n, it) }, enabled = strip.online)
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    VCard(onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(size = 40.dp) { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename strip") },
        text = { VTextField(name, { name = it }, "Name") },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
