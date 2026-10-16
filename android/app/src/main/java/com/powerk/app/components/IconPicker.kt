package com.powerk.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

fun iconGlyph(icon: String): String = DEVICE_ICONS.firstOrNull { it.id == icon }?.emoji ?: "🔌"

@Composable
fun IconPickerMenu(
    expanded: Boolean,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (!expanded) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(14.dp)) {
                Text("Choose icon", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                IconGrid(selected, onSelect, Modifier.padding(top = 8.dp), maxHeight = 420.dp)
            }
        }
    }
}

@Composable
fun IconGrid(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, maxHeight: Dp = 300.dp) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val groups = remember(q) {
        DEVICE_ICONS.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.group.lowercase().contains(q) }.groupBy { it.group }
    }
    Column(modifier) {
        VTextField(query, { query = it }, "Search ${DEVICE_ICONS.size} icons")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(64.dp),
            modifier = Modifier.padding(top = 8.dp).heightIn(max = maxHeight),
            contentPadding = PaddingValues(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (groups.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No icons match", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
            }
            groups.forEach { (group, icons) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "g-$group") { SectionLabel(group, Modifier.padding(top = 6.dp)) }
                items(icons, key = { it.id }) { icon ->
                    val on = icon.id == selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onSelect(icon.id) }
                            .padding(vertical = 7.dp, horizontal = 2.dp),
                    ) {
                        Text(icon.emoji, fontSize = 20.sp)
                        Text(
                            icon.label,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            drawRoundRect(color = trackColor, cornerRadius = CornerRadius(r), style = Stroke(width = h, cap = StrokeCap.Round))
            drawCircle(color = knobColor, radius = r - 4.dp.toPx(), center = Offset(r + (w - h) * progress, h / 2f))
        }
    )
}