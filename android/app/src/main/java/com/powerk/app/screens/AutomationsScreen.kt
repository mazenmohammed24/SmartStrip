package com.powerk.app.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.AppSpacing
import com.powerk.app.ScheduleItem
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel
import com.powerk.app.components.CenteredHeader
import com.powerk.app.components.ClockField
import com.powerk.app.components.ConfirmDialog
import com.powerk.app.components.DayChips
import com.powerk.app.components.EmptyState
import com.powerk.app.components.FormLabel
import com.powerk.app.components.GreenFab
import com.powerk.app.components.IconBox
import com.powerk.app.components.Mono
import com.powerk.app.components.Pill
import com.powerk.app.components.Refreshable
import com.powerk.app.components.SaveButton
import com.powerk.app.components.ScreenHeader
import com.powerk.app.components.SegTabs
import com.powerk.app.components.Shimmer
import com.powerk.app.components.Tone
import com.powerk.app.components.VCard
import com.powerk.app.components.VDropdown
import com.powerk.app.components.VTextInput
import com.powerk.app.outlet

private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

private val ZONES = listOf(
    "Africa/Cairo", "Africa/Johannesburg", "Africa/Lagos", "Africa/Nairobi", "Asia/Riyadh", "Asia/Dubai", "Asia/Kuwait", "Asia/Baghdad",
    "Asia/Amman", "Asia/Beirut", "Asia/Jerusalem", "Europe/Istanbul", "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Madrid",
    "Europe/Rome", "Europe/Moscow", "Asia/Karachi", "Asia/Kolkata", "Asia/Dhaka", "Asia/Bangkok", "Asia/Singapore", "Asia/Shanghai",
    "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland", "America/Sao_Paulo", "America/New_York", "America/Chicago", "America/Denver",
    "America/Los_Angeles", "UTC",
)

private fun zoneOptions(current: String): List<Pair<String, String>> =
    (if (current in ZONES) ZONES else listOf(current) + ZONES).map { it to it }

private fun repeatText(s: ScheduleItem): String =
    if (s.repeat == "once") "one-time" else if (s.daysOfWeek.size == 7) "every day" else s.daysOfWeek.sorted().joinToString(", ") { DAY_NAMES[it] }

private fun outletName(strip: Strip?, relay: Int?): String =
    relay?.let { i -> strip?.outlet(i + 1)?.name?.takeIf { it.isNotBlank() } ?: "Outlet ${i + 1}" } ?: "All outlets"

@Composable
fun AutomationsScreen(vm: StripsViewModel) = SchedulesScreen(vm, null, null)

@Composable
fun SchedulesScreen(vm: StripsViewModel, mac: String?, onBack: (() -> Unit)?) {
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
    var deleting by remember { mutableStateOf<ScheduleItem?>(null) }
    val strip = mac?.let { m -> vm.strips.firstOrNull { it.mac == m } }
    val list = if (mac == null) vm.schedules else vm.schedules.filter { it.stripId == mac }
    val tab = onBack == null

    val edit = editing
    if (edit != null) {
        BackHandler { editing = null }
        ScheduleEditor(edit, vm, lockedStrip = strip, onClose = { editing = null })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Refreshable(vm.refreshing, { vm.refresh() }, Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.listGap),
            ) {
                item(key = "header") {
                    if (onBack != null) CenteredHeader("Schedules", strip?.name ?: "Strip", onBack)
                    else ScreenHeader("Schedules", eyebrow = "Automations")
                }
                when {
                    !vm.loaded -> items(3, key = { "sk$it" }) { Shimmer(Modifier.fillMaxWidth().height(84.dp)) }
                    vm.strips.isEmpty() -> item(key = "nostrip") { EmptyState("No strips", "Add a strip before creating schedules.") }
                    list.isEmpty() -> item(key = "empty") { EmptyState("No schedules yet", "Switch outlets on or off automatically at a set time.") }
                    else -> items(list, key = { it.id }) { s ->
                        ScheduleRow(s, vm.strips, showStrip = mac == null, onToggle = { vm.toggleSchedule(s) }, onEdit = { editing = s }, onDelete = { deleting = s })
                    }
                }
            }
        }
        if (vm.strips.isNotEmpty()) {
            GreenFab(
                "Add schedule", Icons.Rounded.Add,
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (tab) 96.dp else 24.dp),
            ) {
                val target = strip ?: vm.strips.first()
                editing = ScheduleItem("", "", target.mac, null, "on", "18:00", (0..6).toList(), true)
            }
        }
    }

    deleting?.let { s ->
        ConfirmDialog("Delete schedule?", s.name.ifBlank { s.time }, "Delete", { vm.deleteSchedule(s.id) }, { deleting = null })
    }
}

