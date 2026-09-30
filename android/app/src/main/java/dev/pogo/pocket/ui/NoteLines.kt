package dev.pogo.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pogo.pocket.Line

/**
 * Draws a note the way the widget does, from the core's line model.
 * onToggle is called with a task's source line; null makes tasks read-only.
 */
@Composable
fun NoteLines(
    lines: List<Line>,
    ink: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onToggle: ((Int) -> Unit)? = null,
) {
    val base = if (compact) 13.sp else 16.sp
    Column(modifier) {
        for (line in lines) {
            val indent = Modifier.padding(start = (line.indent * 16).dp)
            when (line.kind) {
                "task" -> Row(
                    indent.fillMaxWidth().let { m -> onToggle?.let { m.clickable { it(line.line) } } ?: m },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = line.checked,
                        onCheckedChange = onToggle?.let { { _: Boolean -> it(line.line) } },
                        modifier = Modifier.size(if (compact) 28.dp else 40.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = accent, uncheckedColor = ink.copy(alpha = 0.7f), checkmarkColor = Color.White,
                        ),
                    )
                    Text(
                        line.text,
                        color = if (line.checked) ink.copy(alpha = 0.5f) else ink,
                        fontSize = base,
                        textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                    )
                }
                "gap" -> Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                "rule" -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(ink.copy(alpha = 0.25f)),
                )
                "bullet" -> Row(indent.padding(vertical = 2.dp)) {
                    Text(line.marker, color = accent, fontSize = base)
                    Spacer(Modifier.width(8.dp))
                    Text(line.text, color = ink, fontSize = base)
                }
                else -> Text(
                    line.text,
                    modifier = Modifier.padding(vertical = 2.dp),
                    style = styleFor(line.kind, ink, base.value),
                    maxLines = if (compact) 3 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun styleFor(kind: String, ink: Color, base: Float) = when (kind) {
    "h1" -> TextStyle(color = ink, fontSize = (base + 6).sp, fontWeight = FontWeight.Bold)
    "h2" -> TextStyle(color = ink, fontSize = (base + 2).sp, fontWeight = FontWeight.Bold)
    "quote" -> TextStyle(color = ink.copy(alpha = 0.75f), fontSize = base.sp, fontStyle = FontStyle.Italic)
    "code" -> TextStyle(color = ink, fontSize = (base - 1).sp, fontFamily = FontFamily.Monospace)
    else -> TextStyle(color = ink, fontSize = base.sp)
}
