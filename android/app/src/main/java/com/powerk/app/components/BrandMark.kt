package com.powerk.app.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.AppReducedMotion
import kotlin.math.PI
import kotlin.math.sin

/** Brand bolt path, identical geometry to the launcher icon foreground, fitted into [size]. */
fun brandBoltPath(size: Size): Path {
    val k = size.minDimension / 108f
    return Path().apply {
        moveTo(61f * k, 22f * k)
        lineTo(33f * k, 62f * k)
        lineTo(51f * k, 62f * k)
        lineTo(47f * k, 86f * k)
        lineTo(75f * k, 46f * k)
        lineTo(57f * k, 46f * k)
        close()
    }
}

/** The app mark as a launcher-style tile: bolt on an accent gradient, rounded square. */
@Composable
fun BrandTile(size: Dp = 80.dp, radius: Dp = 24.dp, boltFraction: Float = 0.575f, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(radius)
    Box(
        modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.22f), accent)))
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * boltFraction)) { drawPath(brandBoltPath(this.size), Color.White) }
    }
}

/**
 * The app icon with a radar pulse, shown while the phone searches for strip setup
 * access points (or while the strip connection is being established). Rings and the
 * breathing tile stop completely when reduced motion is on.
 */
@Composable
fun BrandScanRadar(scanning: Boolean, modifier: Modifier = Modifier, caption: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val reduced = AppReducedMotion
    val animate = scanning && !reduced
    val t = if (animate) {
        val radar = rememberInfiniteTransition(label = "radar")
        val v by radar.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "radarT")
        v
    } else {
        0f
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            if (animate) {
                Canvas(Modifier.fillMaxSize()) {
                    val maxR = size.minDimension * 0.5f
                    for (i in 0 until 3) {
                        val p = (t + i / 3f) % 1f
                        drawCircle(
                            color = accent.copy(alpha = (1f - p) * 0.32f),
                            radius = maxR * (0.30f + 0.70f * p),
                            style = Stroke(width = 1.6.dp.toPx()),
                        )
                    }
                }
            }
            val pulse = if (animate) 1f + 0.05f * sin(t * 2f * PI.toFloat()) else 1f
            BrandTile(
                size = 80.dp,
                radius = 24.dp,
                modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(6.dp))
            Text(caption, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
