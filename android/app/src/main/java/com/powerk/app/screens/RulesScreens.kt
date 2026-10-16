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
import com.powerk.app.components.DayChips
import com.powerk.app.components.FormLabel
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
    RuleKind("power-automation", "Power automations", "React to what an outlet is drawing", Icons.Rounded.Bolt, "Add automation"),
    RuleKind("voltage-protection", "Voltage protection", "Switch outlets off when the mains voltage goes out of range", Icons.Rounded.ElectricalServices, "Add protection"),
    RuleKind("current-protection", "Current protection", "Switch outlets off when they draw too many amps", Icons.Rounded.Speed, "Add limit"),
)

internal fun kindOf(type: String) = RULE_KINDS.first { it.type == type }

private val ACTIONS = listOf("notify" to "Notify only", "off" to "Switch off", "both" to "Notify and switch off")

internal fun JSONObject.with(key: String, value: Any): JSONObject = JSONObject(toString()).put(key, value)

internal fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { getInt(it) }

internal fun defaultParams(type: String, strip: Strip): JSONObject = when (type) {
    "smart-timer" -> JSONObject().put("slots", "0".repeat(96)).put("days", JSONArray((0..6).toList()))
    "auto-timer" -> JSONObject().put("homeState", "off").put("returnAfterMin", 30).put("repeat", true)
    "power-automation" -> JSONObject().put("direction", "below").put("thresholdW", 5).put("holdMin", 10).put("action", "both")
    "voltage-protection" -> JSONObject()
        .put("lowEnabled", true).put("lowV", 190).put("highEnabled", true).put("highV", 250)
        .put("holdSec", 60).put("action", "both").put("restoreAfterMin", 5)
        .put("outlets", JSONArray(strip.outlets.map { it.n - 1 }))
    else -> JSONObject().put("scope", "strip").put("limitA", 10).put("holdSec", 60).put("action", "both")
}

internal fun duration(min: Int): String {
    val d = min / 1440
    val h = (min % 1440) / 60
    val m = min % 60
    return listOfNotNull(d.takeIf { it > 0 }?.let { "${it}d" }, h.takeIf { it > 0 }?.let { "${it}h" }, m.takeIf { it > 0 }?.let { "${it}m" })
        .joinToString(" ").ifEmpty { "0m" }
}

private fun slotTime(slot: Int) = "%02d:%02d".format((slot / 4) % 24, (slot % 4) * 15)

internal fun outletLabel(strip: Strip?, index: Int?): String =
    if (index == null) "Whole strip" else strip?.outlets?.firstOrNull { it.n - 1 == index }?.name?.ifBlank { null } ?: "Outlet ${index + 1}"

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
            MenuCard(Icons.Rounded.Schedule, "Schedules", "Turn outlets on or off on a timetable") { onOpen("schedules/$enc") }
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

private fun presetSlots(from: Int, to: Int) = buildString { repeat(96) { append(if (it in from until to) '1' else '0') } }

@Composable
internal fun SmartTimerFields(params: JSONObject, onChange: (JSONObject) -> Unit) {
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
    FormLabel("Repeat on", top = 4.dp)
    DayChips(days.toSet()) { i -> onChange(params.with("days", JSONArray(if (i in days) days - i else (days + i).sorted()))) }
}
