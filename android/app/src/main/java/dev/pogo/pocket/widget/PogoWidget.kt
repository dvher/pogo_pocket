package dev.pogo.pocket.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.pogo.pocket.Line
import dev.pogo.pocket.Pogo
import dev.pogo.pocket.R
import dev.pogo.pocket.WidgetView
import dev.pogo.pocket.mix
import dev.pogo.pocket.ui.MainActivity

/** A Pogo note on the home screen. The folded corner flips to the next note. */
class PogoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val key = Widgets.key(context, id)
        provideContent {
            // Widgets.refresh bumps REV to make the widget reload its view.
            val rev = currentState(Widgets.REV) ?: 0L
            val view = remember(rev) { Widgets.load(context, key) }
            NoteWidget(view)
        }
    }
}

class PogoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PogoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SyncWorker.schedulePeriodic(context)
        SyncWorker.syncNow(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val core = Pogo.core(context)
        appWidgetIds.forEach { core.widgetRemove(it.toString()) }
    }
}

private val CORNER = 44.dp

@Composable
private fun NoteWidget(v: WidgetView) {
    val context = LocalContext.current
    val page = ColorFilter.tint(ColorProvider(v.bg))
    val ink = ColorProvider(v.ink)
    val open = v.note?.let { actionStartActivity(MainActivity.openIntent(context, it.id)) }
        ?: actionStartActivity(MainActivity.launchIntent(context))

    Column(GlanceModifier.fillMaxSize()) {
        Box(
            GlanceModifier.fillMaxWidth().defaultWeight()
                .background(ImageProvider(R.drawable.page_top), ContentScale.FillBounds, page)
                .clickable(open)
                .padding(start = 14.dp, top = 12.dp, end = 12.dp),
        ) {
            when (v.state) {
                "note" -> Lines(v)
                "locked" -> Message(R.drawable.ic_lock, "Notes are locked", "Open Pogo Pocket to unlock", v)
                else -> Message(R.drawable.ic_clipboard, "No notes", null, v)
            }
        }
        Row(GlanceModifier.fillMaxWidth().height(CORNER)) {
            Row(
                GlanceModifier.defaultWeight().fillMaxHeight()
                    .background(ImageProvider(R.drawable.page_bottom_left), ContentScale.FillBounds, page),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    GlanceModifier.size(CORNER).clickable(actionStartActivity(MainActivity.newNoteIntent(context))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        ImageProvider(R.drawable.ic_add), "New note", GlanceModifier.size(20.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(v.ink.copy(alpha = 0.55f))),
                    )
                }
                if (v.count > 1) {
                    Text(
                        "${v.index + 1} / ${v.count}",
                        style = TextStyle(color = ColorProvider(v.ink.copy(alpha = 0.55f)), fontSize = 11.sp),
                    )
                }
            }
            // The folded corner: tap for the next note.
            Box(GlanceModifier.size(CORNER).clickable(actionRunCallback<NextNoteAction>())) {
                Image(
                    ImageProvider(R.drawable.dog_ear), "Next note", GlanceModifier.size(CORNER),
                    colorFilter = ColorFilter.tint(ColorProvider(mix(v.bg, v.accent, 0.45f))),
                )
                Image(ImageProvider(R.drawable.dog_ear_shadow), null, GlanceModifier.size(CORNER))
            }
        }
    }
}

@Composable
private fun Lines(v: WidgetView) {
    val note = v.note ?: return
    val context = LocalContext.current
    val open = actionStartActivity(MainActivity.openIntent(context, note.id))
    val ink = v.ink
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(v.lines) { line ->
            val indent = GlanceModifier.padding(start = (line.indent * 14).dp)
            when (line.kind) {
                "task" -> CheckBox(
                    checked = line.checked,
                    onCheckedChange = actionRunCallback<ToggleTaskAction>(
                        actionParametersOf(ToggleTaskAction.NOTE to note.id, ToggleTaskAction.LINE to line.line),
                    ),
                    text = line.text,
                    modifier = indent,
                    style = TextStyle(
                        color = ColorProvider(if (line.checked) ink.copy(alpha = 0.5f) else ink),
                        fontSize = 13.sp,
                        textDecoration = if (line.checked) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    colors = CheckboxDefaults.colors(
                        checkedColor = ColorProvider(v.accent),
                        uncheckedColor = ColorProvider(ink.copy(alpha = 0.7f)),
                    ),
                )
                "gap" -> Spacer(GlanceModifier.height(6.dp))
                "rule" -> Box(
                    GlanceModifier.fillMaxWidth().padding(vertical = 6.dp),
                ) { Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.25f))) }
                "bullet" -> Row(indent.clickable(open)) {
                    Text(line.marker, style = TextStyle(color = ColorProvider(v.accent), fontSize = 13.sp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(line.text, style = textStyle(ink, 13))
                }
                else -> Text(line.text, GlanceModifier.clickable(open), style = styleFor(line, ink))
            }
        }
    }
}

private fun textStyle(color: Color, size: Int) = TextStyle(color = ColorProvider(color), fontSize = size.sp)

private fun styleFor(line: Line, ink: Color) = when (line.kind) {
    "h1" -> TextStyle(ColorProvider(ink), 17.sp, FontWeight.Bold)
    "h2" -> TextStyle(ColorProvider(ink), 15.sp, FontWeight.Bold)
    "quote" -> TextStyle(ColorProvider(ink.copy(alpha = 0.75f)), 13.sp, fontStyle = FontStyle.Italic)
    "code" -> TextStyle(ColorProvider(ink), 12.sp, fontFamily = FontFamily.Monospace)
    else -> textStyle(ink, 13)
}

@Composable
private fun Message(icon: Int, title: String, subtitle: String?, v: WidgetView) {
    val tint = v.ink.copy(alpha = 0.45f)
    Column(
        GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(icon), title, GlanceModifier.size(48.dp), colorFilter = ColorFilter.tint(ColorProvider(tint)))
        Spacer(GlanceModifier.height(6.dp))
        Text(title, style = TextStyle(ColorProvider(v.ink.copy(alpha = 0.7f)), 14.sp, FontWeight.Medium))
        if (subtitle != null) Text(subtitle, style = textStyle(tint, 11))
    }
}
