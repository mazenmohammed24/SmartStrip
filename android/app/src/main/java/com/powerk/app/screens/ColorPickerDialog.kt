package com.powerk.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.powerk.app.components.Mono

@Composable
fun ColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val initial = remember(initialHex) {
        val hsv = FloatArray(3)
        val rgb = initialHex.removePrefix("#").toLongOrNull(16)?.toInt() ?: 0x1AA34A
        android.graphics.Color.colorToHSV(0xFF000000.toInt() or rgb, hsv)
        hsv
    }
    var hue by remember { mutableStateOf(initial[0]) }
    var sat by remember { mutableStateOf(initial[1]) }
    var v by remember { mutableStateOf(initial[2]) }
    val color = Color.hsv(hue, sat, v, alpha = 1f)
    val hex = "#%02X%02X%02X".format((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    fun pick(p: Offset) {
        if (canvasSize.width <= 0f || canvasSize.height <= 0f) return
        sat = (p.x / canvasSize.width).coerceIn(0f, 1f)
        v = (1f - (p.y / canvasSize.height)).coerceIn(0f, 1f)
    }
    Dialog(onDismissRequest = onDismiss) {
        val shown = remember { MutableTransitionState(false) }
        LaunchedEffect(Unit) { shown.targetState = true }
        AnimatedVisibility(
            visibleState = shown,
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f) + fadeIn(tween(150)),
            exit = scaleOut(tween(130), targetScale = 0.9f) + fadeOut(tween(120)),
        ) {
            val scheme = MaterialTheme.colorScheme
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.9f)),
                shadowElevation = 18.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pick a color", fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
                            .pointerInput(Unit) { detectTapGestures { pick(it) } }
                            .pointerInput(Unit) { detectDragGestures { change, _ -> pick(change.position) } },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            canvasSize = this.size
                            val hueColor = Color.hsv(hue, 1f, 1f, 1f)
                            drawRect(Brush.linearGradient(listOf(Color.White, hueColor), Offset(0f, 0f), Offset(size.width, 0f)))
                            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.Black), Offset(0f, 0f), Offset(0f, size.height)))
                            val cx = sat * size.width
                            val cy = (1f - v) * size.height
                            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
                            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 9.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
                        }
                    }
                    Text("Hue", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .pointerInput(Unit) { detectTapGestures { hue = (it.x / size.width * 360f).coerceIn(0f, 360f) } }
                            .pointerInput(Unit) { detectDragGestures { change, _ -> hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f) } },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Brush.horizontalGradient(List(13) { i -> Color.hsv(i * 30f, 1f, 1f, 1f) }))
                            val cx = (hue / 360f * size.width).coerceIn(11.dp.toPx(), size.width - 11.dp.toPx())
                            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, size.height / 2), style = Stroke(width = 3.dp.toPx()))
                            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 10.5.dp.toPx(), center = Offset(cx, size.height / 2), style = Stroke(width = 1.dp.toPx()))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(1.dp, scheme.outlineVariant, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(hex, fontSize = 13.sp, fontFamily = Mono, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Cancel", color = scheme.onSurfaceVariant) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onPick(hex) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = color,
                                contentColor = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            ),
                        ) { Text("Apply", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}
