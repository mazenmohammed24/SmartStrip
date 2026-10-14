package com.powerk.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PowerkApp() {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1100)
        showSplash = false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PowerkNavigation()

        androidx.compose.animation.AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(tween(420)) + scaleOut(tween(420), targetScale = 1.06f),
        ) { Splash() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Splash() {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    val scheme = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.background))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        val p = appear.value
                        scaleX = 0.62f + 0.38f * p
                        scaleY = 0.62f + 0.38f * p
                        alpha = p
                        rotationZ = -28f * (1f - p)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp),
                    tint = Color.Unspecified,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.graphicsLayer { alpha = appear.value },
            )
            Text(
                stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = appear.value },
            )
            Spacer(Modifier.height(38.dp))
            CircularWavyProgressIndicator(Modifier.size(30.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlugsScreen(
    snapshot: Snapshot?,
    error: String?,
    loading: Boolean,
    pending: Map<String, Boolean>,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onCommand: (String, Int, Boolean) -> Unit,
    onIconChange: (String, Int?, String) -> Unit,
) {
    val strips = snapshot?.strips.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Header(
                stripCount = strips.size,
                onlineCount = strips.count { it.online },
                configured = Server.configured,
                loading = loading,
                onRefresh = onRefresh,
            )
        }

        item {
            if (error != null) {
                MessageCard(error, isError = true, icon = Icons.Filled.Warning)
            }
        }

        if (!Server.configured) {
            item {
                EmptyState(
                    title = stringResource(R.string.no_server_title),
                    body = stringResource(R.string.no_server_body),
                    action = stringResource(R.string.open_settings),
                    onAction = onOpenSettings,
                )
            }
        } else {
            items(strips, key = { it.mac }) { strip ->
                StripCard(
                    strip = strip,
                    showMac = strips.size > 1,
                    pending = pending,
                    onCommand = onCommand,
                    onIconChange = onIconChange,
                    modifier = Modifier,
                )
            }

            if (snapshot != null && strips.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.no_strip_title),
                        body = stringResource(R.string.no_strip_body),
                    )
                }
            }

            if (snapshot == null && error == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularWavyProgressIndicator(Modifier.size(34.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.connecting, Server.host),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    stripCount: Int,
    onlineCount: Int,
    configured: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle = when {
                !configured -> stringResource(R.string.header_no_server)
                onlineCount > 0 -> stringResource(R.string.header_online, onlineCount, Server.host)
                stripCount > 0 -> stringResource(R.string.header_known_offline, stripCount)
                else -> stringResource(R.string.header_waiting, Server.host)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = configured && !loading) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.refresh),
                modifier = Modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StripCard(
    strip: Strip,
    showMac: Boolean,
    pending: Map<String, Boolean>,
    onCommand: (String, Int, Boolean) -> Unit,
    onIconChange: (String, Int?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateOf: (Int) -> Boolean = { n -> pending["${strip.mac}:$n"] ?: strip.outletOn(n) }
    val anyOn = (1..4).any(stateOf)
    val allPending = (1..4).any { pending.containsKey("${strip.mac}:$it") }

    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            var stripIconMenu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(online = strip.online)
                Spacer(Modifier.width(10.dp))
                Box {
                    OutlinedButton(onClick = { stripIconMenu = true }) {
                        Text(iconGlyph(strip.icon), style = MaterialTheme.typography.titleLarge)
                    }
                    IconPickerMenu(
                        expanded = stripIconMenu,
                        selected = strip.icon,
                        onDismiss = { stripIconMenu = false },
                        onSelect = { icon ->
                            stripIconMenu = false
                            onIconChange(strip.mac, null, icon)
                        },
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val title = stringResource(R.string.strip_name) +
                        if (showMac) " · ${strip.mac.takeLast(7)}" else ""
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.strip_fw, strip.fw),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PowerRing(watts = strip.powerW, enabled = strip.online)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill("${strip.energyKwh} kWh")
                    strip.voltage?.let { MetricPill("${it.roundToInt()} V") }
                    strip.currentA?.let { MetricPill("$it A") }
                    strip.rssi?.let { MetricPill("$it dBm") }
                }
            }

            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (column in 0 until 2) {
                        val outlet = strip.outlets.getOrNull(row * 2 + column)
                        if (outlet == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            OutletTile(
                                outlet = outlet,
                                on = stateOf(outlet.n),
                                pending = pending.containsKey("${strip.mac}:${outlet.n}"),
                                enabled = strip.online,
                                onToggle = { onCommand(strip.mac, outlet.n, it) },
                                onIconChange = { icon -> onIconChange(strip.mac, outlet.n, icon) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = { onCommand(strip.mac, 0, !anyOn) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = strip.online && !allPending,
            ) {
                if (allPending) {
                    CircularWavyProgressIndicator(Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    stringResource(if (anyOn) R.string.turn_all_off else R.string.turn_all_on),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OutletTile(
    outlet: Outlet,
    on: Boolean,
    pending: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onIconChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val progress by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "toggle",
    )
    val container = lerp(scheme.surfaceContainerHighest, scheme.primaryContainer, progress)
    val content = lerp(scheme.onSurface, scheme.onPrimaryContainer, progress)

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle(!on)
        },
        enabled = enabled && !pending,
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = modifier
            .heightIn(min = 96.dp)
            .graphicsLayer {
                val pop = 1f + 0.015f * progress
                scaleX = pop
                scaleY = pop
            },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            var iconMenu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { iconMenu = true }, enabled = enabled && !pending) {
                        Text(iconGlyph(outlet.icon), style = MaterialTheme.typography.titleMedium)
                    }
                    IconPickerMenu(
                        expanded = iconMenu,
                        selected = outlet.icon,
                        onDismiss = { iconMenu = false },
                        onSelect = { icon ->
                            iconMenu = false
                            onIconChange(icon)
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.outlet_n, outlet.n),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (pending) {
                    CircularWavyProgressIndicator(Modifier.size(22.dp))
                } else {
                    MiniSwitch(progress = progress, onColor = scheme.primary)
                }
            }
            Text(
                stringResource(if (on) R.string.state_on else R.string.state_off),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (on) {
                    stringResource(R.string.tile_on_detail, outlet.powerW.toString(), outlet.tempC)
                } else {
                    stringResource(R.string.tile_off_detail, outlet.tempC)
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Small animated switch drawn inside an outlet tile: track + sliding thumb. */
@Composable
private fun MiniSwitch(progress: Float, onColor: Color) {
    val scheme = MaterialTheme.colorScheme
    val track = lerp(scheme.outlineVariant, onColor, progress)
    val thumb = lerp(scheme.surfaceContainerLowest, scheme.onPrimary, progress)

    Box(
        Modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .offset(x = (2 + 16 * progress).dp, y = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(thumb),
        )
    }
}


private val IconOptions = listOf(
    "power-strip" to "?", "plug" to "??", "lamp" to "??", "charger" to "?",
    "computer" to "???", "laptop" to "??", "monitor" to "???", "printer" to "???",
    "phone" to "??", "router" to "??", "tv" to "??", "speaker" to "??",
    "gamepad" to "??", "fridge" to "??", "heater" to "??", "fan" to "??",
    "air-conditioner" to "??", "coffee" to "?", "kettle" to "??", "microwave" to "??",
    "washer" to "??", "idle" to "?",
)

private fun iconGlyph(icon: String): String = IconOptions.firstOrNull { it.first == icon }?.second ?: "??"

@Composable
private fun IconPickerMenu(
    expanded: Boolean,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        IconOptions.forEach { (value, glyph) ->
            DropdownMenuItem(
                text = { Text("$glyph  ${value.replace('-', ' ')}") },
                onClick = { onSelect(value) },
                leadingIcon = { if (selected == value) Text("?") },
            )
        }
    }
}

@Composable
private fun PowerRing(watts: Double, enabled: Boolean) {
    val target = (watts / 2000.0).coerceIn(0.0, 1.0).toFloat()
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "sweep",
    )
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceContainerHighest
    val arc = if (enabled && watts > 0.05) scheme.primary else scheme.outlineVariant

    Box(Modifier.size(142.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = arc,
                startAngle = 135f,
                sweepAngle = 270f * sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        // fixed-width inner column keeps the numbers clear of the ring stroke
        Column(
            Modifier.width(92.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${watts.roundToInt()}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    stringResource(R.string.unit_watts),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                stringResource(R.string.watts_now),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MetricPill(value: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                value,
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Box(
        Modifier
            .size(11.dp)
            .background(
                if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                CircleShape,
            )
    )
}

@Composable
private fun MessageCard(text: String, isError: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.padding(18.dp).size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}


@Composable
private fun PowerScreen(snapshot: Snapshot?) {
    val strips = snapshot?.strips.orEmpty()
    var selectedId by remember(strips) { mutableStateOf(strips.firstOrNull()?.mac.orEmpty()) }
    var range by remember { mutableStateOf("7d") }
    var summary by remember { mutableStateOf<PowerSummary?>(null) }
    var history by remember { mutableStateOf<PowerHistory?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selected = strips.firstOrNull { it.mac == selectedId } ?: strips.firstOrNull()

    fun load() {
        val id = selected?.mac ?: return
        scope.launch {
            try {
                summary = Api.powerSummary(Server.base, Server.token, id)
                history = Api.powerHistory(Server.base, Server.token, id, range)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "power failed"
            }
        }
    }

    LaunchedEffect(selected?.mac, range) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle(stringResource(R.string.power_title), stringResource(R.string.power_body)) }
        if (strips.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_strip_title), stringResource(R.string.power_empty), stringResource(R.string.refresh), onAction = { load() }) }
        } else {
            item {
                SegmentedButtonRow(
                    options = listOf("day", "7d", "30d", "90d"),
                    selected = range,
                    onSelected = { range = it },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.power_strip_select), style = MaterialTheme.typography.labelLarge)
                    strips.forEach { strip ->
                        FilledTonalButton(
                            onClick = { selectedId = strip.mac },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(strip.name.ifBlank { strip.mac }) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryMetric(stringResource(R.string.power_today), "${summary?.todayKwh ?: selected?.energyKwh ?: 0.0}", "kWh", Modifier.weight(1f))
                    SummaryMetric(stringResource(R.string.power_live), "${(summary?.liveWatts ?: selected?.powerW ?: 0.0).roundToInt()}", "W", Modifier.weight(1f))
                }
            }
            item { SummaryMetric(stringResource(R.string.power_cost), "${summary?.todayCost ?: 0.0}", summary?.currency ?: "USD", Modifier.fillMaxWidth()) }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.power_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val points = history?.points.orEmpty()
                        if (points.isEmpty()) {
                            Text(stringResource(R.string.power_no_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            points.takeLast(12).forEach { point ->
                                val watts = point.avgW.roundToInt()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(point.bucketStart.take(10), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    Box(Modifier.weight(2f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                        Box(Modifier.fillMaxWidth((watts / 2000f).coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    }
                                    Text("$watts W", Modifier.width(64.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
        }
    }
}

@Composable
private fun ActivityScreen(snapshot: Snapshot?) {
    val strips = snapshot?.strips.orEmpty()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch {
            try {
                events = Api.events(Server.base, Server.token, selectedId)
                error = null
            } catch (e: Exception) { error = e.message }
        }
    }
    LaunchedEffect(selectedId) { load() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle(stringResource(R.string.activity_title), stringResource(R.string.activity_body)) }
        item {
            SegmentedButtonRow(listOf("all") + strips.map { it.mac }, selectedId ?: "all") { selectedId = if (it == "all") null else it }
        }
        if (events.isEmpty()) item { EmptyState(stringResource(R.string.activity_empty_title), stringResource(R.string.activity_empty_body), stringResource(R.string.refresh), onAction = { load() }) }
        items(events, key = { it.id }) { event -> EventCard(event) }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
    }
}

@Composable
private fun NotificationsScreen() {
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch {
            try { notifications = Api.notifications(Server.base, Server.token); error = null } catch (e: Exception) { error = e.message }
        }
    }
    LaunchedEffect(Unit) { load() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle(stringResource(R.string.notifications_title), stringResource(R.string.notifications_body)) }
        if (notifications.isNotEmpty()) {
            item { FilledTonalButton(onClick = { scope.launch { Api.markAllNotificationsRead(Server.base, Server.token); load() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mark_all_read)) } }
        }
        if (notifications.isEmpty()) item { EmptyState(stringResource(R.string.notifications_empty_title), stringResource(R.string.notifications_empty_body), stringResource(R.string.refresh), onAction = { load() }) }
        items(notifications, key = { it.id }) { n ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        MetricPill(n.severity)
                    }
                    Text(n.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (n.readAt == null) FilledTonalButton(onClick = { scope.launch { Api.markNotificationRead(Server.base, Server.token, n.id); load() } }) { Text(stringResource(R.string.mark_read)) }
                        OutlinedButton(onClick = { scope.launch { Api.deleteNotification(Server.base, Server.token, n.id); load() } }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
    }
}

@Composable
private fun AutomationsScreen(snapshot: Snapshot?) {
    val strips = snapshot?.strips.orEmpty()
    var schedules by remember { mutableStateOf<List<ScheduleItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { try { schedules = Api.schedules(Server.base, Server.token); error = null } catch (e: Exception) { error = e.message } } }
    LaunchedEffect(Unit) { load() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle(stringResource(R.string.automations_title), stringResource(R.string.automations_body)) }
        item {
            if (strips.isNotEmpty()) {
                FilledTonalButton(
                    onClick = {
                        val first = strips.first()
                        scope.launch {
                            Api.saveSchedule(Server.base, Server.token, ScheduleItem("", "Evening lights", first.mac, null, "on", "18:00", listOf(0,1,2,3,4,5,6), true))
                            load()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.new_automation)) }
            }
        }
        if (schedules.isEmpty()) item { EmptyState(stringResource(R.string.automations_empty_title), stringResource(R.string.automations_empty_body), null, null) }
        items(schedules, key = { it.id }) { s ->
            val strip = strips.firstOrNull { it.mac == s.stripId }
            val target = s.relayIndex?.let { stringResource(R.string.outlet_n, it + 1) } ?: stringResource(R.string.all_outlets)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        MetricPill(if (s.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled))
                    }
                    Text("${strip?.name ?: s.stripId} ? $target ? ${s.action} ? ${s.time}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { scope.launch { Api.runScheduleNow(Server.base, Server.token, s.id) } }) { Text(stringResource(R.string.run_now)) }
                        OutlinedButton(onClick = { scope.launch { Api.saveSchedule(Server.base, Server.token, s.copy(enabled = !s.enabled)); load() } }) { Text(if (s.enabled) stringResource(R.string.disable) else stringResource(R.string.enable)) }
                        OutlinedButton(onClick = { scope.launch { Api.deleteSchedule(Server.base, Server.token, s.id); load() } }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
    }
}

@Composable
private fun ScreenTitle(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SegmentedButtonRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.take(5).forEach { option ->
            FilledTonalButton(onClick = { onSelected(option) }, modifier = Modifier.weight(1f)) { Text(option.take(8), maxLines = 1) }
        }
    }
}

@Composable
private fun EventCard(event: EventItem) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when (event.type) { "relay-on" -> "??"; "relay-off" -> "?"; "overload" -> "??"; "online" -> "??"; "offline" -> "??"; else -> "?" })
            Column(Modifier.weight(1f)) {
                Text(event.message, style = MaterialTheme.typography.bodyMedium)
                Text(listOfNotNull(event.stripName, event.createdAt.take(16)).joinToString(" ? "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MetricPill(event.type)
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScreen() {
    var serverUrl by remember { mutableStateOf(Server.serverUrl) }
    var token by remember { mutableStateOf(Server.token) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var provisionIp by remember { mutableStateOf(Server.host) }
    var provisionSsid by remember { mutableStateOf("") }
    var provisionPassword by remember { mutableStateOf("") }
    var provisionStatus by remember { mutableStateOf<String?>(null) }
    var provisionOk by remember { mutableStateOf(false) }
    var provisionBusy by remember { mutableStateOf(false) }
    var provisionStep by remember { mutableIntStateOf(0) }
    var boundStripNetwork by remember { mutableStateOf<android.net.Network?>(null) }
    var lang by remember { mutableStateOf(Lang.code) }
    var accentColor by remember { mutableStateOf(AppTheme.accentColor) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // --- Wi-Fi auto-fill state ---
    var stripNetworks by remember { mutableStateOf<List<WifiScanResult>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var selectedStrip by remember { mutableStateOf<String?>(null) }
    var connectingAp by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }
    var showStripDropdown by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    // Auto-fill current SSID when the screen opens (requires location permission)
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val ssid = WifiHelper.getCurrentSsid(context)
            if (ssid.isNotBlank() && !ssid.startsWith("TONLY_TAP_")) {
                provisionSsid = ssid
            }
        }
    }

    fun doScan() {
        scope.launch {
            scanning = true
            val found = WifiHelper.scanForStripNetworks(context)
            stripNetworks = found
            scanning = false
            if (found.isNotEmpty() && selectedStrip == null) {
                selectedStrip = found.first().ssid
            }
        }
    }

    fun commit() = Server.save(serverUrl, token)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.language_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val options = listOf(
                    null to stringResource(R.string.lang_system),
                    "en" to stringResource(R.string.lang_english),
                    "ar" to stringResource(R.string.lang_arabic),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = lang == value,
                            onClick = {
                                if (lang != value) {
                                    lang = value
                                    Lang.set(context, value)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        ) { Text(label) }
                    }
                }
            }
        }


        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.theme_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.theme_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val themeOptions = listOf(
                    AppTheme.SYSTEM to stringResource(R.string.theme_system),
                    AppTheme.LIGHT to stringResource(R.string.theme_light),
                    AppTheme.DARK to stringResource(R.string.theme_dark),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    themeOptions.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = AppTheme.mode == value,
                            onClick = { AppTheme.set(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                        ) { Text(label) }
                    }
                }

                Text(stringResource(R.string.theme_accent), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("#1B6B45", "#176F91", "#7C3AED", "#C2410C", "#BE123C").forEach { color ->
                        OutlinedButton(
                            onClick = {
                                accentColor = color
                                AppTheme.setAccent(color)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (accentColor.equals(color, ignoreCase = true)) "?" else " ")
                        }
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = accentColor,
                        onValueChange = {
                            accentColor = it
                            AppTheme.setAccent(it)
                        },
                        label = { Text(stringResource(R.string.theme_custom_color)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.server_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.server_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // technical values stay left-to-right even in RTL locales
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.field_server_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.field_token)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            commit()
                            status = context.getString(R.string.saved)
                            statusOk = true
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.save)) }
                    FilledTonalButton(
                        enabled = !busy,
                        onClick = {
                            commit()
                            busy = true
                            status = null
                            scope.launch {
                                try {
                                    val snap = Api.snapshot(Server.base, Server.token)
                                    status = context.getString(
                                        R.string.connected_summary,
                                        snap.strips.size,
                                        snap.strips.count { it.online },
                                    )
                                    statusOk = true
                                } catch (e: Exception) {
                                    status = e.message ?: "connection failed"
                                    statusOk = false
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.test)) }
                }
                if (busy) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                AnimatedVisibility(visible = status != null, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (statusOk) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (statusOk) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(status ?: "", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedButton(
                    onClick = {
                        commit()
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(Server.base),
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = Server.configured,
                ) { Text(stringResource(R.string.open_web_ui)) }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.provision_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.provision_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // --- Permission gate ---
                if (!hasLocationPermission) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.provision_need_location),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(onClick = {
                                permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            }) { Text(stringResource(R.string.grant_permission)) }
                        }
                    }
                }

                ProvisionWizardSteps(currentStep = provisionStep, busy = provisionBusy || connectingAp)

                if (provisionStep == 0) {
                    FilledTonalButton(
                        onClick = { doScan() },
                        enabled = hasLocationPermission && !scanning && !provisionBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.provision_step_1_action))
                    }
                    if (scanning) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                    if (stripNetworks.isNotEmpty() || selectedStrip != null) {
                        Box {
                            OutlinedButton(
                                onClick = { showStripDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !provisionBusy && !connectingAp,
                            ) {
                                Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    selectedStrip?.let { stringResource(R.string.provision_selected_strip, it) }
                                        ?: stringResource(R.string.provision_strips_found, stripNetworks.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            DropdownMenu(expanded = showStripDropdown, onDismissRequest = { showStripDropdown = false }) {
                                stripNetworks.forEach { net ->
                                    DropdownMenuItem(
                                        text = { Text("${net.ssid}  (${net.rssi} dBm)") },
                                        onClick = { selectedStrip = net.ssid; showStripDropdown = false },
                                    )
                                }
                            }
                        }
                    } else if (!scanning && hasLocationPermission) {
                        Text(stringResource(R.string.provision_no_strips), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        enabled = selectedStrip != null && !connectingAp,
                        onClick = {
                            val stripSsid = selectedStrip ?: return@Button
                            scope.launch {
                                connectingAp = true
                                provisionStatus = context.getString(R.string.provision_connecting_ap, stripSsid)
                                val network = WifiHelper.connectToStripAp(context, stripSsid)
                                connectingAp = false
                                if (network != null) {
                                    WifiHelper.bindProcessToNetwork(context, network)
                                    boundStripNetwork = network
                                    provisionStep = 1
                                    provisionStatus = context.getString(R.string.provision_step_1_done)
                                    provisionOk = true
                                } else {
                                    provisionStatus = context.getString(R.string.provision_step_1_failed, stripSsid)
                                    provisionOk = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.provision_step_1_button)) }
                }

                if (provisionStep == 1) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = provisionSsid,
                            onValueChange = { provisionSsid = it },
                            label = { Text(stringResource(R.string.field_wifi_ssid)) },
                            placeholder = {
                                val detected = WifiHelper.getCurrentSsid(context)
                                if (detected.isNotBlank() && !detected.startsWith("TONLY_TAP_")) Text(stringResource(R.string.provision_home_ssid_hint, detected), style = MaterialTheme.typography.bodySmall)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = provisionPassword,
                            onValueChange = { provisionPassword = it },
                            label = { Text(stringResource(R.string.field_wifi_password)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(
                        enabled = provisionPassword.isNotBlank(),
                        onClick = { provisionStep = 2; provisionStatus = context.getString(R.string.provision_step_2_done); provisionOk = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.provision_next)) }
                }

                if (provisionStep == 2) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it; provisionIp = it },
                            label = { Text(stringResource(R.string.field_server_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = provisionIp,
                            onValueChange = { provisionIp = it },
                            label = { Text(stringResource(R.string.field_server_ip_for_strip)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(
                        enabled = serverUrl.isNotBlank() && provisionIp.isNotBlank(),
                        onClick = { commit(); provisionStep = 3; provisionStatus = context.getString(R.string.provision_step_3_done); provisionOk = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.provision_next)) }
                }

                if (provisionStep == 3) {
                    Button(
                        enabled = Server.configured && !provisionBusy && !connectingAp,
                        onClick = {
                            commit()
                            provisionBusy = true
                            provisionStatus = context.getString(R.string.provision_step_4_running)
                            provisionOk = false
                            scope.launch {
                                val ssid = provisionSsid.trim().ifBlank { WifiHelper.getCurrentSsid(context) }
                                try {
                                    boundStripNetwork?.let { WifiHelper.bindProcessToNetwork(context, it) }
                                    Api.configureStripWifi(provisionIp.trim(), ssid, provisionPassword)
                                    WifiHelper.releaseStripNetwork(context)
                                    provisionStatus = context.getString(R.string.provision_waiting_registration)
                                    val result = Api.waitForRegistration(Server.base, Server.token)
                                    provisionStatus = result.message
                                    provisionOk = result.registered
                                    if (result.registered) provisionStep = 4
                                } catch (e: Exception) {
                                    provisionStatus = e.message ?: "provisioning failed"
                                    provisionOk = false
                                } finally {
                                    WifiHelper.releaseStripNetwork(context)
                                    provisionBusy = false
                                    connectingAp = false
                                    boundStripNetwork = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.provision_step_4_button)) }
                }

                if (provisionStep == 4) {
                    Button(
                        onClick = { provisionStep = 0; provisionStatus = null; provisionOk = false; selectedStrip = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.provision_start_over)) }
                }
                if (provisionBusy || connectingAp) CircularWavyProgressIndicator(Modifier.fillMaxWidth())
                AnimatedVisibility(visible = provisionStatus != null, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (provisionOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (provisionOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(provisionStatus ?: "", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SetupStep(1, stringResource(R.string.setup_step_1))
                SetupStep(2, stringResource(R.string.setup_step_2))
                SetupStep(3, stringResource(R.string.setup_step_3))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        "Tap 'Scan for strips', select your TONLY_TAP_* network, enter home Wi-Fi password, press Provision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 30.dp),
                    )
                }
                SetupStep(4, stringResource(R.string.setup_step_4))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(R.string.setup_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
private fun ProvisionWizardSteps(currentStep: Int, busy: Boolean) {
    val labels = listOf(
        stringResource(R.string.provision_wizard_1),
        stringResource(R.string.provision_wizard_2),
        stringResource(R.string.provision_wizard_3),
        stringResource(R.string.provision_wizard_4),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val done = currentStep > index
                val active = currentStep == index
                Surface(
                    shape = CircleShape,
                    color = when {
                        done -> MaterialTheme.colorScheme.primary
                        active -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = when {
                        done -> MaterialTheme.colorScheme.onPrimary
                        active -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (done) "?" else "${index + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    label + if (busy && active) "?" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
