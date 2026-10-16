package com.powerk.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.RuleItem
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel
import com.powerk.app.components.CenteredHeader
import com.powerk.app.components.ChoiceChip
import com.powerk.app.components.ConfirmDialog
import com.powerk.app.components.FormLabel
import com.powerk.app.components.GreenFab
import com.powerk.app.components.IconBox
import com.powerk.app.components.InfoCard
import com.powerk.app.components.Mono
import com.powerk.app.components.OptionRow
import com.powerk.app.components.Pill
import com.powerk.app.components.SaveButton
import com.powerk.app.components.SegTabs
import com.powerk.app.components.Tone
import com.powerk.app.components.ToggleCard
import com.powerk.app.components.VCard
import com.powerk.app.components.VDropdown
import com.powerk.app.components.VNumberField
import com.powerk.app.components.VTextInput
import com.powerk.app.components.fmtNum
import org.json.JSONArray
import org.json.JSONObject

private class ActionOpt(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private fun actionOptions(type: String): List<ActionOpt> = when (type) {
    "power-automation" -> listOf(
        ActionOpt("notify", "Notify me", Icons.Rounded.Notifications),
        ActionOpt("off", "Switch the outlet off", Icons.Rounded.PowerSettingsNew),
        ActionOpt("both", "Notify me and switch it off", Icons.Rounded.NotificationsActive),
    )
    "voltage-protection" -> listOf(
        ActionOpt("both", "Notify me and switch outlets off", Icons.Rounded.NotificationsActive),
        ActionOpt("off", "Switch outlets off", Icons.Rounded.PowerSettingsNew),
        ActionOpt("notify", "Notify me only", Icons.Rounded.Notifications),
    )
    else -> listOf(
        ActionOpt("both", "Notify me and switch off", Icons.Rounded.NotificationsActive),
        ActionOpt("off", "Switch off", Icons.Rounded.PowerSettingsNew),
        ActionOpt("notify", "Notify me only", Icons.Rounded.Notifications),
    )
}

private fun minLabel(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> duration(min)
}

private fun secLabel(sec: Int): String = when {
    sec < 60 -> "$sec sec"
    sec % 60 == 0 -> minLabel(sec / 60)
    else -> "$sec sec"
}

private fun options(base: List<Int>, current: Int, label: (Int) -> String): List<Pair<Int, String>> =
    (base + current).distinct().sorted().map { it to label(it) }

private val HOLD_SEC = listOf(10, 30, 60, 120, 300, 600, 900, 1800)
private val HOLD_MIN = listOf(1, 2, 5, 10, 15, 30, 60, 120)
private val RESTORE_MIN = listOf(0, 1, 2, 5, 10, 15, 30, 60)

private fun actionText(type: String, id: String) = actionOptions(type).firstOrNull { it.id == id }?.label.orEmpty()

internal fun ruleTitle(r: RuleItem, strip: Strip): String = r.name.ifBlank {
    when (r.type) {
        "voltage-protection" -> "Voltage protection"
        "current-protection" -> if (r.relayIndex == null) "Whole strip" else outletLabel(strip, r.relayIndex)
        else -> outletLabel(strip, r.relayIndex)
    }
}

private fun slotLabel(slot: Int) = "%02d:%02d".format((slot / 4) % 24, (slot % 4) * 15)

internal fun describe(rule: RuleItem): String {
    val p = rule.params
    val action = actionText(rule.type, p.optString("action"))
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
            if (ranges.isEmpty()) "No hours set"
            else ranges.take(2).joinToString(", ") { "${slotLabel(it.first)}–${slotLabel(it.second)}" } + if (ranges.size > 2) " +${ranges.size - 2}" else ""
        }
        "auto-timer" -> "Back ${p.optString("homeState").uppercase()} after ${duration(p.optInt("returnAfterMin"))}" + if (p.optBoolean("repeat", true)) "" else " · once"
        "power-automation" ->
            "${if (p.optString("direction") == "below") "Drops below" else "Rises above"} ${p.optDouble("thresholdW").toInt()} W · ${minLabel(p.optInt("holdMin"))} → $action"
        "voltage-protection" -> {
            val range = listOfNotNull(
                if (p.optBoolean("lowEnabled")) "below ${p.optInt("lowV")} V" else null,
                if (p.optBoolean("highEnabled")) "above ${p.optInt("highV")} V" else null,
            ).joinToString(" or ").ifEmpty { "No limits set" }
            "$range · ${secLabel(p.optInt("holdSec"))} → $action"
        }
        else -> "Over ${fmtNum(p.optDouble("limitA"), 1)} A · ${secLabel(p.optInt("holdSec"))} → $action"
    }
}

