package dev.pogo.pocket

import android.content.Context
import dev.pogo.pocket.core.pogocore.Core
import dev.pogo.pocket.core.pogocore.Listener
import dev.pogo.pocket.core.pogocore.Pogocore
import dev.pogo.pocket.widget.SyncWorker
import dev.pogo.pocket.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Process-wide access to the Go core. */
object Pogo {
    @Volatile private var core: Core? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _revision = MutableStateFlow(0L)
    /** Bumped whenever notes change, locally or by a sync. */
    val revision: StateFlow<Long> = _revision

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    fun core(context: Context): Core = core ?: synchronized(this) {
        core ?: open(context.applicationContext).also { core = it }
    }

    private fun open(context: Context): Core {
        val dir = File(context.filesDir, "pogo").path
        val c = Pogocore.open(dir, KeyVault.localKey(context))
        c.ensureWelcome()
        c.setListener(object : Listener {
            override fun notesChanged() {
                _revision.value = System.nanoTime()
                scope.launch { Widgets.refreshAll(context) }
            }

            override fun statusChanged(statusJSON: String) {
                _status.value = SyncStatus.parse(statusJSON)
            }
        })
        _status.value = SyncStatus.parse(c.statusJSON())
        return c
    }

    /** Call after a local edit: syncs shortly after the user stops typing. */
    fun edited(context: Context) = SyncWorker.syncSoon(context)

    /** Runs work that must finish even if the screen that started it goes away. */
    fun background(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) { block() }
    }
}
