package com.powerk.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.RuleItem
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel
import com.powerk.app.components.ConfirmDialog
import com.powerk.app.components.EmptyState
import com.powerk.app.components.IconBox
import com.powerk.app.components.Mono
import com.powerk.app.components.Pill
import com.powerk.app.components.ScreenHeader
import com.powerk.app.components.SectionLabel
import com.powerk.app.components.SegTabs
import com.powerk.app.components.Tone
import com.powerk.app.components.VCard
import com.powerk.app.components.VTextField
import com.powerk.app.components.tap
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

private val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

data class RuleKind(val type: String, val title: String, val blurb: String, val icon: ImageVector, val addLabel: String)

val RULE_KINDS = listOf(
    RuleKind("smart-timer", "Smart timer", "Paint the hours an outlet should be ON", Icons.Rounded.Schedule, "Add smart timer"),
    RuleKind("auto-timer", "Auto-timers", "Return outlets to a home state after a set time", Icons.Rounded.HourglassBottom, "Add auto-timer"),
    RuleKind("power-automation", "Power automations", "React to what an outlet is drawing", Icons.Rounded.Bolt, "Add power automation"),
    RuleKind("voltage-protection", "Voltage protection", "Switch outlets off when the mains voltage goes out of range", Icons.Rounded.ElectricalServices, "Add voltage protection"),
    RuleKind("current-protection", "Current protection", "Switch outlets off when they draw too many amps", Icons.Rounded.Speed, "Add current protection"),
)

private fun kindOf(type: String) = RULE_KINDS.first { it.type == type }

private val ACTIONS = listOf("notify" to "Notify only", "off" to "Switch off", "both" to "Notify and switch off")

private fun JSONObject.with(key: String, value: Any): JSONObject = JSONObject(toString()).put(key, value)

private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { getInt(it) }

private fun defaultParams(type: String, strip: Strip): JSONObject = when (type) {
    "smart-timer" -> JSONObject().put("slots", "0".repeat(96)).put("days", JSONArray((0..6).toList()))
    "auto-timer" -> JSONObject().put("homeState", "off").put("returnAfterMin", 30).put("repeat", true)
    "power-automation" -> JSONObject().put("direction", "below").put("thresholdW", 5).put("holdMin", 5).put("action", "both")
    "voltage-protection" -> JSONObject()
        .put("lowEnabled", true).put("lowV", 190).put("highEnabled", true).put("highV", 250)
        .put("holdSec", 10).put("action", "both").put("restoreAfterMin", 5)
        .put("outlets", JSONArray(strip.outlets.map { it.n - 1 }))
    else -> JSONObject().put("scope", "strip").put("limitA", 10).put("holdSec", 5).put("action", "both")
}

private fun duration(min: Int): String {
    val d = min / 1440
    val h = (min % 1440) / 60
    val m = min % 60
    return listOfNotNull(d.takeIf { it > 0 }?.let { "${it}d" }, h.takeIf { it > 0 }?.let { "${it}h" }, m.takeIf { it > 0 }?.let { "${it}m" })
        .joinToString(" ").ifEmpty { "0m" }
}

private fun slotTime(slot: Int) = "%02d:%02d".format((slot / 4) % 24, (slot % 4) * 15)

private fun outletLabel(strip: Strip?, index: Int?): String =
    if (index == null) "Whole strip" else strip?.outlets?.firstOrNull { it.n - 1 == index }?.name?.ifBlank { null } ?: "Outlet ${index + 1}"

