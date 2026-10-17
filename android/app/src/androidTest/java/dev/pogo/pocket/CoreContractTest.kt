package dev.pogo.pocket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pogo.pocket.core.pogocore.Core
import dev.pogo.pocket.core.pogocore.Pogocore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the Go core through the real gomobile binding and parses its JSON with
 * the app's models, so a renamed field or method on either side fails here.
 */
@RunWith(AndroidJUnit4::class)
class CoreContractTest {
    private lateinit var dir: File
    private lateinit var core: Core

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dir = File(context.cacheDir, "core-test-${System.nanoTime()}")
        core = Pogocore.open(dir.path, Pogocore.newKey())
    }

    @After
    fun close() {
        core.close()
        dir.deleteRecursively()
    }

    @Test
    fun welcomeNoteOnlyOnce() {
        assertTrue(core.ensureWelcome())
        assertFalse(core.ensureWelcome())
        val notes = Note.parseList(core.notesJSON())
        assertEquals(1, notes.size)
        assertTrue(notes[0].title.startsWith("Welcome to Pogo Pocket"))
        assertEquals("yellow", notes[0].color)
    }

    @Test
    fun notesRoundTrip() {
        val id = core.create("# Groceries\n\n- [ ] milk\n- [x] eggs\n")
        core.setColor(id, "green")
        val note = Note.parse(org.json.JSONObject(core.noteJSON(id)))
        assertEquals(id, note.id)
        assertEquals("Groceries", note.title)
        assertEquals("green", note.color)
        assertTrue(note.updatedAt > 0)

        core.toggleTask(id, 2L)
        assertTrue(Note.parse(org.json.JSONObject(core.noteJSON(id))).content.contains("- [x] milk"))

        core.setContent(id, "changed")
        assertEquals("changed", Note.parseList(core.notesJSON()).single().content)

        core.delete(id)
        assertTrue(Note.parseList(core.notesJSON()).isEmpty())
        assertThrows(Exception::class.java) { core.noteJSON(id) }
    }

    @Test
    fun widgetViews() {
        val empty = WidgetView.parse(core.widgetViewJSON("7"))
        assertEquals("empty", empty.state)
        assertNull(empty.note)
        assertEquals(0, empty.count)

        val first = core.create("# First\n- [ ] one")
        val second = core.create("# Second")
        core.widgetSetNote("7", first)
        var view = WidgetView.parse(core.widgetViewJSON("7"))
        assertEquals("note", view.state)
        assertEquals(first, view.note?.id)
        assertEquals(2, view.count)
        val task = view.lines.first { it.kind == "task" }
        assertEquals("one", task.text)
        assertFalse(task.checked)

        core.widgetNext("7")
        view = WidgetView.parse(core.widgetViewJSON("7"))
        assertEquals(second, view.note?.id)
        assertEquals(1, view.index)

        // Each widget keeps its own note.
        core.widgetSetNote("8", first)
        assertEquals(first, WidgetView.parse(core.widgetViewJSON("8")).note?.id)
        core.widgetRemove("8")
    }

    @Test
    fun linesAndPalette() {
        val lines = Line.of("# Title\n- [x] done\n- item\n\n> quote")
        assertEquals(listOf("h1", "task", "bullet", "gap", "quote"), lines.map { it.kind })
        assertTrue(lines[1].checked)
        assertEquals(1, lines[1].line)
        assertEquals("•", lines[2].marker)

        assertEquals("- [x] a", Pogocore.toggleTask("- [ ] a", 0L))
        assertEquals("yellow", Palette.colors.first().name)
        assertEquals(7, Palette.colors.size)
        assertEquals(Palette.colors.first(), Palette.of("no-such-color"))
        assertEquals("pink", Palette.of("pink").name)
    }

    @Test
    fun settingsAndStatus() {
        assertEquals(Settings(), Settings.parse(core.settingsJSON()))
        val s = Settings(scheme = "https", host = "pad.example.com", port = "443", token = "pogo_x", enabled = true, intervalSec = 60)
        assertEquals(s, Settings.parse(core.saveSettingsJSON(s.toJSON())))
        assertEquals(s, Settings.parse(core.settingsJSON()))
        assertTrue(core.syncEnabled())

        // Enabled without a host is refused.
        assertThrows(Exception::class.java) { core.saveSettingsJSON(Settings(enabled = true, token = "t").toJSON()) }

        val status = SyncStatus.parse(core.statusJSON())
        assertTrue(status.enabled)
        assertFalse(status.e2eEnabled)
    }

    @Test
    fun tooLongNoteIsRefused() {
        assertThrows(Exception::class.java) { core.create("x".repeat((1 shl 20) + 1)) }
        assertNotNull(core.create("x".repeat(1 shl 20)))
    }
}
