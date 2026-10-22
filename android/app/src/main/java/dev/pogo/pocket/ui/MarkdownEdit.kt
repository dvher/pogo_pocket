package dev.pogo.pocket.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

// Markdown editing commands for the note editor's toolbar, mirroring the
// desktop's frontend/src/lib/editing.ts. Each returns the edited value, or
// null when there is nothing to change. Offsets are UTF-16, like TextRange.
object MarkdownEdit {
    private fun lineStart(text: String, pos: Int) = text.lastIndexOf('\n', pos - 1) + 1

    private fun lineEnd(text: String, pos: Int) = text.indexOf('\n', pos).let { if (it < 0) text.length else it }

    private fun edit(text: String, from: Int, to: Int, insert: String, selStart: Int, selEnd: Int = selStart) =
        TextFieldValue(text.substring(0, from) + insert + text.substring(to), TextRange(selStart, selEnd))

    // Applies fn to every line the selection touches. A selection ending right
    // after a newline does not include the next line.
    private fun mapLines(v: TextFieldValue, fn: (line: String, lines: List<String>) -> String): TextFieldValue? {
        val text = v.text
        val s = v.selection.min
        val e = v.selection.max
        val from = lineStart(text, s)
        val to = lineEnd(text, if (e > s && text[e - 1] == '\n') e - 1 else e)
        val lines = text.substring(from, to).split('\n')
        val out = lines.map { fn(it, lines) }
        val insert = out.joinToString("\n")
        if (insert == text.substring(from, to)) return null
        val selStart = maxOf(from, s + out[0].length - lines[0].length)
        val selEnd = if (s == e) selStart else maxOf(selStart, e + insert.length - (to - from))
        return edit(text, from, to, insert, selStart, selEnd)
    }

    // Wraps the selection in marker (** for bold, * for italic, …), or unwraps
    // it when it is already wrapped.
    fun wrap(v: TextFieldValue, marker: String): TextFieldValue {
        val text = v.text
        val s = v.selection.min
        val e = v.selection.max
        val n = marker.length
        val sel = text.substring(s, e)
        if (sel.length >= 2 * n && sel.startsWith(marker) && sel.endsWith(marker)) {
            val inner = sel.substring(n, sel.length - n)
            return edit(text, s, e, inner, s, s + inner.length)
        }
        if (s >= n && text.startsWith(marker, s - n) && text.startsWith(marker, e)) {
            return edit(text, s - n, e + n, sel, s - n, e - n)
        }
        return edit(text, s, e, marker + sel + marker, s + n, e + n)
    }

    // Turns the selection into a link and selects the part still to fill in.
    fun link(v: TextFieldValue): TextFieldValue {
        val s = v.selection.min
        val e = v.selection.max
        val sel = v.text.substring(s, e)
        if (Regex("""^https?://\S+$""").matches(sel)) return edit(v.text, s, e, "[]($sel)", s + 1)
        val at = s + sel.length + 3
        return edit(v.text, s, e, "[$sel](url)", at, at + 3)
    }

    // indent, quotes, list marker, spacing, checkbox
    private val listItem = Regex("""^(\s*)((?:>\s?)*)([-*+]|\d+[.)])(\s+|$)(\[[ xX]\](?:\s+|$))?""")
    private fun isTask(l: String) = listItem.find(l)?.groupValues?.get(5)?.isNotEmpty() == true

    // Makes the touched lines tasks, or plain text again if they all are.
    fun toggleTask(v: TextFieldValue): TextFieldValue? = mapLines(v) { l, lines ->
        val filled = lines.filter { it.isNotBlank() }
        val m = listItem.find(l)
        when {
            lines.size > 1 && l.isBlank() -> l
            filled.isNotEmpty() && filled.all(::isTask) -> m!!.groupValues[1] + m.groupValues[2] + l.substring(m.value.length)
            m != null && m.groupValues[5].isNotEmpty() -> l
            m != null -> l.substring(0, m.value.length) + (if (m.groupValues[4].isEmpty()) " " else "") + "[ ] " + l.substring(m.value.length)
            else -> {
                val indent = l.takeWhile { it.isWhitespace() }
                indent + "- [ ] " + l.substring(indent.length)
            }
        }
    }

