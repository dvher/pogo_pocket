package dev.pogo.pocket.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Same cases as the desktop's frontend/src/lib/editing.test.ts, written the
// same way: "|" is the cursor, "<" and ">" a selection.
class MarkdownEditTest {
    private fun parse(src: String): TextFieldValue {
        val s = src.indexOf('<')
        if (s >= 0) return TextFieldValue(src.replace("<", "").replace(">", ""), TextRange(s, src.indexOf('>') - 1))
        val c = src.indexOf('|')
        return TextFieldValue(src.replace("|", ""), TextRange(c))
    }

    private fun show(v: TextFieldValue?): String? {
        v ?: return null
        val (s, e) = v.selection.min to v.selection.max
        if (s == e) return v.text.substring(0, s) + "|" + v.text.substring(s)
        return v.text.substring(0, s) + "<" + v.text.substring(s, e) + ">" + v.text.substring(e)
    }

    private fun run(src: String, cmd: (TextFieldValue) -> TextFieldValue?) = show(cmd(parse(src)))

    @Test fun wrap() {
        val bold = { v: TextFieldValue -> MarkdownEdit.wrap(v, "**") }
        assertEquals("a **<word>** b", run("a <word> b", bold))
        assertEquals("a <word> b", run("a **<word>** b", bold))
        assertEquals("a <word> b", run("a <**word**> b", bold))
        assertEquals("a **|** b", run("a | b", bold))
        assertEquals("a | b", run("a **|** b", bold))
        assertEquals("**|**", run("|", bold))
    }

    @Test fun link() {
        assertEquals("see [docs](<url>)", run("see <docs>", MarkdownEdit::link))
        assertEquals("[|](https://pogo.dev)", run("<https://pogo.dev>", MarkdownEdit::link))
    }

    @Test fun toggleTask() {
        assertEquals("- [ ] milk|", run("milk|", MarkdownEdit::toggleTask))
        assertEquals("  - [ ] milk|", run("  - milk|", MarkdownEdit::toggleTask))
        assertEquals("1. [ ] milk|", run("1. milk|", MarkdownEdit::toggleTask))
        assertEquals("milk|", run("- [x] milk|", MarkdownEdit::toggleTask))
        assertEquals("- [ ] |", run("|", MarkdownEdit::toggleTask))
        assertEquals("- [ ] <a\n\n- [ ] b\n- [ ] c>", run("<a\n\n- b\n- [ ] c>", MarkdownEdit::toggleTask))
        assertEquals("<a\nb>", run("<- [ ] a\n- [x] b>", MarkdownEdit::toggleTask))
        assertEquals("- [ ] <a\n>b", run("<a\n>b", MarkdownEdit::toggleTask))
    }

    @Test fun lineCommands() {
        assertEquals("- milk|", run("milk|", MarkdownEdit::bullet))
        assertEquals("milk|", run("- milk|", MarkdownEdit::bullet))
        assertEquals("# Title|", run("Title|", MarkdownEdit::heading))
        assertEquals("## Title|", run("# Title|", MarkdownEdit::heading))
        assertEquals("Title|", run("## Title|", MarkdownEdit::heading))
        assertEquals("> a|", run("a|", MarkdownEdit::quote))
        assertEquals("a|", run("> a|", MarkdownEdit::quote))
        assertEquals("  - a|", run("- a|", MarkdownEdit::indent))
        assertEquals("  - a|", run("    - a|", MarkdownEdit::outdent))
        assertNull(run("a|", MarkdownEdit::outdent))
    }

    @Test fun continueList() {
        assertEquals("- milk\n- |", run("- milk|", MarkdownEdit::continueList))
        assertEquals("  * [x] milk\n  * [ ] |", run("  * [x] milk|", MarkdownEdit::continueList))
        assertEquals("9) one\n10) |", run("9) one|", MarkdownEdit::continueList))
        assertEquals("> - quoted\n> - |", run("> - quoted|", MarkdownEdit::continueList))
        assertEquals("- mi\n- |lk", run("- mi|lk", MarkdownEdit::continueList))
        assertEquals("- a\n|", run("- a\n- [ ] |", MarkdownEdit::continueList))
        assertNull(run("plain|", MarkdownEdit::continueList))
        assertNull(run("|- a", MarkdownEdit::continueList))
    }

    @Test fun typedNewline() {
        assert(MarkdownEdit.typedNewline(parse("- a|b"), parse("- a\n|b")))
        assert(!MarkdownEdit.typedNewline(parse("- a|"), parse("- ab|")))
        assert(!MarkdownEdit.typedNewline(parse("- a|"), parse("- a\n\n|")))
    }

    @Test fun insertBlock() {
        val paste = { v: TextFieldValue -> MarkdownEdit.insertBlock(v, "- [ ] a") }
        assertEquals("- [ ] a|", run("|", paste))
        assertEquals("Todo\n- [ ] a|", run("Todo|", paste))
        assertEquals("x\n- [ ] a|\ny", run("x|y", paste))
        assertEquals("x\n- [ ] a|\ny", run("x\n<old>\ny", paste))
        assertNull(MarkdownEdit.insertBlock(parse("|"), ""))
    }
}