@Composable
private fun StatusPill(r: RuleItem, modifier: Modifier = Modifier) {
    if (!r.enabled) return
    val watching = r.type == "power-automation" || r.type == "voltage-protection" || r.type == "current-protection"
    when (r.status) {
        "tripped" -> Pill(if (r.type == "power-automation") "Triggered" else "Tripped", Tone.BAD, modifier)
        "waiting" -> Pill(if (r.type == "auto-timer") "Counting down" else "Timing", Tone.WARN, modifier)
        "active" -> Pill("On now", Tone.GOOD, modifier)
        else -> if (watching) Pill("Watching", Tone.GOOD, modifier)
    }
}

@Composable
private fun RuleRow(r: RuleItem, strip: Strip, kind: RuleKind, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val icon = when {
        r.type == "power-automation" && r.params.optString("direction") == "above" -> Icons.AutoMirrored.Rounded.TrendingUp
        r.type == "power-automation" -> Icons.AutoMirrored.Rounded.TrendingDown
        else -> kind.icon
    }
    VCard(Modifier.alpha(if (r.enabled) 1f else 0.55f), onClick = onEdit, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(size = 42.dp) { Icon(icon, null, Modifier.size(20.dp), tint = scheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ruleTitle(r, strip), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(describe(r), fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, maxLines = 2)
                StatusPill(r, Modifier.padding(top = 4.dp))
            }
            Switch(checked = r.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit, Modifier.size(36.dp)) { Icon(Icons.Rounded.Edit, "Edit", Modifier.size(19.dp), tint = scheme.onSurfaceVariant) }
            IconButton(onClick = onDelete, Modifier.size(36.dp)) { Icon(Icons.Rounded.DeleteOutline, "Delete", Modifier.size(19.dp), tint = scheme.error) }
        }
    }
}

@Composable
private fun LiveCard(strip: Strip, type: String) {
    val scheme = MaterialTheme.colorScheme
    VCard(padding = 14.dp) {
        if (type == "voltage-protection") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strip.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Live mains voltage", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Text(strip.voltage?.let { fmtNum(it, 1) + " V" } ?: "— V", fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
            }
        } else {
            val volts = strip.voltage?.takeIf { it > 50 } ?: 220.0
            val total = strip.currentA ?: (strip.outlets.sumOf { it.powerW } / volts)
            Text("Live current", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            (listOf("Total" to total) + strip.outlets.map { it.name.ifBlank { "Outlet ${it.n}" } to it.powerW / volts }).forEachIndexed { i, (label, amps) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    Text(fmtNum(amps, 2) + " A", fontFamily = Mono, fontSize = 14.sp, color = scheme.primary)
                }
            }
        }
    }
}

private fun emptyText(type: String) = when (type) {
    "power-automation" -> "No power automations yet. React to what an outlet is drawing, for example switch it off once it goes idle."
    "voltage-protection" -> "No voltage protection yet. Switch a fridge or air conditioner off during a brownout or over-voltage, and back on when it passes."
    else -> "No current limits yet. Set one below what your socket or extension cord is rated for, or switch off the outlets when the total gets too high."
}

