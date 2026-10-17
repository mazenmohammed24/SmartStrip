package com.powerk.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.powerk.app.AppElevation
import com.powerk.app.AppReducedMotion
import com.powerk.app.AppSemantic
import com.powerk.app.Link
import java.time.Duration
import java.time.Instant
import java.util.Locale

val Mono = com.powerk.app.PlexMono

fun fmtW(w: Double): String =
    if (w >= 1000) String.format(Locale.US, "%.2f kW", w / 1000) else String.format(Locale.US, "%.0f W", w)

fun fmtKwh(v: Double): String = String.format(Locale.US, if (v >= 100) "%.0f" else "%.2f", v)

fun fmtNum(v: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", v)

fun ago(iso: String): String {
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val s = Duration.between(then, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        else -> "${s / 86400}d"
    }
}

/** Press-scale click used by every tappable surface. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tap(enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = AppReducedMotion
    val scale by animateFloatAsState(if (pressed && !reduced) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "tap")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)
}

/**
 * Theme-tinted drop shadow: the single place that owns shadow colour and strength, so every
 * lifted surface (cards, dock, rail, thumbs, FABs) shares one depth language. Use [AppElevation]
 * levels for the height instead of ad-hoc numbers.
 */
@Composable
fun Modifier.appShadow(shape: Shape, elevation: Dp): Modifier {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.background.luminance() > 0.5f
    // Light themes: a neutral-warm tint at partial strength reads softer than pure black.
    // Dark themes: deeper black keeps lifted surfaces separated from the page.
    val tint = if (light) scheme.onSurface.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.88f)
    return shadow(elevation, shape, clip = false, ambientColor = tint, spotColor = tint)
}

/**
 * Opaque fill for "selected" capsules (dock, rail, segmented tabs, mini outlet pills).
 * Composited onto a container colour on purpose: a translucent capsule lets shadows and
 * whatever sits behind it show through, which reads as a strange lighter blob inside the pill.
 */
@Composable
fun capsuleFill(strong: Boolean = false): Color {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.surface.luminance() > 0.5f
    val base = if (light) scheme.surfaceContainer else scheme.surfaceContainerHigh
    return scheme.primary.copy(alpha = if (strong) 0.24f else 0.17f).compositeOver(base)
}

