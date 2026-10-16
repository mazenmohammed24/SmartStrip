package com.powerk.app.screens

import com.powerk.app.components.TilesStripCard

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.draw.clip

import androidx.compose.material.icons.rounded.ViewAgenda

import androidx.compose.material.icons.rounded.Dashboard

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyItemScope

import androidx.compose.foundation.layout.offset

import android.content.Context

import androidx.compose.animation.togetherWith

import androidx.compose.animation.slideOutHorizontally

import androidx.compose.animation.slideInHorizontally

import androidx.compose.animation.scaleOut

import androidx.compose.animation.scaleIn

import androidx.compose.animation.fadeOut

import androidx.compose.animation.fadeIn

import androidx.compose.animation.core.spring

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.animateColorAsState

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.AnimatedContent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.text.style.TextAlign
import com.powerk.app.components.SectionLabel
import com.powerk.app.components.BackBtn
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.unit.sp
import com.powerk.app.Link
import com.powerk.app.Server
import com.powerk.app.Strip
import com.powerk.app.StripsViewModel
import com.powerk.app.components.EmptyState
import com.powerk.app.components.IconBox
import com.powerk.app.components.LiveBadge
import com.powerk.app.components.MessageCard
import com.powerk.app.components.Mono
import com.powerk.app.components.ScreenHeader
import com.powerk.app.components.Shimmer
import com.powerk.app.components.StripCard
import com.powerk.app.components.StripRow
import com.powerk.app.components.VCard
import com.powerk.app.components.fmtKwh
import com.powerk.app.components.fmtW
import com.powerk.app.components.rememberDismissKeyboard
import com.powerk.app.components.tap

