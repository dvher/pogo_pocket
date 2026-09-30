package dev.pogo.pocket.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.pogo.pocket.Pogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The folded corner: show the next note. */
class NextNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = Widgets.key(context, glanceId)
        withContext(Dispatchers.IO) { Pogo.core(context).widgetNext(key) }
        Widgets.refresh(context, glanceId)
    }
}

/** Crosses a task off (or back on) without opening the app. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val note = parameters[NOTE] ?: return
        val line = parameters[LINE] ?: return
        withContext(Dispatchers.IO) { Pogo.core(context).toggleTask(note, line.toLong()) }
        Pogo.edited(context)
        Widgets.refreshAll(context)
    }

    companion object {
        val NOTE = ActionParameters.Key<String>("note")
        val LINE = ActionParameters.Key<Int>("line")
    }
}
