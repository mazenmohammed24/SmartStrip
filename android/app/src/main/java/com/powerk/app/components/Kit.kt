package com.powerk.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "tap")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun VCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

enum class Tone { GOOD, WARN, BAD, NEUTRAL }

@Composable
fun Pill(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, outlined: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        Tone.GOOD -> scheme.primaryContainer to scheme.primary
        Tone.WARN -> scheme.tertiaryContainer to scheme.tertiary
        Tone.BAD -> scheme.errorContainer to scheme.error
        Tone.NEUTRAL -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        border = if (outlined) BorderStroke(1.dp, fg.copy(alpha = 0.35f)) else null,
        modifier = modifier,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun MetricPill(value: String) = Pill(value, Tone.NEUTRAL)

@Composable
fun StatusDot(online: Boolean) {
    Box(
        Modifier
            .size(9.dp)
            .background(if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, CircleShape)
    )
}

@Composable
fun LiveBadge(link: Link) {
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    val scheme = MaterialTheme.colorScheme
    val (tone, label, color) = when (link) {
        Link.LIVE -> Triple(Tone.GOOD, "LIVE", scheme.primary)
        Link.CONNECTING -> Triple(Tone.WARN, "CONNECTING", scheme.tertiary)
        Link.OFFLINE -> Triple(Tone.BAD, "OFFLINE", scheme.error)
    }
    val bg = when (tone) {
        Tone.GOOD -> scheme.primaryContainer
        Tone.WARN -> scheme.tertiaryContainer
        else -> scheme.errorContainer
    }
    Surface(shape = CircleShape, color = bg) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).alpha(if (link == Link.LIVE) alpha else 1f).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text.uppercase(),
        modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(38.dp).tap(onClick = onClick),
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
            Text(title, fontSize = if (eyebrow != null) 24.sp else 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SegTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, tween(140), label = "seg")
                val edge by animateColorAsState(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else Color.Transparent, tween(140), label = "segedge")
                val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(140), label = "segfg")
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(bg).border(1.dp, edge, RoundedCornerShape(13.dp)).tap { onSelect(i) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(MaterialTheme.shapes.large).alpha(a).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun rememberDismissKeyboard(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return { keyboard?.hide(); focus.clearFocus() }
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
    var text by remember(value) { mutableStateOf(value) }
    val dismiss = rememberDismissKeyboard()
    fun commit() { if (text != value && text.isNotBlank()) onChange(text.trim()) else text = value }
    // Numeric/suffixed fields stay compact; text fields fill the available width.
    val compact = numeric || suffix != null
    Surface(modifier = if (compact) modifier else modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = scheme.surfaceContainer, border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = Mono, color = scheme.onSurface, textAlign = if (compact) TextAlign.End else TextAlign.Start),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit(); dismiss() }),
                decorationBox = { inner ->
                    Box { if (text.isEmpty()) Text(label, fontSize = 13.sp, color = scheme.onSurfaceVariant); inner() }
                },
                modifier = (if (compact) Modifier.width(80.dp) else Modifier.weight(1f)).onFocusChanged { if (!it.isFocused) commit() },
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
    val level = if (active) rssiLevel(rssi) else 0
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
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