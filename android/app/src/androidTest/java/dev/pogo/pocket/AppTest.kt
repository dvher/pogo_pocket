package dev.pogo.pocket

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pogo.pocket.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun localKeyIsStable() {
        val key = KeyVault.localKey(context)
        assertEquals(44, key.length) // 32 bytes, base64
        assertEquals(key, KeyVault.localKey(context))
    }

    @Test
    fun appOpens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
        // The first start leaves at least the welcome note.
        assertTrue(Note.parseList(Pogo.core(context).notesJSON()).isNotEmpty() || !Pogo.core(context).ensureWelcome())
    }
}
