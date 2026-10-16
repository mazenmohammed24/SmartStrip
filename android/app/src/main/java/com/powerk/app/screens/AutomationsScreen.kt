package com.powerk.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import com.powerk.app.components.tap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import com.powerk.app.ScheduleItem
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel
import com.powerk.app.outlet
import com.powerk.app.components.ConfirmDialog
import com.powerk.app.components.EmptyState
import com.powerk.app.components.Mono
import com.powerk.app.components.Pill
import com.powerk.app.components.ScreenHeader
import com.powerk.app.components.SectionLabel
import com.powerk.app.components.SegTabs
import com.powerk.app.components.Shimmer
import com.powerk.app.components.Tone
import com.powerk.app.components.VCard
import com.powerk.app.components.VTextField

private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun AutomationsScreen(vm: StripsViewModel) {
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
    var deleting by remember { mutableStateOf<ScheduleItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { ScreenHeader("Schedules", eyebrow = "Automations") }
            when {
                !vm.loaded -> items(3, key = { "sk$it" }) { Shimmer(Modifier.fillMaxWidth().height(84.dp)) }
                vm.strips.isEmpty() -> item(key = "nostrip") { EmptyState("No strips", "Add a strip before creating schedules.") }
                vm.schedules.isEmpty() -> item(key = "empty") { EmptyState("No schedules", "Switch outlets on or off automatically at a set time.") }
                else -> items(vm.schedules, key = { it.id }) { s ->
                    ScheduleRow(s, vm.strips, onToggle = { vm.toggleSchedule(s) }, onEdit = { editing = s }, onDelete = { deleting = s })
                }
            }
        }
        if (vm.strips.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { editing = ScheduleItem("", "", vm.strips.first().mac, null, "on", "18:00", (0..6).toList(), true) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New schedule") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    editing?.let { item -> ScheduleSheet(item, vm, onDismiss = { editing = null }) }
    deleting?.let { s ->
        ConfirmDialog("Delete schedule?", s.name.ifBlank { s.time }, "Delete", { vm.deleteSchedule(s.id) }, { deleting = null })
    }
}

@Composable
private fun ScheduleRow(s: ScheduleItem, strips: List<Strip>, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val strip = strips.firstOrNull { it.mac == s.stripId }
    val target = (strip?.name ?: "Unknown strip") + " · " + (s.relayIndex?.let { i -> strip?.outlet(i + 1)?.name?.takeIf { it.isNotBlank() } ?: "Outlet ${i + 1}" } ?: "All outlets")
    VCard(Modifier.fillMaxWidth(), onClick = onEdit, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.time, fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Pill(s.action.uppercase(), if (s.action == "on") Tone.GOOD else Tone.NEUTRAL)
                }
                if (s.name.isNotBlank()) Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(target, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    if (s.daysOfWeek.size == 7) "Every day" else s.daysOfWeek.sorted().joinToString(", ") { DAY_NAMES[it] },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(initial: ScheduleItem, vm: StripsViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial.name) }
    var stripId by remember { mutableStateOf(initial.stripId) }
    var relay by remember { mutableStateOf(initial.relayIndex) }
    var action by remember { mutableStateOf(initial.action) }
    var days by remember { mutableStateOf(initial.daysOfWeek.toSet()) }
    var time by remember { mutableStateOf(initial.time) }
    var pickTime by remember { mutableStateOf(false) }
    val strip = vm.strips.firstOrNull { it.mac == stripId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel("Schedules")
                Text(if (initial.id.isBlank()) "New schedule" else "Edit schedule", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            VCard(padding = 18.dp) {
                if (vm.strips.size > 1) {
                    SectionLabel("Strip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.strips.forEach { s -> FilterChip(selected = s.mac == stripId, onClick = { stripId = s.mac; relay = null }, label = { Text(s.name, maxLines = 1) }) }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                SectionLabel("Outlet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = relay == null, onClick = { relay = null }, label = { Text("All") })
                    strip?.outlets?.forEach { o ->
                        FilterChip(selected = relay == o.n - 1, onClick = { relay = o.n - 1 }, label = { Text(o.name.ifBlank { "Outlet ${o.n}" }, maxLines = 1) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Action", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SegTabs(listOf("Turn ON", "Turn OFF"), if (action == "on") 0 else 1, { action = if (it == 0) "on" else "off" })
            }
            VCard(padding = 18.dp) {
                SectionLabel("Days", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_NAMES.forEachIndexed { i, d ->
                        FilterChip(selected = i in days, onClick = { days = if (i in days) days - i else days + i }, label = { Text(d) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().tap { pickTime = true },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(time, fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            VCard(padding = 18.dp) {
                SectionLabel("Label (optional)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                VTextField(name, { name = it }, "e.g. Turn off the heater overnight")
            }
            Button(
                enabled = days.isNotEmpty() && strip != null,
                onClick = {
                    vm.saveSchedule(initial.copy(name = name.trim(), stripId = stripId, relayIndex = relay, action = action, time = time, daysOfWeek = days.sorted()), onDone = onDismiss)
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(time.substringBefore(':').toIntOrNull() ?: 18, time.substringAfter(':').toIntOrNull() ?: 0, true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { time = "%02d:%02d".format(state.hour, state.minute); pickTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}
