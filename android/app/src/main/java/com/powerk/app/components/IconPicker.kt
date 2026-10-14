package com.powerk.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.powerk.app.R

private val icons = listOf(
    "plug" to "🔌",
    "lamp" to "💡",
    "fridge" to "❄️",
    "charger" to "🔋",
    "tv" to "📺",
    "pc" to "🖥️",
    "fan" to "🌀",
    "washer" to "🫧",
    "coffee" to "☕",
    "printer" to "🖨️",
    "router" to "📡",
    "speaker" to "🔊",
    "power-strip" to "⚡",
    "kettle" to "🫖",
)

fun iconGlyph(icon: String): String = icons.firstOrNull { it.first == icon }?.second ?: "🔌"

@Composable
fun IconPickerMenu(
    expanded: Boolean,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        icons.chunked(4).forEach { row ->
            androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                row.forEach { (key, glyph) ->
                    DropdownMenuItem(
                        text = { Text(glyph, style = MaterialTheme.typography.headlineSmall) },
                        onClick = { onSelect(key) },
                        modifier = if (selected == key) Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp),
                        ) else Modifier,
                    )
                }
            }
        }
    }
}

/** Small toggle knob drawn with Canvas, used inside OutletTile. */
@Composable
fun MiniSwitch(progress: Float, onColor: Color) {
    val trackColor = onColor.copy(alpha = 0.3f + 0.4f * progress)
    val knobColor = onColor
    Box(
        Modifier.size(width = 44.dp, height = 26.dp).drawBehind {
            val w = size.width; val h = size.height
            val r = h / 2f
            drawRoundRect(color = trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(width = h, cap = StrokeCap.Round))
            drawCircle(color = knobColor, radius = r - 4.dp.toPx(), center = Offset(r + (w - h) * progress, h / 2f))
        }
    )
}