private fun summarize(rule: RuleItem, strip: Strip?): String {
    val p = rule.params
    val target = outletLabel(strip, rule.relayIndex)
    val action = ACTIONS.firstOrNull { it.first == p.optString("action") }?.second.orEmpty()
    return when (rule.type) {
        "smart-timer" -> {
            val slots = p.optString("slots")
            val ranges = mutableListOf<Pair<Int, Int>>()
            var start = -1
            for (i in 0..96) {
                val on = i < 96 && slots.getOrNull(i) == '1'
                if (on && start < 0) start = i
                if (!on && start >= 0) { ranges += start to i; start = -1 }
            }
            val text = if (ranges.isEmpty()) "No hours set" else ranges.take(2).joinToString(", ") { "${slotTime(it.first)}–${slotTime(it.second)}" } + if (ranges.size > 2) " +${ranges.size - 2}" else ""
            "$target · $text"
        }
        "auto-timer" -> "$target · back ${p.optString("homeState").uppercase()} after ${duration(p.optInt("returnAfterMin"))}" + if (p.optBoolean("repeat", true)) "" else " · once"
        "power-automation" -> "$target · ${p.optString("direction")} ${p.optDouble("thresholdW").toInt()} W for ${p.optInt("holdMin")} min · $action"
        "voltage-protection" -> {
            val range = listOfNotNull(
                if (p.optBoolean("lowEnabled")) "< ${p.optInt("lowV")} V" else null,
                if (p.optBoolean("highEnabled")) "> ${p.optInt("highV")} V" else null,
            ).joinToString(" or ").ifEmpty { "no limits" }
            "$range for ${p.optInt("holdSec")}s · $action"
        }
        else -> "$target · over ${p.optDouble("limitA")} A for ${p.optInt("holdSec")}s · $action"
    }
}

@Composable
fun MenuCard(icon: ImageVector, title: String, desc: String, badge: String? = null, onClick: () -> Unit) {
    VCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(size = 44.dp) { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge != null) Pill(badge, Tone.GOOD)
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StripChips(vm: StripsViewModel, selected: String, onSelect: (String) -> Unit) {
    if (vm.strips.size < 2) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.strips.forEach { s -> FilterChip(selected = s.mac == selected, onClick = { onSelect(s.mac) }, label = { Text(s.name, maxLines = 1) }) }
    }
}

@Composable
fun AutomationHubScreen(vm: StripsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var mac by remember { mutableStateOf(vm.strips.firstOrNull()?.mac.orEmpty()) }
    val strip = vm.strips.firstOrNull { it.mac == mac } ?: vm.strips.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 0.dp)) {
        ScreenHeader("Automations", "Rules that run on the server", onBack = onBack, eyebrow = "Rules")
        if (strip == null) {
            EmptyState("No strips yet", "Add a strip to create automations.")
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            StripChips(vm, strip.mac) { mac = it }
            RULE_KINDS.forEach { k ->
                val n = vm.rules.count { it.stripId == strip.mac && it.type == k.type }
                MenuCard(k.icon, k.title, k.blurb, n.takeIf { it > 0 }?.toString()) { onOpen("rule/${k.type}/${android.net.Uri.encode(strip.mac)}") }
            }
        }
    }
}

