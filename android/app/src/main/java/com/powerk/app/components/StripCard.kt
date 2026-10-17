package com.powerk.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.scale
import com.powerk.app.RelayTimer
import com.powerk.app.timerKey
import com.powerk.app.Outlet
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel

@Composable
fun MasterButton(on: Boolean, enabled: Boolean, onClick: () -> Unit, size: Dp = 44.dp) {
    val scheme = MaterialTheme.colorScheme
    val big = size >= 60.dp
    val bg by animateColorAsState(if (on) scheme.primary else if (big) scheme.surfaceContainerHigh else scheme.surface, tween(160), label = "masterBg")
    val fg by animateColorAsState(if (on) scheme.onPrimary else scheme.onSurfaceVariant, tween(160), label = "masterFg")
    if (!big) {
        Surface(
            shape = CircleShape,
            color = bg,
            border = BorderStroke(if (on) 0.dp else 1.dp, scheme.outlineVariant),
            modifier = Modifier.size(size).tap(enabled = enabled, onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PowerSettingsNew, "Toggle all", Modifier.size(size * 0.5f), tint = fg) }
        }
        return
    }
    val dot by animateColorAsState(if (on) scheme.primary else scheme.outline.copy(alpha = 0.45f), tween(160), label = "masterDot")
    Surface(
        shape = CircleShape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 6.dp,
        modifier = Modifier.size(size).tap(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.padding(7.dp), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = bg, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Fingerprint, "Toggle all", Modifier.size(size * 0.4f), tint = fg) }
            }
            Canvas(Modifier.fillMaxSize()) {
                val r = 2.5.dp.toPx()
                val inset = 5.dp.toPx()
                listOf(Offset(inset, inset), Offset(this.size.width - inset, inset), Offset(inset, this.size.height - inset), Offset(this.size.width - inset, this.size.height - inset))
                    .forEach { drawCircle(dot, r, it) }
            }
        }
    }
}

@Composable
fun SocketFace(on: Boolean, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (on) scheme.primary else scheme.outline, tween(160), label = "socket")
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawCircle(tint, radius = w / 2 - 1.5.dp.toPx(), style = Stroke(2.dp.toPx()))
        val slotW = w * 0.09f
        val slotH = w * 0.24f
        listOf(0.36f, 0.64f).forEach { x ->
            drawRoundRect(tint, Offset(w * x - slotW / 2, w * 0.5f - slotH / 2), Size(slotW, slotH), CornerRadius(slotW / 2))
        }
    }
}

@Composable
private fun OutletSubline(strip: Strip, o: Outlet, timer: RelayTimer?) {
    val scheme = MaterialTheme.colorScheme
    val text = when {
        timer != null -> "⏱ ${TimerCountdown(timer.restoreAtMs)} → ${timer.state}"
        o.on -> buildString {
            if (o.changedAtMs > 0) append(fmtDuration(Ticker.now - o.changedAtMs)).append(" · ")
            append(fmtW(o.powerW)).append(" · ").append(o.tempC).append("°C")
        }
        else -> "Off · ${o.tempC}°C"
    }
    Text(text, fontSize = 11.sp, fontFamily = Mono, color = if (timer != null) scheme.tertiary else scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun OutletLine(strip: Strip, o: Outlet, vm: StripsViewModel, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (o.on) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainer, tween(160), label = "lineBg")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).tap(onClick = onOpen).padding(start = 8.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (o.on) scheme.primaryContainer else scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (o.icon == "plug" || o.icon.isBlank()) SocketFace(o.on, 20.dp) else Text(iconGlyph(o.icon), fontSize = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(o.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutletSubline(strip, o, vm.relayTimers[timerKey(strip.mac, o.n)])
        }
        Switch(
            checked = o.on,
            enabled = strip.online,
            onCheckedChange = { vm.command(strip.mac, o.n, it) },
            modifier = Modifier.scale(0.78f),
        )
    }
}

@Composable
private fun StripHead(strip: Strip, onEdit: () -> Unit, onOpen: () -> Unit, onRename: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBox(size = 36.dp, modifier = Modifier.tap(onClick = onEdit)) { Text(iconGlyph(strip.icon), fontSize = 17.sp) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f).tap(onClick = onEdit, onLongClick = onRename)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Privacy.name(strip), Modifier.weight(1f, fill = false), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Edit, "Edit strip", Modifier.size(12.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                NameEye(strip, 15.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (strip.online) scheme.primary else scheme.error, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (strip.online) strip.location?.ifBlank { null } ?: strip.model else "Offline",
                    fontSize = 11.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        WifiBars(strip.rssi, active = strip.online)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.ChevronRight, "Open strip", Modifier.size(20.dp), tint = scheme.onSurfaceVariant) }
    }
}