    // Makes the touched lines bullets, or plain text again if they all are.
    fun bullet(v: TextFieldValue): TextFieldValue? = mapLines(v) { l, lines ->
        val filled = lines.filter { it.isNotBlank() }
        val m = listItem.find(l)
        when {
            lines.size > 1 && l.isBlank() -> l
            filled.isNotEmpty() && filled.all { listItem.containsMatchIn(it) } -> m!!.groupValues[1] + m.groupValues[2] + l.substring(m.value.length)
            m != null -> l
            else -> {
                val indent = l.takeWhile { it.isWhitespace() }
                indent + "- " + l.substring(indent.length)
            }
        }
    }

    // Cycles the current line through "# ", "## " and no heading.
    fun heading(v: TextFieldValue): TextFieldValue? = mapLines(v) { l, _ ->
        val body = l.replace(Regex("""^\s*#{1,6}\s*"""), "")
        when {
            l.startsWith("# ") -> "## $body"
            l.startsWith("## ") -> body
            else -> "# $body"
        }
    }

    // Quotes the touched lines, or unquotes them if they all are.
    fun quote(v: TextFieldValue): TextFieldValue? = mapLines(v) { l, lines ->
        val filled = lines.filter { it.isNotBlank() }
        when {
            lines.size > 1 && l.isBlank() -> l
            filled.isNotEmpty() && filled.all { it.startsWith(">") } -> l.replace(Regex("""^>\s?"""), "")
            else -> "> $l"
        }
    }

    fun indent(v: TextFieldValue): TextFieldValue? = mapLines(v) { l, lines -> if (lines.size > 1 && l.isBlank()) l else "  $l" }

    fun outdent(v: TextFieldValue): TextFieldValue? = mapLines(v) { l, _ -> l.replace(Regex("""^(?: {1,2}|\t)"""), "") }

    // Enter inside a list item starts the next one; Enter on an empty item
    // ends the list. v is the value before the newline was typed.
    fun continueList(v: TextFieldValue): TextFieldValue? {
        if (!v.selection.collapsed) return null
        val text = v.text
        val s = v.selection.start
        val from = lineStart(text, s)
        val to = lineEnd(text, s)
        val line = text.substring(from, to)
        val m = listItem.find(line) ?: return null
        if (s < from + m.value.length) return null
        if (line.substring(m.value.length).isBlank()) return edit(text, from, to, "", from)
        val num = Regex("""^(\d+)([.)])$""").find(m.groupValues[3])
        val marker = num?.let { "${it.groupValues[1].toLong() + 1}${it.groupValues[2]}" } ?: m.groupValues[3]
        val insert = "\n" + m.groupValues[1] + m.groupValues[2] + marker + " " + (if (m.groupValues[5].isNotEmpty()) "[ ] " else "")
        return edit(text, s, s, insert, s + insert.length)
    }

    // Replaces the selection with block (e.g. a pasted checklist), on lines
    // of its own.
    fun insertBlock(v: TextFieldValue, block: String): TextFieldValue? {
        if (block.isEmpty()) return null
        val text = v.text
        val s = v.selection.min
        val e = v.selection.max
        var insert = if (s > 0 && text[s - 1] != '\n') "\n$block" else block
        val end = s + insert.length
        if (e < text.length && text[e] != '\n') insert += "\n"
        return edit(text, s, e, insert, end)
    }

    // Detects a single newline typed at a collapsed cursor, so the editor can
    // continue the list instead.
    fun typedNewline(old: TextFieldValue, new: TextFieldValue): Boolean {
        if (!old.selection.collapsed || !new.selection.collapsed) return false
        val s = old.selection.start
        return new.text.length == old.text.length + 1 && new.selection.start == s + 1 &&
            new.text[s] == '\n' && new.text.regionMatches(0, old.text, 0, s) &&
            new.text.regionMatches(s + 1, old.text, s, old.text.length - s)
    }
}