/**
 * Pull-to-refresh shell for pages whose data can be re-fetched manually. The page keeps its own
 * scroll container; the indicator follows the nested scroll and only shows while [refreshing].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun VCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier.fillMaxWidth()
            .appShadow(shape, AppElevation.card)
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.9f)),
    ) {
        Column(
            Modifier
                .background(
                    // Fully opaque: a translucent card lets whatever sits behind it ghost through
                    // (e.g. the swipe-to-delete layer in Activity showed under the alert text).
                    Brush.verticalGradient(listOf(scheme.surface, scheme.surfaceContainerLow)),
                )
                .drawBehind {
                    // Diagonal specular sheen: light grazing across the glass pane.
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.9f, size.height * 0.85f),
                        ),
                    )
                    // Hairline highlight along the top edge; makes flat dark cards feel lifted.
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, scheme.onSurface.copy(alpha = 0.10f), Color.Transparent),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(padding),
            content = content,
        )
    }
}

enum class Tone { GOOD, WARN, BAD, NEUTRAL }

@Composable
fun Pill(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, outlined: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val semantic = AppSemantic
    val (bg, fg) = when (tone) {
        Tone.GOOD -> semantic.successContainer to semantic.onSuccessContainer
        Tone.WARN -> semantic.warningContainer to semantic.onWarningContainer
        Tone.BAD -> scheme.errorContainer to scheme.error
        Tone.NEUTRAL -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        border = if (outlined) BorderStroke(1.dp, fg.copy(alpha = 0.3f)) else null,
        modifier = modifier,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun MetricPill(value: String) = Pill(value, Tone.NEUTRAL)

@Composable
fun StatusDot(online: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val semantic = AppSemantic
    Box(
        Modifier
            .size(9.dp)
            .background(if (online) semantic.success else scheme.error, CircleShape)
    )
}

@Composable
fun LiveBadge(link: Link) {
    val reduced = AppReducedMotion
    // The pulse is decorative: skipped entirely when motion is reduced.
    val alpha = if (link == Link.LIVE && !reduced) {
        val pulse = rememberInfiniteTransition(label = "live")
        val a by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "alpha",
        )
        a
    } else {
        1f
    }
    val scheme = MaterialTheme.colorScheme
    val semantic = AppSemantic
    val (label, color, bg) = when (link) {
        Link.LIVE -> Triple("LIVE", semantic.success, semantic.successContainer)
        Link.CONNECTING -> Triple("CONNECTING", semantic.warning, semantic.warningContainer)
        Link.OFFLINE -> Triple("OFFLINE", scheme.error, scheme.errorContainer)
    }
    Surface(shape = CircleShape, color = bg) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).alpha(alpha).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun IconBox(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(container),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun BackBtn(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(40.dp).tap(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(18.dp)) }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BackBtn(onBack)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) SectionLabel(eyebrow)
            Text(
                title,
                fontSize = if (eyebrow != null) 26.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SegTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.surface.luminance() > 0.5f
    val shape = RoundedCornerShape(18.dp)
    val thumbShape = RoundedCornerShape(14.dp)
    val gap = 4.dp
    val inset = 4.dp
    Surface(
        shape = shape,
        color = scheme.surfaceContainerLow.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, (if (light) scheme.outlineVariant else Color.White).copy(alpha = 0.26f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val reduced = AppReducedMotion
            val segW = (maxWidth - inset * 2 - gap * (options.size - 1).coerceAtLeast(0)) / options.size
            val thumbX by animateDpAsState(
                inset + (segW + gap) * selected,
                if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 360f),
                label = "segThumbX",
            )
            Box(Modifier.fillMaxWidth()) {
                // Sliding thumb — opaque fill so no shadow or content shows through as a blob.
                Box(
                    Modifier
                        .offset(x = thumbX)
                        .padding(vertical = inset)
                        .width(segW)
                        .height(34.dp)
                        .shadow(3.dp, thumbShape, clip = false)
                        .clip(thumbShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(capsuleFill(strong = true), capsuleFill()),
                            ),
                        )
                        .border(1.dp, scheme.primary.copy(alpha = 0.35f), thumbShape),
                )
                Row(Modifier.padding(inset), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    options.forEachIndexed { i, label ->
                        val on = i == selected
                        val fg by animateColorAsState(
                            if (on) scheme.primary else scheme.onSurfaceVariant,
                            spring(stiffness = Spring.StiffnessMediumLow),
                            label = "segfg",
                        )
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(thumbShape).tap { onSelect(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Shimmer(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val reduced = AppReducedMotion
    val x = if (reduced) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "shimmer")
        val v by t.animateFloat(0f, 1400f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x")
        v
    }
    Box(
        modifier.clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHigh)
            .drawBehind {
                if (reduced) return@drawBehind
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, scheme.onSurface.copy(alpha = 0.07f), Color.Transparent),
                        start = Offset(x - 400f, 0f),
                        end = Offset(x, size.height * 1.6f),
                    ),
                )
            },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val reduced = AppReducedMotion
        val shown = remember { MutableTransitionState(false) }
        LaunchedEffect(Unit) { shown.targetState = true }
        AnimatedVisibility(
            visibleState = shown,
            enter = if (reduced) fadeIn(tween(140)) else scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f) + fadeIn(tween(140)),
            exit = if (reduced) fadeOut(tween(120)) else scaleOut(tween(130), targetScale = 0.9f) + fadeOut(tween(120)),
        ) {
            val scheme = MaterialTheme.colorScheme
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.9f)),
                shadowElevation = AppElevation.overlay,
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                    Spacer(Modifier.height(8.dp))
                    Text(text, fontSize = 13.5.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = scheme.onSurfaceVariant) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onConfirm(); onDismiss() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                        ) { Text(confirm, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberDismissKeyboard(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return { keyboard?.hide(); focus.clearFocus() }
}

/** Rename a strip or outlet: live text field (Save lights up while typing), Cancel/Save. */
@Composable
fun RenameDialog(current: String, title: String = "Rename", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.9f)),
            shadowElevation = AppElevation.overlay,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                Spacer(Modifier.height(12.dp))
                VTextField(name, { name = it }, "Name")
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = scheme.onSurfaceVariant) }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = { onSave(name.trim()); onDismiss() },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
fun VTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    suffix: String? = null,
    singleLine: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var focused by remember { mutableStateOf(false) }
    // The caller can reset the value (another outlet, server refresh) — mirror it locally.
    LaunchedEffect(value) { if (value != tfv.text) tfv = TextFieldValue(value, TextRange(value.length)) }
    val dismiss = rememberDismissKeyboard()
    // Every keystroke propagates live so Save buttons enable and searches filter while typing;
    // done/blur only normalises the value (trims).
    fun commit() {
        val trimmed = tfv.text.trim()
        if (trimmed != tfv.text) { tfv = tfv.copy(text = trimmed, selection = TextRange(trimmed.length)); onChange(trimmed) }
    }
    // Numeric/suffixed fields stay compact; text fields fill the available width.
    val compact = numeric || suffix != null
    Surface(modifier = if (compact) modifier else modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = scheme.surfaceContainer, border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = tfv,
                onValueChange = { tfv = it; onChange(it.text) },
                singleLine = singleLine,
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = Mono, color = scheme.onSurface, textAlign = if (compact) TextAlign.End else TextAlign.Start),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit(); dismiss() }),
                decorationBox = { inner ->
                    Box { if (tfv.text.isEmpty()) Text(label, fontSize = 13.sp, color = scheme.onSurfaceVariant); inner() }
                },
                modifier = (if (compact) Modifier.width(80.dp) else Modifier.weight(1f)).onFocusChanged { st ->
                    // Focusing selects the whole value, so editing a name never starts with
                    // a manual select-all. Blur commits (trims).
                    if (st.isFocused && !focused) tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
                    focused = st.isFocused
                    if (!st.isFocused) commit()
                },
            )
            if (suffix != null) Text(" $suffix", fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun IconTextButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick) { content() }
}

