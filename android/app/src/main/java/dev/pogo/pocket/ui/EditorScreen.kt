package dev.pogo.pocket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pogo.pocket.Line
import dev.pogo.pocket.Note
import dev.pogo.pocket.Palette
import dev.pogo.pocket.Pogo
import dev.pogo.pocket.core.pogocore.Pogocore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(id: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val core = remember { Pogo.core(context) }
    val revision by Pogo.revision.collectAsState()
    var note by remember { mutableStateOf<Note?>(null) }
    var content by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(true) }
    var palette by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val text = content
        if (note == null || text == saved) return
        saved = text
        Pogo.background {
            runCatching { core.setContent(id, text) }
            Pogo.edited(context)
        }
    }

    fun done() {
        save()
        onDone()
    }

    // Load, and follow edits made elsewhere (e.g. a task ticked on the widget)
    // while there are no unsaved local changes.
    LaunchedEffect(revision) {
        val loaded = withContext(Dispatchers.IO) { runCatching { Note.parse(org.json.JSONObject(core.noteJSON(id))) }.getOrNull() }
        if (loaded == null) {
            onDone() // deleted
            return@LaunchedEffect
        }
        if (note == null || content == saved) {
            content = loaded.content
            saved = loaded.content
        }
        note = loaded
    }
    LaunchedEffect(content) {
        delay(800)
        save()
    }
    BackHandler { done() }

    val n = note ?: return
    val color = Palette.of(n.color)

    Scaffold(
        containerColor = color.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = color.bg, navigationIconContentColor = color.ink, actionIconContentColor = color.ink,
                ),
                title = {},
                navigationIcon = {
                    IconButton(onClick = ::done) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { palette = true }) {
                            Box(Modifier.size(22.dp).background(color.accent, CircleShape).border(2.dp, color.ink.copy(alpha = 0.4f), CircleShape))
                        }
                        DropdownMenu(expanded = palette, onDismissRequest = { palette = false }) {
                            Row(Modifier.padding(horizontal = 8.dp)) {
                                Palette.colors.forEach { c ->
                                    IconButton(onClick = {
                                        palette = false
                                        Pogo.background {
                                            runCatching { core.setColor(id, c.name) }
                                            Pogo.edited(context)
                                        }
                                    }) {
                                        Box(
                                            Modifier.size(28.dp).background(c.bg, CircleShape)
                                                .border(if (c.name == n.color) 3.dp else 1.dp, c.accent, CircleShape),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { save(); editing = !editing }) {
                        if (editing) Icon(Icons.Default.Check, "Preview") else Icon(Icons.Default.Edit, "Edit")
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        val body = Modifier.padding(padding).imePadding().fillMaxSize()
        if (editing) {
            // Keep the cursor where the user left it; start at the end.
            var field by remember { mutableStateOf(TextFieldValue(content, TextRange(content.length))) }
            if (field.text != content) field = TextFieldValue(content, TextRange(content.length))
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    content = it.text
                },
                textStyle = TextStyle(color = color.ink, fontSize = 16.sp, lineHeight = 24.sp),
                cursorBrush = SolidColor(color.ink),
                modifier = body.padding(horizontal = 20.dp, vertical = 8.dp).focusRequester(focus),
            )
        } else {
            val lines = remember(content) { Line.of(content) }
            NoteLines(
                lines, color.ink, color.accent,
                modifier = body.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                onToggle = { line -> content = Pogocore.toggleTask(content, line.toLong()) },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("It will be removed from all your devices.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    note = null
                    Pogo.background {
                        runCatching { core.delete(id) }
                        Pogo.edited(context)
                    }
                    onDone()
                }) { Text("Delete", color = Color(0xFFB3261E)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