@Composable
fun HomeScreen(
    vm: StripsViewModel,
    onOpenStrip: (String) -> Unit,
    onTab: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("powerk", Context.MODE_PRIVATE) }
    var tiles by rememberSaveable { mutableStateOf(prefs.getBoolean("home_tiles", false)) }
    var query by rememberSaveable { mutableStateOf("") }
    var gridView by rememberSaveable { mutableStateOf(true) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val dismiss = rememberDismissKeyboard()
    val strips = vm.strips
    val visible by remember(strips, query) {
        derivedStateOf {
            val q = query.trim().lowercase()
            if (q.isEmpty()) strips else strips.filter { s ->
                s.name.lowercase().contains(q) || s.mac.lowercase().contains(q) || s.outlets.any { it.name.lowercase().contains(q) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Control center")
                    Text("My strips", fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(Server.host, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LayoutToggle(tiles) { tiles = it; prefs.edit().putBoolean("home_tiles", it).apply() }
                Spacer(Modifier.width(8.dp))
                HeaderButton(Icons.Rounded.Refresh, "Refresh") { vm.refresh() }
                if (!tiles) {
                    Spacer(Modifier.width(8.dp))
                    HeaderButton(Icons.Rounded.Add, "Add strip", primary = true) { showAdd = true }
                }
            }
            AnimatedContent(
                targetState = tiles,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = 0.94f) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 10 * dir })
                        .togetherWith(fadeOut(tween(140)) + scaleOut(tween(220), targetScale = 0.96f) + slideOutHorizontally(tween(220)) { -it / 12 * dir })
                },
                label = "homeLayout",
            ) { isTiles ->
                val state = rememberLazyListState()
                LaunchedEffect(state.isScrollInProgress) { if (state.isScrollInProgress) dismiss() }
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = if (isTiles) 168.dp else 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!isTiles) {
                        item(key = "total") { TotalCard(vm, strips, vm.link) }
                        item(key = "links") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickLink(Icons.Rounded.BarChart, "Usage", Modifier.weight(1f)) { onTab("energy") }
                                QuickLink(Icons.Rounded.AccessTime, "Timers", Modifier.weight(1f)) { onTab("automations") }
                                QuickLink(Icons.Rounded.AutoMode, "Rules", Modifier.weight(1f)) { onTab("rules") }
                                QuickLink(Icons.Rounded.MeetingRoom, "Rooms", Modifier.weight(1f)) { onTab("rooms") }
                            }
                        }
                    }
                    if (strips.size > 1 || query.isNotEmpty()) {
                        item(key = "search") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchField(query, { query = it }, dismiss, Modifier.weight(1f))
                                if (!isTiles) ViewToggle(gridView) { gridView = it }
                            }
                        }
                    }
                    stripBody(vm, strips, visible, query, { showAdd = true }) { strip ->
                        Box(Modifier.animateItem()) {
                            when {
                                isTiles -> TilesStripCard(strip, vm, { onOpenStrip(strip.mac) })
                                gridView -> StripCard(strip, vm, { onOpenStrip(strip.mac) })
                                else -> StripRow(strip, vm, { onOpenStrip(strip.mac) })
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            tiles,
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
            enter = fadeIn(tween(200)) + scaleIn(spring(0.6f, 500f), initialScale = 0.6f),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.7f),
        ) {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add strip", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
    if (showAdd) AddStripSheet(vm) { showAdd = false }
}

private fun LazyListScope.stripBody(
    vm: StripsViewModel,
    strips: List<Strip>,
    visible: List<Strip>,
    query: String,
    onAdd: () -> Unit,
    card: @Composable LazyItemScope.(Strip) -> Unit,
) {
    when {
        !vm.loaded && vm.link != Link.OFFLINE -> items(2, key = { "sk$it" }) { Shimmer(Modifier.fillMaxWidth().height(200.dp)) }
        !vm.loaded -> item(key = "offline") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MessageCard("Can't reach ${Server.host}. Retrying automatically.", true, Icons.Rounded.Warning)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().tap { vm.restart() }) {
                    Text("Retry now", Modifier.padding(12.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        strips.isEmpty() -> item(key = "empty") { EmptyState("No strips yet", "Add a strip and it shows up here instantly.", "Add strip", onAdd) }
        visible.isEmpty() -> item(key = "nomatch") { EmptyState("No match", "Nothing matches \"$query\".") }
        else -> items(visible, key = { it.mac }) { strip -> this.card(strip) }
    }
}

@Composable
private fun LayoutToggle(tiles: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val slot = 36.dp
    val x by animateDpAsState(if (tiles) slot else 0.dp, spring(0.7f, 700f), label = "toggleX")
    Surface(shape = CircleShape, color = scheme.surface, border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Box(Modifier.padding(2.dp)) {
            Box(Modifier.offset(x = x).size(slot, 34.dp).background(scheme.primary, CircleShape))
            Row {
                listOf(false to Icons.Rounded.Dashboard, true to Icons.Rounded.GridView).forEach { (mode, icon) ->
                    val tint by animateColorAsState(if (tiles == mode) scheme.onPrimary else scheme.onSurfaceVariant, tween(160), label = "toggleTint")
                    Box(Modifier.size(slot, 34.dp).clip(CircleShape).tap { if (tiles != mode) onChange(mode) }, contentAlignment = Alignment.Center) {
                        Icon(icon, if (mode) "Tiles layout" else "Compact layout", Modifier.size(18.dp), tint = tint)
                    }
                }
            }
        }
    }
}
@Composable
private fun HeaderButton(icon: ImageVector, label: String, primary: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (primary) scheme.primary else scheme.surface,
        border = if (primary) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.size(38.dp).tap(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(20.dp), tint = if (primary) scheme.onPrimary else scheme.onSurface) }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, dismiss: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.surface, border = BorderStroke(1.dp, scheme.outlineVariant), modifier = modifier.height(40.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { dismiss() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text("Search strips or outlets", fontSize = 14.sp, color = scheme.onSurfaceVariant)
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                Icon(Icons.Rounded.Close, "Clear", Modifier.size(18.dp).tap { onChange(""); dismiss() }, tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TotalCard(vm: StripsViewModel, strips: List<Strip>, link: Link) {
    val scheme = MaterialTheme.colorScheme
    val watts by animateFloatAsState(strips.sumOf { it.powerW }.toFloat(), tween(450), label = "watts")
    val online = strips.count { it.online }
    val outlets = strips.sumOf { it.outlets.size }
    val on = strips.sumOf { s -> s.outlets.count { it.on } }
    VCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total power right now", Modifier.weight(1f), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            LiveBadge(link)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val kw = watts >= 1000
            Text(
                if (kw) String.format(java.util.Locale.US, "%.2f", watts / 1000) else String.format(java.util.Locale.US, "%.0f", watts),
                fontFamily = Mono, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary, maxLines = 1,
            )
            Text(if (kw) " kW" else " W", Modifier.padding(bottom = 5.dp), fontFamily = Mono, fontSize = 13.sp, color = scheme.primary)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = scheme.errorContainer,
                modifier = Modifier.alpha(if (on > 0) 1f else 0.4f).tap(enabled = on > 0) {
                    strips.filter { it.online }.forEach { vm.command(it.mac, 0, false) }
                },
            ) { Text("All off", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.error) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalStat("$online/${strips.size}", "strips online", Modifier.weight(1f))
            TotalStat("$on/$outlets", "outlets on", Modifier.weight(1f))
            TotalStat(fmtKwh(strips.sumOf { it.energyKwh }), "kWh total", Modifier.weight(1f))
        }
    }
}

@Composable
private fun TotalStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun QuickLink(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    VCard(modifier, onClick = onClick, padding = 8.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBox(size = 30.dp) { Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary) }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun ViewToggle(grid: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.tap { onChange(!grid) },
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.ViewAgenda, "Switch layout", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}