fun rssiLevel(rssi: Int?): Int = when {
    rssi == null -> 0
    rssi >= -55 -> 4
    rssi >= -67 -> 3
    rssi >= -75 -> 2
    else -> 1
}

@Composable
fun WifiBars(rssi: Int?, modifier: Modifier = Modifier, active: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    // Online but the strip hasn't reported signal strength yet (fresh MTTL sessions start with
    // rssi unset): show a connected glyph instead of four dead bars that read as "no Wi-Fi".
    if (active && rssi == null) {
        Icon(Icons.Rounded.Wifi, "Connected", modifier.size(13.dp), tint = scheme.primary)
        return
    }
    val level = if (active) rssiLevel(rssi) else 0
    val on = scheme.primary
    val off = scheme.outlineVariant
    Row(modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) Box(Modifier.width(3.dp).height((3 + i * 2.25f).dp).clip(RoundedCornerShape(1.dp)).background(if (i <= level) on else off))
    }
}

fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h ${(s % 3600) / 60}m"
        else -> "${s / 86400}d ${(s % 86400) / 3600}h"
    }
}

/** One shared 1 s clock; only composables that read [now] recompose on tick. */
object Ticker {
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    @Composable
    fun Run() {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                now = System.currentTimeMillis()
            }
        }
    }
}