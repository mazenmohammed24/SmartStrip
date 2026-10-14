package com.powerk.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powerk.app.R
import com.powerk.app.Strip
import com.powerk.app.outletOn

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StripCard(
    strip: Strip,
    showMac: Boolean,
    pending: Map<String, Boolean>,
    onCommand: (String, Int, Boolean) -> Unit,
    onIconChange: (String, Int?, String) -> Unit,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val stateOf: (Int) -> Boolean = { n -> pending["${strip.mac}:$n"] ?: strip.outletOn(n) }
    val anyOn = (1..4).any(stateOf)
    val allPending = (1..4).any { pending.containsKey("${strip.mac}:$it") }

    ElevatedCard(
        onClick = onTap ?: {},
        enabled = onTap != null,
        modifier = modifier.fillMaxWidth(),
    ) {
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
                    val title = strip.name.ifBlank { stringResource(R.string.strip_name) } +
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
                    strip.voltage?.let { MetricPill("${it.toInt()} V") }
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

@Composable
fun StatusDot(online: Boolean) {
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
fun MetricPill(value: String) {
    androidx.compose.material3.Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
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