@Composable
fun RuleTypeScreen(vm: StripsViewModel, mac: String, type: String, onBack: () -> Unit) {
    val strip = vm.strips.firstOrNull { it.mac == mac }
    val kind = kindOf(type)
    val rules = vm.rules.filter { it.stripId == mac && it.type == type }
    var editing by remember { mutableStateOf<RuleItem?>(null) }
    var removing by remember { mutableStateOf<RuleItem?>(null) }
    if (strip == null) {
        Column(Modifier.fillMaxSize()) { CenteredHeader(kind.title, "Strip", onBack) }
        return
    }
    val edit = editing
    if (edit != null) {
        BackHandler { editing = null }
        RuleEditor(vm, strip, type, edit, onClose = { editing = null })
        return
    }
    val perOutlet = type == "smart-timer" || type == "auto-timer"

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CenteredHeader(kind.title, strip.name, onBack)
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (perOutlet) {
                    InfoCard(
                        if (type == "auto-timer") "Give an outlet a home state. Whenever it changes to the opposite, it returns to the home state automatically after the time you set."
                        else "Paint the hours an outlet should be ON. The server switches it on and off for you, every selected day.",
                    )
                    strip.outlets.forEach { o ->
                        val rule = rules.firstOrNull { it.relayIndex == o.n - 1 }
                        VCard(onClick = { editing = rule ?: RuleItem("", type, mac, o.n - 1, "", true, JSONObject()) }, padding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBox(size = 42.dp) { Icon(kind.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(o.name.ifBlank { "Outlet ${o.n}" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        rule?.let { describe(it) } ?: if (type == "auto-timer") "No auto-timer" else "No smart timer",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    )
                                    if (rule != null) StatusPill(rule, Modifier.padding(top = 4.dp))
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    if (type != "power-automation") LiveCard(strip, type)
                    if (rules.isEmpty()) Text(emptyText(type), Modifier.padding(vertical = 12.dp, horizontal = 4.dp), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    rules.forEach { r ->
                        RuleRow(r, strip, kind, onEdit = { editing = r }, onToggle = { vm.toggleRule(r) }, onDelete = { removing = r })
                    }
                }
            }
        }
        if (!perOutlet) {
            GreenFab(kind.addLabel, Icons.Rounded.Add, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)) {
                editing = if (type == "voltage-protection" && rules.isNotEmpty()) rules.first() else RuleItem("", type, mac, null, "", true, JSONObject())
            }
        }
    }
    removing?.let { r ->
        ConfirmDialog("Delete rule?", ruleTitle(r, strip) + "\n" + describe(r), "Delete", { vm.deleteRule(r.id) }, { removing = null })
    }
}

@Composable
private fun NumInput(value: Double, suffix: String, enabled: Boolean = true, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()) }
    VNumberField(text, { text = it; it.toDoubleOrNull()?.let(onValue) }, suffix, enabled = enabled)
}

