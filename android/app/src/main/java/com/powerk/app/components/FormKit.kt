package com.powerk.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CenteredHeader(eyebrow: String, title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 46.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart)) { BackBtn(onBack) }
        Column(Modifier.padding(horizontal = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel(eyebrow)
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier, top: Dp = 14.dp) {
    Text(
        text.uppercase(),
        modifier.padding(top = top, bottom = 6.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FieldShell(modifier: Modifier, onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
fun <T> VDropdown(value: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier, mono: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second.orEmpty()
    Box(modifier) {
        FieldShell(Modifier.fillMaxWidth(), onClick = { open = true }) {
            Text(
                label, Modifier.weight(1f),
                fontFamily = if (mono) Mono else null,
                color = if (mono) scheme.primary else scheme.onSurface,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Rounded.ArrowDropDown, null, tint = scheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(
                    text = { Text(l, fontFamily = if (mono) Mono else null, fontWeight = if (v == value) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(v); open = false },
                )
            }
        }
    }
}

@Composable
fun VNumberField(value: String, onChange: (String) -> Unit, suffix: String?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val dismiss = rememberDismissKeyboard()
    FieldShell(modifier.alpha(if (enabled) 1f else 0.45f), null) {
        BasicTextField(
            value, onChange,
            enabled = enabled, singleLine = true,
            textStyle = TextStyle(fontFamily = Mono, fontSize = 16.sp, color = scheme.primary),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { dismiss() }),
            modifier = Modifier.weight(1f),
        )
        if (suffix != null) {
            Spacer(Modifier.width(8.dp))
            Text(suffix, fontFamily = Mono, fontSize = 14.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VTextInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dismiss = rememberDismissKeyboard()
    FieldShell(modifier.fillMaxWidth(), null) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = scheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { dismiss() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ClockField(time: String, onClick: () -> Unit, icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    FieldShell(Modifier.fillMaxWidth(), onClick) {
        Text(time, Modifier.weight(1f), fontFamily = Mono, fontSize = 16.sp, color = scheme.primary)
        Icon(icon, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
    }
}

@Composable
fun OptionRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainer, tween(140), label = "optbg")
    val edge by animateColorAsState(if (selected) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant, tween(140), label = "optedge")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, edge),
        modifier = Modifier.fillMaxWidth().tap(onClick = onClick),
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) scheme.primary else scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) scheme.onSurface else scheme.onSurfaceVariant)
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = scheme.primary)
        }
    }
}

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.18f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, null, Modifier.padding(top = 1.dp).size(18.dp), tint = scheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ToggleCard(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    VCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (desc != null) Text(desc, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
fun SaveButton(text: String = "Save", enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) scheme.primary else scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(54.dp).tap(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surface, tween(140), label = "chipbg")
    val edge by animateColorAsState(if (selected) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant, tween(140), label = "chipedge")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, edge),
        modifier = modifier.tap(onClick = onClick),
    ) {
        Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) scheme.primary else scheme.onSurface, maxLines = 1,
            )
        }
    }
}

@Composable
fun DayChips(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        names.forEachIndexed { i, d ->
            val on = i in selected
            val scheme = MaterialTheme.colorScheme
            val bg by animateColorAsState(if (on) scheme.primaryContainer else scheme.surfaceContainer, tween(140), label = "daybg")
            val edge by animateColorAsState(if (on) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant, tween(140), label = "dayedge")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bg,
                border = BorderStroke(1.dp, edge),
                modifier = Modifier.weight(1f).tap { onToggle(i) },
            ) {
                Box(Modifier.heightIn(min = 42.dp), contentAlignment = Alignment.Center) {
                    Text(d, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) scheme.primary else scheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
fun GreenFab(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.primary,
        shadowElevation = 6.dp,
        modifier = modifier.tap(onClick = onClick),
    ) {
        Row(Modifier.height(52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = scheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary, maxLines = 1)
        }
    }
}