@Composable
fun StripCard(strip: Strip, vm: StripsViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var outletSheet by remember { mutableStateOf<Int?>(null) }
    var stripSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val onCount = strip.outlets.count { it.on }
    VCard(modifier, padding = 10.dp) {
        StripHead(strip, onEdit = { stripSheet = true }, onOpen = onOpen, onRename = { renaming = true })
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtW(strip.powerW), fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary, maxLines = 1)
            Text(
                "  ·  $onCount/${strip.outlets.size} on" + (strip.voltage?.let { "  ·  ${fmtNum(it, 0)} V" } ?: ""),
                fontSize = 11.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            SmallAction("All on", strip.online && onCount < strip.outlets.size) { vm.command(strip.mac, 0, true) }
            Spacer(Modifier.width(6.dp))
            SmallAction("Off", strip.online && onCount > 0) { vm.command(strip.mac, 0, false) }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            strip.outlets.forEach { o -> OutletLine(strip, o, vm) { outletSheet = o.n } }
        }
    }
    outletSheet?.let { n -> OutletSheet(strip.mac, n, vm) { outletSheet = null } }
    if (stripSheet) StripSheet(strip.mac, vm, { stripSheet = false }, onOpen)
    if (renaming) RenameDialog(strip.name, "Rename strip", { renaming = false }) { vm.renameStrip(strip.mac, it) }
}

@Composable
private fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f).tap(enabled = enabled, onClick = onClick),
    ) { Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

/**
 * Minimised outlet control for collapsed strip rows: one small pill per outlet.
 * Tap toggles the outlet, long-press opens the full outlet sheet.
 */
@Composable
private fun OutletMiniPill(strip: Strip, o: Outlet, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (o.on) capsuleFill() else scheme.surfaceContainer,
        border = BorderStroke(1.dp, if (o.on) scheme.primary.copy(alpha = 0.32f) else scheme.outlineVariant),
        modifier = Modifier
            .alpha(if (strip.online) 1f else 0.45f)
            .semantics { contentDescription = o.name }
            .tap(enabled = strip.online, onLongClick = onLongClick, onClick = onClick),
    ) {
        Box(Modifier.height(30.dp).widthIn(min = 36.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(iconGlyph(o.icon), fontSize = 14.sp)
        }
    }
}

@Composable
fun StripRow(strip: Strip, vm: StripsViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var stripSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var outletSheet by remember { mutableStateOf<Int?>(null) }
    val onCount = strip.outlets.count { it.on }
    VCard(modifier, onClick = onOpen, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(size = 36.dp, modifier = Modifier.tap(onClick = { stripSheet = true })) { Text(iconGlyph(strip.icon), fontSize = 17.sp) }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(Privacy.name(strip), Modifier.tap(onClick = onOpen, onLongClick = { renaming = true }), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (strip.online) "$onCount/${strip.outlets.size} on · ${fmtW(strip.powerW)}" else "Offline",
                    fontSize = 11.sp, fontFamily = Mono, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            WifiBars(strip.rssi, active = strip.online)
            Spacer(Modifier.width(10.dp))
            MasterButton(strip.on, strip.online, { vm.command(strip.mac, 0, !strip.on) }, 34.dp)
        }
        // Outlets stay controllable while the row is collapsed — a minimised strip of pills.
        if (strip.outlets.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                strip.outlets.forEach { o ->
                    OutletMiniPill(
                        strip, o,
                        onClick = { vm.command(strip.mac, o.n, !o.on) },
                        onLongClick = { outletSheet = o.n },
                    )
                }
            }
        }
    }
    outletSheet?.let { n -> OutletSheet(strip.mac, n, vm) { outletSheet = null } }
    if (stripSheet) StripSheet(strip.mac, vm, { stripSheet = false }, onOpen)
    if (renaming) RenameDialog(strip.name, "Rename strip", { renaming = false }) { vm.renameStrip(strip.mac, it) }
}
@Composable
fun RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
