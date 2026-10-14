package com.powerk.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.R
import kotlin.math.roundToInt

private const val MAX_W = 2000f

@Composable
fun PowerRing(watts: Double, enabled: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val fraction = (watts / MAX_W).toFloat().coerceIn(0f, 1f)

    Canvas(modifier.size(80.dp)) {
        val stroke = 10.dp.toPx()
        val pad = stroke / 2f
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(color = surface, startAngle = -225f, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(pad, pad), size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        if (enabled && fraction > 0f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(primary.copy(0.6f), primary), center = Offset(size.width / 2, size.height / 2)),
                startAngle = -225f, sweepAngle = 270f * fraction, useCenter = false,
                topLeft = Offset(pad, pad), size = arc, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        val label = "${watts.roundToInt()}"
        val result = measurer.measure(label, TextStyle(fontSize = 15.sp, textAlign = TextAlign.Center, color = if (enabled) textColor else textColor.copy(0.4f)))
        drawText(result, topLeft = Offset((size.width - result.size.width) / 2f, (size.height - result.size.height) / 2f - 4.dp.toPx()))
    }
}
