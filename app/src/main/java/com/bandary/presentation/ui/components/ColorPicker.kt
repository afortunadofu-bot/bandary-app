package com.bandary.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** 自訂顏色調色盤對話框：2D 飽和度/明度面板 + 色相條，回傳 #RRGGBB。 */
@Composable
fun ColorPickerDialog(
    initialHex: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val hsv = remember {
        val argb = runCatching { android.graphics.Color.parseColor(initialHex ?: "#6B9080") }
            .getOrDefault(android.graphics.Color.parseColor("#6B9080"))
        FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val current = Color.hsv(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自訂顏色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(current)
                )
                SvPanel(hue = hue, onChange = { s, v -> sat = s; value = v })
                HueBar(onChange = { hue = it })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current.toHex()) }) { Text("加入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SvPanel(hue: Float, onChange: (sat: Float, value: Float) -> Unit) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxWidth().height(160.dp)
            .clip(RoundedCornerShape(10.dp))
            .onSizeChanged { sizePx = it }
            .background(Color.hsv(hue, 1f, 1f))
            .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInputTapDrag { x, y ->
                if (sizePx.width > 0 && sizePx.height > 0) {
                    onChange((x / sizePx.width).coerceIn(0f, 1f), (1f - y / sizePx.height).coerceIn(0f, 1f))
                }
            }
    )
}

@Composable
private fun HueBar(onChange: (Float) -> Unit) {
    var w by remember { mutableStateOf(0) }
    val hueColors = remember { (0..360 step 60).map { Color.hsv(it.toFloat().coerceAtMost(359f), 1f, 1f) } }
    Box(
        modifier = Modifier
            .fillMaxWidth().height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { w = it.width }
            .background(Brush.horizontalGradient(hueColors))
            .pointerInputTapDrag { x, _ -> if (w > 0) onChange((x / w).coerceIn(0f, 1f) * 360f) }
    )
}

/** 同時處理點擊與拖曳，回傳 panel 內座標。 */
private fun Modifier.pointerInputTapDrag(onPos: (x: Float, y: Float) -> Unit): Modifier =
    this
        .pointerInput(Unit) { detectTapGestures { off -> onPos(off.x, off.y) } }
        .pointerInput(Unit) { detectDragGestures { change, _ -> onPos(change.position.x, change.position.y) } }

private fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())
