package dev.pogo.pocket.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.pogo.pocket.Pogo
import dev.pogo.pocket.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Notes : Route
    data class Editor(val id: String) : Route
    data object SyncSettings : Route
}

class MainActivity : ComponentActivity() {
    private var route by mutableStateOf<Route>(Route.Notes)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            PogoTheme {
                BackHandler(enabled = route != Route.Notes) { route = Route.Notes }
                when (val r = route) {
                    Route.Notes -> NotesScreen(
                        onOpen = { route = Route.Editor(it) },
                        onNew = { newNote() },
                        onSettings = { route = Route.SyncSettings },
                    )
                    is Route.Editor -> EditorScreen(r.id, onDone = { route = Route.Notes })
                    Route.SyncSettings -> SettingsScreen(onBack = { route = Route.Notes })
                }
            }
        }
        // Sync on the desktop's schedule while the app is on screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val core = withContext(Dispatchers.IO) { Pogo.core(this@MainActivity) }
                while (true) {
                    withContext(Dispatchers.IO) { runCatching { core.syncNow() } }
                    delay(Settings.parse(core.settingsJSON()).intervalSec.coerceAtLeast(10) * 1000L)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN -> intent.getStringExtra(EXTRA_NOTE)?.let { route = Route.Editor(it) }
            ACTION_NEW -> newNote()
        }
        // Don't reopen the same note after a configuration change.
        intent?.action = Intent.ACTION_MAIN
    }

    private fun newNote() {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) { Pogo.core(this@MainActivity).create("") }
            Pogo.edited(this@MainActivity)
            route = Route.Editor(id)
        }
    }

    companion object {
        private const val ACTION_OPEN = "dev.pogo.pocket.OPEN_NOTE"
        private const val ACTION_NEW = "dev.pogo.pocket.NEW_NOTE"
        private const val EXTRA_NOTE = "note"

        fun launchIntent(context: Context) = Intent(context, MainActivity::class.java)

        /** The data URI keeps PendingIntents for different notes apart. */
        fun openIntent(context: Context, id: String) = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN).setData(Uri.parse("pogo://note/$id")).putExtra(EXTRA_NOTE, id)

        fun newNoteIntent(context: Context) = Intent(context, MainActivity::class.java)
            .setAction(ACTION_NEW).setData(Uri.parse("pogo://new"))
    }
}
