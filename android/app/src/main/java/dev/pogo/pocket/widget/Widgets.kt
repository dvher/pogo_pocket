package dev.pogo.pocket.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.pogo.pocket.Pogo
import dev.pogo.pocket.WidgetView

object Widgets {
    /** Bumped to make a widget reload its view from the core. */
    val REV = longPreferencesKey("rev")

    /** The core keys widget state by the Android appWidgetId. */
    suspend fun key(context: Context, id: GlanceId): String =
        GlanceAppWidgetManager(context).getAppWidgetId(id).toString()

    fun load(context: Context, key: String): WidgetView =
        WidgetView.parse(Pogo.core(context).widgetViewJSON(key))

    suspend fun refresh(context: Context, id: GlanceId) {
        updateAppWidgetState(context, id) { it[REV] = System.nanoTime() }
        PogoWidget().update(context, id)
    }

    suspend fun refresh(context: Context, appWidgetId: Int) =
        refresh(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId))

    suspend fun refreshAll(context: Context) {
        try {
            GlanceAppWidgetManager(context).getGlanceIds(PogoWidget::class.java).forEach { refresh(context, it) }
        } catch (e: Exception) {
            Log.w("Pogo", "widget refresh failed", e)
        }
    }

    fun canPin(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** Asks the launcher to add a widget; it shows the note picker afterwards. */
    fun requestPin(context: Context) {
        AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, PogoWidgetReceiver::class.java), null, null)
    }
}
