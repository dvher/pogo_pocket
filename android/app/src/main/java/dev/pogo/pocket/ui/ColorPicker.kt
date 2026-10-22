package dev.pogo.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pogo.pocket.Palette

private fun hsvHex(h: Float, s: Float, v: Float) =
    "#%06x".format(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) and 0xffffff)

private fun argbHsv(argb: Int) = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }

private fun hexHsv(hex: String) = argbHsv(android.graphics.Color.parseColor(hex))

private fun normalize(t: String) = t.trim().lowercase().let { if (it.startsWith("#")) it else "#$it" }

// Calls onMove with the pointer position (clamped to the element) on press
// and while dragging.
private suspend fun PointerInputScope.track(onMove: (Offset, IntSize) -> Unit) = awaitEachGesture {
    val down = awaitFirstDown()
    fun report(p: Offset) = onMove(Offset(p.x.coerceIn(0f, size.width.toFloat()), p.y.coerceIn(0f, size.height.toFloat())), size)
    report(down.position)
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        report(change.position)
        change.consume()
    }
}

// Picks any "#rrggbb" note color: a saturation/brightness square, a hue
// bar and a hex field, with a preview of the note's text on it.
@Composable
fun ColorPickerDialog(initial: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val start = remember { argbHsv(Palette.of(initial).bg.toArgb()) }
    var h by remember { mutableStateOf(start[0]) }
    var s by remember { mutableStateOf(start[1]) }
    var v by remember { mutableStateOf(start[2]) }
    var typed by remember { mutableStateOf<String?>(null) }
    // A valid typed value is used as is; HSV round trips can be off by one.
    val hex = typed?.let(::normalize)?.takeIf(Palette::isCustom) ?: hsvHex(h, s, v)
    val preview = Palette.of(hex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color.White, Color.hsv(h, 1f, 1f))))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        .pointerInput(Unit) {
                            track { p, size ->
                                s = p.x / size.width
                                v = 1f - p.y / size.height
                                typed = null
                            }
                        },
                ) {
                    Knob(maxWidth * s, maxHeight * (1 - v))
                }
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp))
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                        .pointerInput(Unit) {
                            track { p, size ->
                                h = (p.x / size.width * 360f).coerceIn(0f, 359.9f)
                                typed = null
                            }
                        },
                ) {
                    Knob(maxWidth * (h / 360f), maxHeight / 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = typed ?: hex,
                        label = { Text("Hex") },
                        onValueChange = { t ->
                            typed = t
                            val norm = normalize(t)
                            if (Palette.isCustom(norm)) hexHsv(norm).let { h = it[0]; s = it[1]; v = it[2] }
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(preview.bg)
                            .border(1.dp, preview.accent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", color = preview.ink) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(hex) }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Knob(x: Dp, y: Dp) {
    Box(
        Modifier.offset(x - 9.dp, y - 9.dp).size(18.dp).border(3.dp, Color.White, CircleShape)
            .border(4.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
    )
}