@Composable
private fun ThenOptions(type: String, value: String, onChange: (String) -> Unit) {
    FormLabel("Then")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actionOptions(type).forEach { OptionRow(it.icon, it.label, value == it.id) { onChange(it.id) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(vm: StripsViewModel, strip: Strip, type: String, initial: RuleItem, onClose: () -> Unit) {
    val kind = kindOf(type)
    val current = initial.takeIf { it.id.isNotBlank() }
    var relay by remember { mutableStateOf(initial.relayIndex ?: if (type == "power-automation") strip.outlets.firstOrNull()?.let { it.n - 1 } else null) }
    var error by remember { mutableStateOf<String?>(null) }
    val outletOptions = strip.outlets.map { (it.n - 1) to it.name.ifBlank { "Outlet ${it.n}" } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CenteredHeader(kind.title, strip.name, onClose)
        key(initial.id, initial.relayIndex) {
            var params by remember { mutableStateOf(JSONObject((current?.params ?: defaultParams(type, strip)).toString())) }
            var enabled by remember { mutableStateOf(current?.enabled ?: true) }
            var label by remember { mutableStateOf(current?.name.orEmpty()) }
            val scheme = MaterialTheme.colorScheme
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (type) {
                    "smart-timer" -> {
                        Text(outletLabel(strip, relay), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SmartTimerFields(params) { params = it }
                    }
                    "auto-timer" -> {
                        InfoCard("Pick the state this outlet should return to, and how long after it changes before it snaps back.")
                        VCard(padding = 16.dp) {
                            Text(outletLabel(strip, relay), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            val home = params.optString("homeState")
                            FormLabel("Home state")
                            SegTabs(listOf("Home: ON", "Home: OFF"), if (home == "on") 0 else 1, { params = params.with("homeState", if (it == 0) "on" else "off") })
                            Text(
                                "If this outlet is turned ${if (home == "on") "OFF" else "ON"}, it comes back ${home.uppercase()} after the delay.",
                                Modifier.padding(top = 8.dp), fontSize = 12.sp, color = scheme.onSurfaceVariant,
                            )
                            FormLabel("Return after")
                            val total = params.optInt("returnAfterMin")
                            val d = total / 1440
                            val h = (total % 1440) / 60
                            val m = total % 60
                            fun setTime(nd: Int, nh: Int, nm: Int) { params = params.with("returnAfterMin", nd * 1440 + nh * 60 + nm) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VDropdown(d, (0..30).map { it to "$it d" }, { setTime(it, h, m) }, Modifier.weight(1f), mono = true)
                                VDropdown(h, (0..23).map { it to "$it h" }, { setTime(d, it, m) }, Modifier.weight(1f), mono = true)
                                VDropdown(m, (0..59).map { it to "$it m" }, { setTime(d, h, it) }, Modifier.weight(1f), mono = true)
                            }
                        }
                        ToggleCard("Repeat every time", "Return it home every time it changes, not just once.", params.optBoolean("repeat", true)) { params = params.with("repeat", it) }
                    }
                    "power-automation" -> {
                        VCard(padding = 16.dp) {
                            FormLabel("Outlet", top = 0.dp)
                            VDropdown(relay ?: 0, outletOptions, { relay = it })
                            FormLabel("When power")
                            SegTabs(listOf("Drops below", "Rises above"), if (params.optString("direction") == "below") 0 else 1, { params = params.with("direction", if (it == 0) "below" else "above") })
                            FormLabel("Threshold (W)")
                            NumInput(params.optDouble("thresholdW"), "W") { params = params.with("thresholdW", it) }
                            FormLabel("Held for")
                            VDropdown(params.optInt("holdMin"), options(HOLD_MIN, params.optInt("holdMin"), ::minLabel), { params = params.with("holdMin", it) }, mono = true)
                            ThenOptions(type, params.optString("action")) { params = params.with("action", it) }
                            FormLabel("Label (optional)")
                            VTextInput(label, { label = it }, "e.g. Charger finished")
                        }
                        InfoCard("Fires once the outlet is on and has stayed ${if (params.optString("direction") == "below") "under" else "over"} the threshold for the time you set. It re-arms when the reading moves back across the threshold, so it does not repeat on every check.")
                    }
                    "voltage-protection" -> {
                        VCard(padding = 16.dp) {
                            FormLabel("Safe range", top = 0.dp)
                            listOf("lowEnabled" to "lowV", "highEnabled" to "highV").forEach { (enKey, vKey) ->
                                val on = params.optBoolean(enKey)
                                Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = on, onCheckedChange = { params = params.with(enKey, it) })
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(if (enKey == "lowEnabled") "Lower limit (V)" else "Upper limit (V)", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                        Spacer(Modifier.size(4.dp))
                                        NumInput(params.optDouble(vKey), "V", enabled = on) { params = params.with(vKey, it) }
                                    }
                                }
                            }
                            FormLabel("Outside the range for")
                            VDropdown(params.optInt("holdSec"), options(HOLD_SEC, params.optInt("holdSec"), ::secLabel), { params = params.with("holdSec", it) }, mono = true)
                            ThenOptions(type, params.optString("action")) { params = params.with("action", it) }
                            FormLabel("Outlets to switch off")
                            val picked = params.optJSONArray("outlets").ints()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                outletOptions.forEach { (i, name) ->
                                    ChoiceChip(name, i in picked) { params = params.with("outlets", JSONArray(if (i in picked) picked - i else (picked + i).sorted())) }
                                }
                            }
                            FormLabel("Switch back on after normal for")
                            VDropdown(
                                params.optInt("restoreAfterMin"),
                                options(RESTORE_MIN, params.optInt("restoreAfterMin")) { if (it == 0) "Never" else minLabel(it) },
                                { params = params.with("restoreAfterMin", it) }, mono = true,
                            )
                            FormLabel("Label (optional)")
                            VTextInput(label, { label = it }, "e.g. Fridge guard")
                        }
                        InfoCard("Trips once the voltage stays outside the range for the time you set, and switches off only outlets that are on at that moment. With auto-restore it switches them back on once the voltage has been normal for the time you set. The strip is checked about once a minute, so this guards against brownouts and sustained over-voltage, not split-second surges.")
                    }
                    else -> {
                        VCard(padding = 16.dp) {
                            FormLabel("Limit applies to", top = 0.dp)
                            SegTabs(listOf("One outlet", "Whole strip"), if (params.optString("scope") == "outlet") 0 else 1, {
                                params = params.with("scope", if (it == 0) "outlet" else "strip")
                                if (it == 0 && relay == null) relay = strip.outlets.firstOrNull()?.let { o -> o.n - 1 }
                            })
                            if (params.optString("scope") == "outlet") {
                                FormLabel("Outlet")
                                VDropdown(relay ?: 0, outletOptions, { relay = it })
                            }
                            FormLabel("Limit (A)")
                            NumInput(params.optDouble("limitA"), "A") { params = params.with("limitA", it) }
                            FormLabel("Over the limit for")
                            VDropdown(params.optInt("holdSec"), options(HOLD_SEC, params.optInt("holdSec"), ::secLabel), { params = params.with("holdSec", it) }, mono = true)
                            ThenOptions(type, params.optString("action")) { params = params.with("action", it) }
                            FormLabel("Label (optional)")
                            VTextInput(label, { label = it }, "e.g. Heater limit")
                        }
                        InfoCard("Trips once the outlet draws more than the limit for the time you set, even if it is already over when you save. It never switches back on by itself: an overload means the appliance is the problem. Readings arrive about once a minute, so this manages sustained overload.")
                    }
                }
                ToggleCard("Enabled", null, enabled) { enabled = it }
                error?.let { Text(it, color = scheme.error, fontSize = 13.sp) }
                SaveButton {
                    val target = when (type) {
                        "voltage-protection" -> null
                        "current-protection" -> if (params.optString("scope") == "outlet") relay else null
                        else -> relay
                    }
                    error = when {
                        type == "voltage-protection" && params.optBoolean("lowEnabled") && params.optBoolean("highEnabled") && params.optDouble("lowV") >= params.optDouble("highV") -> "Lower limit must be below the upper limit."
                        type == "voltage-protection" && !params.optBoolean("lowEnabled") && !params.optBoolean("highEnabled") -> "Turn on at least one limit."
                        type == "voltage-protection" && params.optJSONArray("outlets").ints().isEmpty() -> "Pick at least one outlet."
                        type == "smart-timer" && params.optJSONArray("days").ints().isEmpty() -> "Pick at least one day."
                        type == "auto-timer" && params.optInt("returnAfterMin") < 1 -> "Return time must be at least 1 minute."
                        type == "current-protection" && params.optDouble("limitA") <= 0 -> "Enter a current limit above 0."
                        (type == "smart-timer" || type == "auto-timer" || type == "power-automation") && target == null -> "Choose an outlet."
                        else -> null
                    }
                    if (error == null) {
                        vm.saveRule(RuleItem(current?.id.orEmpty(), type, strip.mac, target, label.trim(), enabled, params), onDone = onClose)
                    }
                }
                if (current != null && (type == "smart-timer" || type == "auto-timer")) {
                    TextButton(onClick = { vm.deleteRule(current.id); onClose() }, Modifier.fillMaxWidth()) {
                        Text("Remove ${kind.title.lowercase().removeSuffix("s")}", color = scheme.error)
                    }
                }
            }
        }
    }
}
