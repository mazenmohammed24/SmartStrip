package com.powerk.app.screens

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.SharedPreferences
import com.powerk.app.Api
import com.powerk.app.AppSettings
import com.powerk.app.AppTheme
import com.powerk.app.Lang
import com.powerk.app.Link
import com.powerk.app.R
import com.powerk.app.Diag
import com.powerk.app.Server
import com.powerk.app.findActivity
import com.powerk.app.StripsViewModel
import com.powerk.app.components.LiveBadge
import com.powerk.app.components.Mono
import com.powerk.app.components.ScreenHeader
import com.powerk.app.components.SectionLabel
import com.powerk.app.components.VTextField
import com.powerk.app.components.rememberDismissKeyboard
import com.powerk.app.components.tap
import kotlinx.coroutines.launch




private val ACCENTS = listOf("#0f766e", "#16803c", "#176f91", "#7c3aed", "#c2410c", "#be123c")


@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(title, Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) { Column { content() } }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 12.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)))
}

@Composable
private fun Row2(label: String, hint: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun MiniSeg(options: List<Pair<String?, String>>, value: String?, onChange: (String?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(11.dp), color = scheme.surfaceContainer, border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Row(Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { (v, label) ->
                val on = v == value
                val bg by animateColorAsState(if (on) scheme.primary else Color.Transparent, tween(140), label = "miniSeg")
                val fg by animateColorAsState(if (on) scheme.onPrimary else scheme.onSurfaceVariant, tween(140), label = "miniSegFg")
                Box(Modifier.background(bg, RoundedCornerShape(9.dp)).tap { onChange(v) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun NumField(value: String, suffix: String?, keyboard: KeyboardType = KeyboardType.Decimal, width: Int = 80, onCommit: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember(value) { mutableStateOf(value) }
    val dismiss = rememberDismissKeyboard()
    fun commit() { if (text != value && text.isNotBlank()) onCommit(text.trim()) else text = value }
    Surface(shape = RoundedCornerShape(10.dp), color = scheme.surfaceContainer, border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = Mono, color = scheme.onSurface, textAlign = TextAlign.End),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(); dismiss() }),
                modifier = Modifier.width(width.dp).onFocusChanged { if (!it.isFocused) commit() },
            )
            if (suffix != null) Text(" $suffix", fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(vm: StripsViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val settings = vm.settings
    var serverUrl by remember { mutableStateOf(Server.serverUrl) }
    var token by remember { mutableStateOf(Server.token) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(Lang.code) }
    var showAdd by remember { mutableStateOf(false) }

    fun update(next: (AppSettings) -> AppSettings) { settings?.let { vm.saveSettings(next(it)) } }
    fun commit() { Server.save(serverUrl, token); vm.restart() }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
        ScreenHeader(stringResource(R.string.settings_title), eyebrow = "Preferences")
        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Group(stringResource(R.string.theme_title)) {
                Row2(stringResource(R.string.theme_title)) {
                    MiniSeg(
                        listOf(AppTheme.SYSTEM to stringResource(R.string.theme_system), AppTheme.LIGHT to stringResource(R.string.theme_light), AppTheme.DARK to stringResource(R.string.theme_dark)),
                        AppTheme.mode,
                    ) { v -> v?.let { AppTheme.set(it); update { s -> s.copy(theme = it) } } }
                }
                Divider()
                Row2(stringResource(R.string.language_title)) {
                    MiniSeg(
                        listOf(null to stringResource(R.string.lang_system), "en" to "EN", "ar" to "ع"),
                        lang,
                    ) { v -> lang = v; Lang.set(context, v) }
                }
                Divider()
                Row2("Time format") {
                    MiniSeg(listOf("24h" to "24h", "12h" to "12h"), settings?.timeFormat) { v -> v?.let { update { s -> s.copy(timeFormat = it) } } }
                }
                Divider()
                Row2(stringResource(R.string.theme_accent), "Synced with the web app") {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        ACCENTS.forEach { c ->
                            val on = AppTheme.accentColor.equals(c, true)
                            val color = Color(0xFF000000 or c.removePrefix("#").toLong(16))
                            Box(
                                Modifier.size(22.dp).background(color, CircleShape).then(if (on) Modifier.border(2.dp, scheme.onSurface, CircleShape) else Modifier)
                                    .tap { AppTheme.setAccent(c); update { s -> s.copy(accentColor = c) } },
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = Color.White) }
                        }
                    }
                }
                Divider()
                Row2(stringResource(R.string.theme_custom_color), "Pick any color") {
                    var showPicker by remember { mutableStateOf(false) }
                    val colorPreview = remember(AppTheme.accentColor) {
                        Color(0xFF000000 or (AppTheme.accentColor.removePrefix("#").toLongOrNull(16) ?: 0x1AA34A))
                    }
                    Box(
                        Modifier.size(30.dp).background(colorPreview, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .tap { showPicker = true },
                    )
                    Text(AppTheme.accentColor, fontSize = 12.sp, fontFamily = Mono, color = scheme.onSurfaceVariant)
                    if (showPicker) {
                        ColorPickerDialog(
                            initialHex = AppTheme.accentColor,
                            onDismiss = { showPicker = false },
                            onPick = { hex -> AppTheme.setAccent(hex); update { s -> s.copy(accentColor = hex) }; showPicker = false },
                        )
                    }
                }
            }

            Group("Energy & tariff") {
                Row2("Tariff", if (settings?.tariffMode == "egypt") "Egypt tiered residential prices" else "One flat price per kWh") {
                    MiniSeg(listOf("egypt" to "Egypt", "flat" to "Flat"), settings?.tariffMode) { v -> v?.let { update { s -> s.copy(tariffMode = it) } } }
                }
                if (settings?.tariffMode == "flat") {
                    Divider()
                    Row2("Rate per kWh") {
                        NumField(settings.ratePerKwh.toString(), settings.currency) { v -> v.toDoubleOrNull()?.takeIf { it >= 0 }?.let { r -> update { s -> s.copy(ratePerKwh = r) } } }
                    }
                }
                Divider()
                Row2("Energy unit") {
                    MiniSeg(listOf("kWh" to "kWh", "Wh" to "Wh"), settings?.energyUnit) { v -> v?.let { update { s -> s.copy(energyUnit = it) } } }
                }
                Divider()
                Row2("Currency") {
                    NumField(settings?.currency.orEmpty(), null, KeyboardType.Ascii, 44) { v ->
                        if (v.length == 3) update { s -> s.copy(currency = v.uppercase()) }
                    }
                }
                Divider()
                Row2("Overload threshold", "Warn above this load per strip") {
                    NumField(settings?.overloadThresholdWatts?.toString().orEmpty(), "W", KeyboardType.Number, 52) { v ->
                        v.toIntOrNull()?.takeIf { it >= 100 }?.let { w -> update { s -> s.copy(overloadThresholdWatts = w) } }
                    }
                }
            }

            Group("Devices") {
                Row2("Add a strip", "Find on network or set up a new one", onClick = { showAdd = true }) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                }
                Divider()
                Row2("Rooms", "Group strips by room", onClick = { onNavigate("rooms") }) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                }
            }

            Group(stringResource(R.string.server_title)) {
                Row2("Connection") { LiveBadge(vm.link) }
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        VTextField(serverUrl, { serverUrl = it }, stringResource(R.string.field_server_url))
                        VTextField(token, { token = it }, stringResource(R.string.field_token))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { commit(); status = context.getString(R.string.saved); statusOk = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save), fontSize = 13.sp) }
                        FilledTonalButton(
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                commit(); busy = true; status = null
                                scope.launch {
                                    try {
                                        val list = Api.snapshot(Server.base, Server.token)
                                        status = context.getString(R.string.connected_summary, list.size, list.count { it.online })
                                        statusOk = true
                                    } catch (e: Exception) { status = e.message ?: "connection failed"; statusOk = false }
                                    busy = false
                                }
                            },
                        ) { Text(if (busy) "…" else stringResource(R.string.test), fontSize = 13.sp) }
                    }
                    AnimatedVisibility(visible = status != null, enter = fadeIn(), exit = fadeOut()) {
                        Surface(shape = MaterialTheme.shapes.medium, color = if (statusOk) scheme.primaryContainer else scheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(status ?: "", Modifier.padding(10.dp), fontSize = 12.sp, color = if (statusOk) scheme.onPrimaryContainer else scheme.onErrorContainer)
                        }
                    }
                    OutlinedButton(
                        onClick = { commit(); context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Server.base))) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = Server.configured,
                    ) { Text(stringResource(R.string.open_web_ui), fontSize = 13.sp) }
                }
            }

            Group("Diagnostics") {
                Row2("Screenshots", "Attach screen captures to diagnostics sent to your server") {
                    Switch(Diag.screenshots, { Diag.toggleScreenshots(it) })
                }
            }

            Group("About") {
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—" }
                Row2("SmartStrip", "Local control for MTTL-W01 strips, no cloud") {
                    Text(version, fontFamily = Mono, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Divider()
                Row2("Server") { Text(Server.host, Modifier.widthIn(max = 200.dp), fontFamily = Mono, fontSize = 11.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    if (showAdd) AddStripSheet(vm) { showAdd = false }
}