@Composable
fun StripMenuScreen(vm: StripsViewModel, mac: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val strip = vm.strips.firstOrNull { it.mac == mac }
    var roomDialog by remember { mutableStateOf(false) }
    val enc = android.net.Uri.encode(mac)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader(strip?.name ?: "Strip", "Strip settings & tools", onBack = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            SectionLabel("Automation", Modifier.padding(top = 8.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            MenuCard(Icons.Rounded.Schedule, "Schedules", "Turn outlets on or off on a timetable") { onOpen("automations") }
            RULE_KINDS.forEach { k ->
                val n = vm.rules.count { it.stripId == mac && it.type == k.type }
                MenuCard(k.icon, k.title, k.blurb, n.takeIf { it > 0 }?.toString()) { onOpen("rule/${k.type}/$enc") }
            }
            SectionLabel("Energy", Modifier.padding(top = 10.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            MenuCard(Icons.Rounded.Bolt, "Energy", "Usage history and cost estimates") { onOpen("energy?strip=$enc") }
            SectionLabel("Device", Modifier.padding(top = 10.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            MenuCard(Icons.Rounded.ElectricalServices, "Device settings", "Rename, icon, firmware and remove") { onOpen("strip/$enc") }
            MenuCard(Icons.Rounded.ChevronRight, "Room", strip?.location ?: "Not in a room") { roomDialog = true }
        }
    }
    if (roomDialog && strip != null) RoomDialog(vm, strip) { roomDialog = false }
}

@Composable
fun RoomDialog(vm: StripsViewModel, strip: Strip, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(strip.location.orEmpty()) }
    val rooms = vm.strips.mapNotNull { it.location?.trim()?.ifBlank { null } }.distinct().sorted()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Room for ${strip.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rooms.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rooms.forEach { r -> FilterChip(selected = draft == r, onClick = { draft = r }, label = { Text(r) }) }
                }
                VTextField(draft, { draft = it }, "Room name")
            }
        },
        confirmButton = { TextButton(onClick = { vm.setLocation(strip.mac, draft.trim().ifBlank { null }); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RoomsScreen(vm: StripsViewModel, onBack: () -> Unit) {
    var moving by remember { mutableStateOf<Strip?>(null) }
    val unassigned = "Unassigned"
    val rooms = vm.strips.groupBy { it.location?.trim()?.ifBlank { null } ?: unassigned }
        .toList().sortedWith(compareBy({ it.first == unassigned }, { it.first }))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("Rooms", "Group strips by where they live", onBack = onBack, eyebrow = "Home")
        if (vm.strips.isEmpty()) EmptyState("No rooms yet", "Add a strip, then group it into a room.")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            rooms.forEach { (name, strips) ->
                VCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${strips.size} strip${if (strips.size == 1) "" else "s"} · ${com.powerk.app.components.fmtW(strips.sumOf { it.powerW })}",
                                fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { strips.filter { it.online }.forEach { vm.command(it.mac, 0, true) } }) { Text("All on") }
                        TextButton(onClick = { strips.filter { it.online }.forEach { vm.command(it.mac, 0, false) } }) { Text("All off") }
                    }
                    strips.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().tap { moving = s }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(com.powerk.app.components.iconGlyph(s.icon), fontSize = 20.sp)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    "${s.outlets.count { it.on }}/${s.outlets.size} on · ${com.powerk.app.components.fmtW(s.powerW)}",
                                    fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Pill(if (s.online) "Online" else "Offline", if (s.online) Tone.GOOD else Tone.BAD, outlined = true)
                        }
                    }
                }
            }
        }
    }
    moving?.let { RoomDialog(vm, it) { moving = null } }
}

