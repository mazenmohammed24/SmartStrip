package com.powerk.app.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powerk.app.Outlet
import com.powerk.app.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OutletTile(
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
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.7f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
