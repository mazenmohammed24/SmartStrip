package com.powerk.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    fun pick(p: Offset) {
        if (size.width <= 0f || size.height <= 0f) return
        sat = (p.x / size.width).coerceIn(0f, 1f)
        v = (1f - (p.y / size.height)).coerceIn(0f, 1f)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(Unit) { detectTapGestures { pick(it) } }
                        .pointerInput(Unit) { detectDragGestures { change, _ -> pick(change.position) } },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        size = this.size
                        val hueColor = Color.hsv(hue, 1f, 1f, 1f)
                        drawRect(Brush.linearGradient(listOf(Color.White, hueColor), Offset(0f, 0f), Offset(size.width, 0f)))
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.Black), Offset(0f, 0f), Offset(0f, size.height)))
                        drawCircle(Color.White, radius = 8f, center = Offset(sat * size.width, (1f - v) * size.height), style = Stroke(width = 3f))
                    }
                }
                Text("Hue", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(hue, { hue = it }, valueRange = 0f..360f, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "#%02X%02X%02X".format((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()),
                        fontSize = 12.sp,
                        fontFamily = Mono,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick("#%02X%02X%02X".format((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}