@Composable
fun RuleTypeScreen(vm: StripsViewModel, mac: String, type: String, onBack: () -> Unit) {
    val strip = vm.strips.firstOrNull { it.mac == mac }
    val kind = kindOf(type)
    val rules = vm.rules.filter { it.stripId == mac && it.type == type }
    var editing by remember { mutableStateOf<RuleItem?>(null) }
    var removing by remember { mutableStateOf<RuleItem?>(null) }
    if (strip == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { ScreenHeader(kind.title, onBack = onBack) }
        return
    }
    val edit = editing
    if (edit != null) {
        BackHandler { editing = null }
        RuleEditor(vm, strip, type, edit, rules, onClose = { editing = null })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader(strip.name, kind.blurb, onBack = onBack, eyebrow = kind.title)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            if (rules.isEmpty()) EmptyState("Nothing here yet", kind.blurb)
            rules.forEach { r ->
                VCard(onClick = { editing = r }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBox(size = 40.dp) { Icon(kind.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(summarize(r, strip), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (r.enabled && r.status != null && r.status != "idle") {
                                Pill(r.status.uppercase(), if (r.status == "tripped") Tone.BAD else Tone.GOOD, Modifier.padding(top = 4.dp))
                            }
                        }
                        Switch(checked = r.enabled, onCheckedChange = { vm.toggleRule(r) })
                        IconButton(onClick = { removing = r }) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            PrimaryButton(kind.addLabel) { editing = RuleItem("", type, mac, null, "", true, JSONObject()) }
        }
    }
    removing?.let { r ->
        ConfirmDialog("Delete rule?", summarize(r, strip), "Delete", { vm.deleteRule(r.id) }, { removing = null })
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(56.dp).tap(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumField(label: String, value: Double, suffix: String, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()) }
    VTextField(text, { text = it; it.toDoubleOrNull()?.let(onValue) }, label, numeric = true, suffix = suffix)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionChips(value: String, onChange: (String) -> Unit) {
    SectionLabel("When it triggers", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ACTIONS.forEach { (id, label) -> FilterChip(selected = value == id, onClick = { onChange(id) }, label = { Text(label) }) }
    }
}

@Composable
private fun OutletChips(strip: Strip, selected: Int?, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        strip.outlets.forEach { o ->
            FilterChip(selected = selected == o.n - 1, onClick = { onSelect(o.n - 1) }, label = { Text(o.name.ifBlank { "Outlet ${o.n}" }, maxLines = 1) })
        }
    }
}

@Composable
private fun RuleEditor(vm: StripsViewModel, strip: Strip, type: String, initial: RuleItem, siblings: List<RuleItem>, onClose: () -> Unit) {
    val kind = kindOf(type)
    val perOutlet = type == "smart-timer" || type == "auto-timer" || type == "power-automation"
    var relay by remember { mutableStateOf(initial.relayIndex ?: if (perOutlet) strip.outlets.firstOrNull()?.let { it.n - 1 } else null) }
    val unique = type == "smart-timer" || type == "auto-timer" || type == "voltage-protection"
    val current = when {
        initial.id.isNotBlank() && !unique -> initial
        type == "voltage-protection" -> siblings.firstOrNull()
        unique -> siblings.firstOrNull { it.relayIndex == relay }
        else -> null
    }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader(strip.name, kind.blurb, onBack = onClose, eyebrow = kind.title)
        key(current?.id, relay.takeIf { unique }) {
            var params by remember { mutableStateOf(JSONObject((current?.params ?: defaultParams(type, strip)).toString())) }
            var enabled by remember { mutableStateOf(current?.enabled ?: true) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 28.dp)) {
                if (perOutlet) {
                    SectionLabel("Outlet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (unique || initial.id.isBlank()) OutletChips(strip, relay) { relay = it }
                    else Text(outletLabel(strip, relay), fontWeight = FontWeight.SemiBold)
                }
                when (type) {
                    "smart-timer" -> SmartTimerFields(params) { params = it }
                    "auto-timer" -> {
                        SectionLabel("Home state", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SegTabs(listOf("ON", "OFF"), if (params.optString("homeState") == "on") 0 else 1, { params = params.with("homeState", if (it == 0) "on" else "off") })
                        SectionLabel("Return after", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 15, 30, 60, 120, 360, 720, 1440).forEach { m ->
                                FilterChip(selected = params.optInt("returnAfterMin") == m, onClick = { params = params.with("returnAfterMin", m) }, label = { Text(duration(m)) })
                            }
                        }
                        NumField("Custom minutes", params.optDouble("returnAfterMin"), "min") { params = params.with("returnAfterMin", it.toInt()) }
                        SwitchRow("Repeat every time", params.optBoolean("repeat", true)) { params = params.with("repeat", it) }
                    }
                    "power-automation" -> {
                        SectionLabel("Trigger when power is", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SegTabs(listOf("Below", "Above"), if (params.optString("direction") == "below") 0 else 1, { params = params.with("direction", if (it == 0) "below" else "above") })
                        NumField("Threshold", params.optDouble("thresholdW"), "W") { params = params.with("thresholdW", it) }
                        NumField("For at least", params.optDouble("holdMin"), "min") { params = params.with("holdMin", it) }
                        ActionChips(params.optString("action")) { params = params.with("action", it) }
                    }
                    "voltage-protection" -> {
                        SwitchRow("Low voltage limit", params.optBoolean("lowEnabled")) { params = params.with("lowEnabled", it) }
                        if (params.optBoolean("lowEnabled")) NumField("Lower limit", params.optDouble("lowV"), "V") { params = params.with("lowV", it) }
                        SwitchRow("High voltage limit", params.optBoolean("highEnabled")) { params = params.with("highEnabled", it) }
                        if (params.optBoolean("highEnabled")) NumField("Upper limit", params.optDouble("highV"), "V") { params = params.with("highV", it) }
                        NumField("Out of range for", params.optDouble("holdSec"), "sec") { params = params.with("holdSec", it.toInt()) }
                        ActionChips(params.optString("action")) { params = params.with("action", it) }
                        SectionLabel("Outlets to switch off", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val picked = params.optJSONArray("outlets").ints()
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            strip.outlets.forEach { o ->
                                val i = o.n - 1
                                FilterChip(
                                    selected = i in picked,
                                    onClick = { params = params.with("outlets", JSONArray(if (i in picked) picked - i else (picked + i).sorted())) },
                                    label = { Text(o.name.ifBlank { "Outlet ${o.n}" }, maxLines = 1) },
                                )
                            }
                        }
                        NumField("Restore after normal for (0 = never)", params.optDouble("restoreAfterMin"), "min") { params = params.with("restoreAfterMin", it.toInt()) }
                    }
                    else -> {
                        SectionLabel("Scope", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SegTabs(listOf("Whole strip", "One outlet"), if (params.optString("scope") == "strip") 0 else 1, {
                            params = params.with("scope", if (it == 0) "strip" else "outlet")
                            if (it == 1 && relay == null) relay = strip.outlets.firstOrNull()?.let { o -> o.n - 1 }
                        })
                        if (params.optString("scope") == "outlet") OutletChips(strip, relay) { relay = it }
                        NumField("Current limit", params.optDouble("limitA"), "A") { params = params.with("limitA", it) }
                        NumField("Over the limit for", params.optDouble("holdSec"), "sec") { params = params.with("holdSec", it.toInt()) }
                        ActionChips(params.optString("action")) { params = params.with("action", it) }
                    }
                }
                SwitchRow("Enabled", enabled) { enabled = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                PrimaryButton("Save") {
                    val target = when (type) {
                        "voltage-protection" -> null
                        "current-protection" -> if (params.optString("scope") == "outlet") relay else null
                        else -> relay
                    }
                    error = when {
                        type == "voltage-protection" && params.optBoolean("lowEnabled") && params.optBoolean("highEnabled") && params.optDouble("lowV") >= params.optDouble("highV") -> "Lower limit must be below the upper limit."
                        type == "voltage-protection" && params.optJSONArray("outlets").ints().isEmpty() -> "Pick at least one outlet."
                        type == "smart-timer" && params.optJSONArray("days").ints().isEmpty() -> "Pick at least one day."
                        type == "auto-timer" && params.optInt("returnAfterMin") < 1 -> "Return time must be at least 1 minute."
                        type == "current-protection" && params.optDouble("limitA") <= 0 -> "Enter a current limit above 0."
                        perOutlet && target == null -> "Choose an outlet."
                        else -> null
                    }
                    if (error == null) {
                        vm.saveRule(RuleItem(current?.id.orEmpty(), type, strip.mac, target, current?.name.orEmpty(), enabled, params), onDone = onClose)
                    }
                }
                if (current != null) {
                    TextButton(onClick = { vm.deleteRule(current.id); onClose() }, Modifier.fillMaxWidth()) {
                        Text("Delete rule", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun presetSlots(from: Int, to: Int) = buildString { repeat(96) { append(if (it in from until to) '1' else '0') } }

@Composable
private fun SmartTimerFields(params: JSONObject, onChange: (JSONObject) -> Unit) {
    val slots = params.optString("slots").padEnd(96, '0')
    val days = params.optJSONArray("days").ints()
    val scheme = MaterialTheme.colorScheme
    val now = LocalTime.now()
    val nowSlot = (now.hour * 60 + now.minute) / 15
    val slotsState = rememberUpdatedState(slots)
    val onChangeState = rememberUpdatedState(onChange)
    val paramsState = rememberUpdatedState(params)
    var paint by remember { mutableStateOf(true) }

    fun slotAt(pos: Offset, size: androidx.compose.ui.unit.IntSize): Int? {
        val dx = pos.x - size.width / 2f
        val dy = pos.y - size.height / 2f
        if (dx * dx + dy * dy < (size.width * 0.28f) * (size.width * 0.28f)) return null
        var deg = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())) + 90.0
        if (deg < 0) deg += 360.0
        return (deg / 3.75).toInt().coerceIn(0, 95)
    }

    fun apply(slot: Int, value: Boolean) {
        val cur = slotsState.value
        if ((cur[slot] == '1') == value) return
        val next = cur.toCharArray().also { it[slot] = if (value) '1' else '0' }.concatToString()
        onChangeState.value(paramsState.value.with("slots", next))
    }

    SectionLabel("Hours it stays on", color = scheme.onSurfaceVariant)
    VCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { pos -> slotAt(pos, size)?.let { apply(it, slotsState.value[it] != '1') } }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos -> slotAt(pos, size)?.let { paint = slotsState.value[it] != '1'; apply(it, paint) } },
                                onDrag = { change, _ -> slotAt(change.position, size)?.let { apply(it, paint) } },
                            )
                        },
                ) {
                    val stroke = size.width * 0.11f
                    val r = size.width / 2f - stroke / 2f - 2.dp.toPx()
                    val tl = Offset(size.width / 2f - r, size.height / 2f - r)
                    for (i in 0 until 96) {
                        drawArc(
                            color = if (slots[i] == '1') scheme.primary else scheme.surfaceContainerHigh,
                            startAngle = -90f + i * 3.75f + 0.4f,
                            sweepAngle = 2.95f,
                            useCenter = false,
                            topLeft = tl,
                            size = Size(r * 2, r * 2),
                            style = Stroke(stroke),
                        )
                    }
                    val a = Math.toRadians((-90.0 + (now.hour * 60 + now.minute) / 4.0))
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawLine(
                        scheme.onSurface,
                        c + Offset((Math.cos(a) * r * 0.55f).toFloat(), (Math.sin(a) * r * 0.55f).toFloat()),
                        c + Offset((Math.cos(a) * (r + stroke / 2f)).toFloat(), (Math.sin(a) * (r + stroke / 2f)).toFloat()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Now ${"%02d:%02d".format(now.hour, now.minute)}", fontFamily = Mono, fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    val on = slots[nowSlot] == '1'
                    Text(if (on) "ON" else "OFF", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (on) scheme.primary else scheme.onSurface)
                    Text(if (on) "Stays on" else "Stays off", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                listOf(0 to Alignment.TopCenter, 6 to Alignment.CenterEnd, 12 to Alignment.BottomCenter, 18 to Alignment.CenterStart).forEach { (h, al) ->
                    Text(h.toString(), Modifier.align(al).padding(34.dp), fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                }
            }
        }
        Text("Tap or drag to paint. Drag from a green block to erase.", fontSize = 12.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Evening 6 PM–12 AM" to (72 to 96), "Night 12–6 AM" to (0 to 24), "Work hours 8 AM–4 PM" to (32 to 64), "All day" to (0 to 96)).forEach { (label, r) ->
            FilterChip(selected = false, onClick = { onChange(params.with("slots", presetSlots(r.first, r.second))) }, label = { Text(label) })
        }
        FilterChip(selected = false, onClick = { onChange(params.with("slots", "0".repeat(96))) }, label = { Text("Clear all") })
    }
    SectionLabel("Repeat on", color = scheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DAYS.forEachIndexed { i, d ->
            FilterChip(
                selected = i in days,
                onClick = { onChange(params.with("days", JSONArray(if (i in days) days - i else (days + i).sorted()))) },
                label = { Text(d) },
            )
        }
    }
}
