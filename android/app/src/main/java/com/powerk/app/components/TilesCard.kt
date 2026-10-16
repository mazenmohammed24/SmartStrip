package com.powerk.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import com.powerk.app.Outlet
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel
import com.powerk.app.timerKey

private val TILE_GAP = 8.dp
private val MASTER = 60.dp

/** Rounded tile whose inner corner is cut by the master button's circle (centre sits half a gap beyond the tile). */
private class NotchShape(private val corner: Dp, private val notchRadius: Dp, private val gap: Dp, private val left: Boolean, private val top: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val g = with(density) { gap.toPx() } / 2f
        val nr = with(density) { notchRadius.toPx() }
        val tile = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(r))) }
        val center = Offset(if (left) size.width + g else -g, if (top) size.height + g else -g)
        val hole = Path().apply { addOval(Rect(center, nr)) }
        return Outline.Generic(Path.combine(PathOperation.Difference, tile, hole))
    }
}

@Composable
fun TilesStripCard(strip: Strip, vm: StripsViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var outletSheet by remember { mutableStateOf<Int?>(null) }
    var stripSheet by remember { mutableStateOf(false) }
    val anyOn = strip.outlets.any { it.on }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (strip.online) scheme.primary else scheme.error, CircleShape))
                Spacer(Modifier.width(8.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(Privacy.name(strip), Modifier.weight(1f, fill = false).tap(onClick = onOpen), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    NameEye(strip, 16.dp)
                }
                Spacer(Modifier.width(6.dp))
                WifiBars(strip.rssi, active = strip.online)
                Spacer(Modifier.width(8.dp))
                Pill(if (strip.online) "Online" else "Offline", if (strip.online) Tone.GOOD else Tone.BAD)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Settings, "Strip settings", Modifier.size(22.dp).tap { stripSheet = true }, tint = scheme.onSurfaceVariant)
            }
            Text(
                fmtW(strip.powerW) + (strip.voltage?.let { " · ${fmtNum(it)} V" } ?: ""),
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp),
                textAlign = TextAlign.End, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (strip.online) scheme.primary else scheme.onSurfaceVariant,
            )
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    strip.outlets.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                            pair.forEachIndexed { col, o ->
                                OutletTile(
                                    strip, o, vm, left = col == 0, top = row == 0,
                                    notch = strip.outlets.size >= 2,
                                    modifier = Modifier.weight(1f),
                                ) { outletSheet = o.n }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (strip.outlets.size >= 2) {
                    Box(Modifier.align(Alignment.Center)) { TilesMaster(strip, anyOn) { vm.command(strip.mac, 0, !anyOn) } }
                }
            }
        }
    }
    outletSheet?.let { n -> OutletSheet(strip.mac, n, vm) { outletSheet = null } }
    if (stripSheet) StripSheet(strip.mac, vm, { stripSheet = false }, onOpen)
}

@Composable
private fun OutletTile(strip: Strip, o: Outlet, vm: StripsViewModel, left: Boolean, top: Boolean, notch: Boolean, modifier: Modifier, onLongPress: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (o.on) scheme.primaryContainer.copy(alpha = 0.7f) else scheme.surfaceContainer, spring(stiffness = 300f), label = "tileBg")
    val edge by animateColorAsState(if (o.on) scheme.primary.copy(alpha = 0.3f) else Color0, tween(260), label = "tileEdge")
    val faceBg by animateColorAsState(if (o.on) scheme.primary.copy(alpha = 0.2f) else scheme.surface.copy(alpha = 0.85f), tween(260), label = "tileFace")
    val shape = if (notch) NotchShape(22.dp, MASTER / 2 + 6.dp, TILE_GAP, left, top) else RoundedCornerShape(22.dp)
    val timer = vm.relayTimers[timerKey(strip.mac, o.n)]
    val align = if (left) Alignment.Start else Alignment.End
    val textAlign = if (left) TextAlign.Start else TextAlign.End
    val pop = rememberPop(o.on)
    val orbit by animateFloatAsState(if (o.on) 1f else 0f, tween(380), label = "tileOrbit")
    // The master button's notch eats the inner bottom corner of the top tiles, so keep the text clear of it.
    val inset = if (notch && top) 26.dp else 0.dp

    Box(
        modifier.height(128.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(shape).background(bg).border(1.5.dp, edge, shape)
            .tap(enabled = strip.online, onLongClick = onLongPress) { vm.command(strip.mac, o.n, !o.on) },
    ) {
        if (orbit > 0.01f) OrbitEdge(shape, scheme.primary, orbit, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().padding(12.dp)) {
        Row(Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val face: @Composable () -> Unit = {
                Box(Modifier.size(34.dp).background(faceBg, CircleShape), contentAlignment = Alignment.Center) {
                    if (o.icon == "plug" || o.icon.isBlank()) SocketFace(o.on, 22.dp) else Text(iconGlyph(o.icon), fontSize = 17.sp)
                }
            }
            val marks: @Composable () -> Unit = {
                AnimatedVisibility(o.on, enter = fadeIn(tween(160)) + scaleIn(spring(0.4f, 500f), initialScale = 0.3f), exit = fadeOut(tween(100)) + scaleOut(tween(120))) {
                    Icon(Icons.Rounded.Bolt, null, Modifier.size(14.dp), tint = scheme.primary)
                }
                if (timer != null) Icon(Icons.Rounded.AccessTime, "Timer active", Modifier.size(14.dp), tint = scheme.tertiary)
            }
            if (left) { face(); marks() } else { marks(); face() }
        }
        Column(
            Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(start = if (left) 0.dp else inset, end = if (left) inset else 0.dp),
            horizontalAlignment = align,
        ) {
            Text(o.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
            AnimatedVisibility(o.on, enter = fadeIn(tween(220)) + expandVertically(spring(0.8f, 400f)), exit = fadeOut(tween(100)) + shrinkVertically(tween(160))) {
                Column(horizontalAlignment = align) {
                    val since = if (o.changedAtMs > 0) fmtDuration(Ticker.now - o.changedAtMs) else ""
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmtW(o.powerW), fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.primary, maxLines = 1)
                        if (since.isNotEmpty()) Text(" · $since", fontFamily = Mono, fontSize = 10.sp, color = scheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(bottom = 1.dp))
                    }
                    val amps = strip.voltage?.takeIf { it > 0 }?.let { fmtNum(o.powerW / it, 2) + " A · " }.orEmpty()
                    Text(timer?.let { "⏱ " + TimerCountdown(it.restoreAtMs) } ?: "$amps${o.tempC}°C", fontFamily = Mono, fontSize = 10.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
                }
            }
            AnimatedVisibility(!o.on, enter = fadeIn(tween(220)) + expandVertically(), exit = fadeOut(tween(100)) + shrinkVertically(tween(140))) {
                Text("OFF", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, textAlign = textAlign)
            }
        }
        }
    }
}