@Composable
private fun ScheduleRow(s: ScheduleItem, strips: List<Strip>, showStrip: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val strip = strips.firstOrNull { it.mac == s.stripId }
    val title = (if (showStrip) (strip?.name ?: "Unknown strip") + " · " else "") + outletName(strip, s.relayIndex)
    val sub = listOfNotNull(s.name.takeIf { it.isNotBlank() }, repeatText(s), s.timezone).joinToString(" · ")
    VCard(Modifier.alpha(if (s.enabled) 1f else 0.55f), onClick = onEdit, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(size = 42.dp, container = if (s.action == "on") scheme.primaryContainer else scheme.surfaceContainerHigh) {
                Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(20.dp), tint = if (s.action == "on") scheme.primary else scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.time, fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    Pill(s.action.uppercase(), if (s.action == "on") Tone.GOOD else Tone.NEUTRAL)
                }
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            Switch(checked = s.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Edit, "Edit", Modifier.size(20.dp), tint = scheme.onSurfaceVariant) }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.DeleteOutline, "Delete", Modifier.size(20.dp), tint = scheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditor(initial: ScheduleItem, vm: StripsViewModel, lockedStrip: Strip?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var stripId by remember { mutableStateOf(initial.stripId) }
    var relay by remember { mutableStateOf(initial.relayIndex) }
    var action by remember { mutableStateOf(initial.action) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var days by remember { mutableStateOf(initial.daysOfWeek.toSet()) }
    var time by remember { mutableStateOf(initial.time) }
    var zone by remember { mutableStateOf(initial.timezone) }
    var pickTime by remember { mutableStateOf(false) }
    val strip = vm.strips.firstOrNull { it.mac == stripId }
    val isNew = initial.id.isBlank()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CenteredHeader(if (isNew) "New schedule" else "Edit schedule", (lockedStrip ?: strip)?.name ?: "Strip", onClose)
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VCard(padding = 16.dp) {
                if (lockedStrip == null && vm.strips.size > 1) {
                    FormLabel("Strip", top = 0.dp)
                    VDropdown(stripId, vm.strips.map { it.mac to it.name }, { stripId = it; relay = null })
                }
                FormLabel("Outlet", top = if (lockedStrip == null && vm.strips.size > 1) 14.dp else 0.dp)
                VDropdown(
                    relay,
                    listOf<Pair<Int?, String>>(null to "All outlets") + (strip?.outlets?.map { (it.n - 1) as Int? to it.name.ifBlank { "Outlet ${it.n}" } } ?: emptyList()),
                    { relay = it },
                )
                FormLabel("Action")
                SegTabs(listOf("Turn ON", "Turn OFF"), if (action == "on") 0 else 1, { action = if (it == 0) "on" else "off" })
            }
            VCard(padding = 16.dp) {
                FormLabel("Repeat", top = 0.dp)
                SegTabs(listOf("Weekly", "One-time"), if (repeat == "once") 1 else 0, { repeat = if (it == 1) "once" else "weekly" })
                if (repeat == "weekly") {
                    FormLabel("Days")
                    DayChips(days) { d -> days = if (d in days) days - d else days + d }
                }
                FormLabel("Time")
                ClockField(time, { pickTime = true }, Icons.Rounded.Schedule)
            }
            VCard(padding = 16.dp) {
                FormLabel("Timezone", top = 0.dp)
                VDropdown(zone, zoneOptions(zone), { zone = it })
                FormLabel("Label (optional)")
                VTextInput(name, { name = it }, "e.g. Turn off the heater overnight")
            }
            SaveButton(enabled = (repeat == "once" || days.isNotEmpty()) && strip != null) {
                val saved = initial.copy(
                    name = name.trim(), stripId = stripId, relayIndex = relay, action = action, time = time,
                    daysOfWeek = if (repeat == "once" && days.isEmpty()) (0..6).toList() else days.sorted(),
                    timezone = zone, repeat = repeat,
                )
                vm.saveSchedule(saved, onDone = onClose)
            }
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
