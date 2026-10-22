package dev.pogo.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pogo.pocket.core.pogocore.Pogocore
import kotlinx.coroutines.launch

// One-tap Markdown above the keyboard. Each button turns the current value
// into a new one through MarkdownEdit and hands it to onChange.
@Composable
fun MarkdownToolbar(value: TextFieldValue, ink: Color, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun run(edit: (TextFieldValue) -> TextFieldValue?) {
        edit(value)?.let(onChange)
    }

    Row(
        modifier.fillMaxWidth().background(ink.copy(alpha = 0.07f)).horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Key("☐", "Task", ink) { run(MarkdownEdit::toggleTask) }
        Key("•", "Bullet", ink) { run(MarkdownEdit::bullet) }
        Key("H", "Heading", ink, TextStyle(fontWeight = FontWeight.Bold)) { run(MarkdownEdit::heading) }
        Key("B", "Bold", ink, TextStyle(fontWeight = FontWeight.Black)) { run { MarkdownEdit.wrap(it, "**") } }
        Key("I", "Italic", ink, TextStyle(fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif)) { run { MarkdownEdit.wrap(it, "*") } }
        Key("S", "Strikethrough", ink, TextStyle(textDecoration = TextDecoration.LineThrough)) { run { MarkdownEdit.wrap(it, "~~") } }
        Key("</>", "Code", ink, TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)) { run { MarkdownEdit.wrap(it, "`") } }
        Key("🔗", "Link", ink) { run(MarkdownEdit::link) }
        Key("❝", "Quote", ink) { run(MarkdownEdit::quote) }
        Key("⇤", "Outdent", ink) { run(MarkdownEdit::outdent) }
        Key("⇥", "Indent", ink) { run(MarkdownEdit::indent) }
        Key("📋 ☐", "Paste as checklist", ink, TextStyle(fontSize = 15.sp)) {
            scope.launch {
                val clip = clipboard.getClipEntry()?.clipData ?: return@launch
                val text = (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(context) }
                run { MarkdownEdit.insertBlock(it, Pogocore.checklistFromText(text)) }
            }
        }
    }
}

@Composable
private fun Key(label: String, description: String, ink: Color, style: TextStyle = TextStyle(), onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .semantics { contentDescription = description }.padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, style = TextStyle(fontSize = 18.sp).merge(style))
    }
}