private val Color0 = Color.Transparent

/** Springy squash-and-release that plays whenever [on] flips (not on first composition). */
@Composable
private fun rememberPop(on: Boolean): Animatable<Float, AnimationVector1D> {
    val pop = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on != seen) {
            seen = on
            pop.snapTo(0.93f)
            pop.animateTo(1f, spring(0.38f, 380f))
        }
    }
    return pop
}

/** Two soft highlights that circle the outline of [shape]. Only draws; never recomposes while spinning. */
@Composable
private fun OrbitEdge(shape: Shape, color: Color, alpha: Float, modifier: Modifier) {
    val angle by rememberInfiniteTransition(label = "orbit").animateFloat(
        0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "orbitAngle",
    )
    Box(
        modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val bright = color.toArgb()
            val clear = color.copy(alpha = 0f).toArgb()
            val stroke = Stroke(6.dp.toPx())
            val matrix = android.graphics.Matrix()
            onDrawBehind {
                matrix.setRotate(angle, size.width / 2f, size.height / 2f)
                val brush = object : ShaderBrush() {
                    override fun createShader(size: Size): android.graphics.Shader =
                        android.graphics.SweepGradient(size.width / 2f, size.height / 2f, intArrayOf(clear, bright, clear, bright, clear), floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f))
                            .also { it.setLocalMatrix(matrix) }
                }
                drawOutline(outline, brush, alpha, stroke)
            }
        },
    )
}

@Composable
private fun TilesMaster(strip: Strip, anyOn: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg by animateColorAsState(if (anyOn) scheme.onPrimary else scheme.onSurfaceVariant, tween(220), label = "mFg")
    val bg by animateColorAsState(if (anyOn) scheme.primary else scheme.surfaceContainerHigh, tween(220), label = "mBg")
    val pop = rememberPop(anyOn)
    val orbit by animateFloatAsState(if (anyOn) 1f else 0f, tween(380), label = "mOrbit")
    val dotOn = scheme.primary
    val dotOff = scheme.outline.copy(alpha = 0.45f)
    val dots = (0 until 4).map { i -> animateColorAsState(if (strip.outlets.getOrNull(i)?.on == true) dotOn else dotOff, tween(220), label = "mDot$i").value }
    Surface(
        shape = CircleShape, color = scheme.surface, border = BorderStroke(1.dp, scheme.outlineVariant), shadowElevation = 6.dp,
        modifier = Modifier.size(MASTER).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.tap(enabled = strip.online, onClick = onClick),
    ) {
        Box(Modifier.padding(7.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Fingerprint, "Toggle all", Modifier.size(26.dp), tint = fg)
            }
            Canvas(Modifier.fillMaxSize()) {
                val r = 2.5.dp.toPx()
                val inset = 3.dp.toPx()
                val pts = listOf(Offset(inset, inset), Offset(size.width - inset, inset), Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset))
                pts.forEachIndexed { i, pt -> drawCircle(dots[i], r, pt) }
            }
        }
        if (orbit > 0.01f) OrbitEdge(CircleShape, scheme.primary, orbit, Modifier.fillMaxSize())
    }
}