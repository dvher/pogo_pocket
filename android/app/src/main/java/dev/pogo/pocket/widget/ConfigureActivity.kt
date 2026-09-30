package dev.pogo.pocket.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.pogo.pocket.Line
import dev.pogo.pocket.Note
import dev.pogo.pocket.Palette
import dev.pogo.pocket.Pogo
import dev.pogo.pocket.ui.NoteLines
import dev.pogo.pocket.ui.PogoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown when a widget is added (or reconfigured): pick the note it shows. */
class ConfigureActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Backing out cancels adding the widget.
        setResult(RESULT_CANCELED, result())
        enableEdgeToEdge()
        val notes = Note.parseList(Pogo.core(this).notesJSON())
        setContent { PogoTheme { Picker(notes, ::choose, ::createAndChoose) } }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private fun choose(id: String?) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { id?.let { Pogo.core(this@ConfigureActivity).widgetSetNote(widgetId.toString(), it) } }
            Widgets.refresh(this@ConfigureActivity, widgetId)
            setResult(RESULT_OK, result())
            finish()
        }
    }

    private fun createAndChoose() {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) { Pogo.core(this@ConfigureActivity).create("") }
            Pogo.edited(this@ConfigureActivity)
            choose(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(notes: List<Note>, onPick: (String?) -> Unit, onCreate: () -> Unit) {
    var picked by remember { mutableStateOf(false) }
    val pick = { id: String? -> if (!picked) { picked = true; onPick(id) } }
    Scaffold(topBar = { TopAppBar(title = { Text("Which note should the widget show?") }) }) { padding ->
        if (notes.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text("No notes yet", style = MaterialTheme.typography.titleMedium)
                Text("The widget will show an empty clipboard until a note is added here or synced from Pogo Pad.")
                Button(onClick = onCreate) { Text("Write a note") }
                TextButton(onClick = { pick(null) }) { Text("Add the empty widget") }
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(notes, key = { it.id }) { note ->
                val color = Palette.of(note.color)
                Card(
                    onClick = { pick(note.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = color.bg),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 180.dp),
                ) {
                    NoteLines(remember(note.content) { Line.of(note.content).take(7) }, color.ink, color.accent, Modifier.padding(12.dp), compact = true)
                }
            }
        }
    }
}
