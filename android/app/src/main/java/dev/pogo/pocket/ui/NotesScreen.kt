package dev.pogo.pocket.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.pogo.pocket.Line
import dev.pogo.pocket.Note
import dev.pogo.pocket.Palette
import dev.pogo.pocket.Pogo
import dev.pogo.pocket.R
import dev.pogo.pocket.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onOpen: (String) -> Unit, onNew: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val revision by Pogo.revision.collectAsState()
    val status by Pogo.status.collectAsState()
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(revision) {
        notes = withContext(Dispatchers.IO) { Note.parseList(Pogo.core(context).notesJSON()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pogo Pocket") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Sync settings") }
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Sync now") }, onClick = {
                            menu = false
                            scope.launch(Dispatchers.IO) { runCatching { Pogo.core(context).syncNow() } }
                        })
                        if (Widgets.canPin(context)) {
                            DropdownMenuItem(text = { Text("Add widget to home screen") }, onClick = {
                                menu = false
                                Widgets.requestPin(context)
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, "New note") }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = status.syncing,
            onRefresh = { scope.launch(Dispatchers.IO) { runCatching { Pogo.core(context).syncNow() } } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (status.enabled && status.lastError.isNotEmpty()) {
                    Text(
                        status.lastError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                val list = notes
                when {
                    list == null -> {}
                    list.isEmpty() -> EmptyNotes()
                    else -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
                        verticalItemSpacing = 10.dp,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(list, key = { it.id }) { NoteCard(it) { onOpen(it.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val color = Palette.of(note.color)
    val lines = remember(note.content) { Line.of(note.content).take(10) }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.bg),
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
    ) {
        NoteLines(lines, color.ink, color.accent, Modifier.padding(12.dp), compact = true)
    }
}

@Composable
private fun EmptyNotes() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(96.dp))
        Image(
            painterResource(R.drawable.ic_clipboard), null, Modifier.size(72.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.height(12.dp))
        Text("No notes", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap + to write one, or set up sync to bring in your notes from Pogo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Box(Modifier.height(8.dp))
    }
}
