package dev.pogo.pocket.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pogo.pocket.Pogo
import dev.pogo.pocket.Settings
import dev.pogo.pocket.widget.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val core = remember { Pogo.core(context) }
    val status by Pogo.status.collectAsState()
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(Settings.parse(core.settingsJSON())) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // ok?, text
    var busy by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var e2eMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmDisable by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            block()
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sync your notes through Pogo Pad, a server you host yourself. Create a token on the server with " +
                    "`pogo-pad token create --name phone`.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    form.host, { form = form.copy(host = it) }, Modifier.weight(1f),
                    label = { Text("IP address or hostname") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    form.port, { form = form.copy(port = it.filter(Char::isDigit)) }, Modifier.weight(0.45f),
                    label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            SwitchRow("Use HTTPS", form.scheme == "https") { form = form.copy(scheme = if (it) "https" else "http") }
            OutlinedTextField(
                form.token, { form = form.copy(token = it) }, Modifier.fillMaxWidth(),
                label = { Text("API token") }, placeholder = { Text("pogo_…") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            SwitchRow("Enable sync", form.enabled) { form = form.copy(enabled = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    run {
                        message = withContext(Dispatchers.IO) {
                            runCatching { true to core.testConnectionJSON(form.toJSON()) }.getOrElse { false to (it.message ?: "failed") }
                        }
                    }
                }) { Text("Test connection") }
                Button(enabled = !busy, onClick = {
                    run {
                        message = withContext(Dispatchers.IO) {
                            runCatching {
                                form = Settings.parse(core.saveSettingsJSON(form.toJSON()))
                                true to "Saved."
                            }.getOrElse { false to (it.message ?: "failed") }
                        }
                        if (message?.first == true) SyncWorker.syncNow(context)
                    }
                }) { Text("Save") }
            }
            message?.let { (ok, text) ->
                Text(text, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            StatusLine(status.enabled, status.syncing, status.lastSync, status.lastError)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("End-to-end encryption", style = MaterialTheme.typography.titleMedium)
            when {
                status.e2eEnabled -> {
                    Text("On. Notes are encrypted before they leave this phone; Pogo Pad only stores unreadable data.")
                    OutlinedButton(enabled = !busy, onClick = { confirmDisable = true }) { Text("Turn off for all devices") }
                }
                else -> {
                    Text(
                        if (status.e2eServer) "Notes on this server are end-to-end encrypted. Enter the passphrase you set on your other devices."
                        else "Optional. Encrypt notes with a passphrase before they leave your devices. If you lose it, notes on the server can't be recovered.",
                    )
                    OutlinedTextField(
                        passphrase, { passphrase = it }, Modifier.fillMaxWidth(),
                        label = { Text("Passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(enabled = !busy && passphrase.isNotEmpty(), onClick = {
                        run {
                            e2eMessage = withContext(Dispatchers.IO) {
                                runCatching { core.enableE2E(passphrase); true to "End-to-end encryption is on." }
                                    .getOrElse { false to (it.message ?: "failed") }
                            }
                            if (e2eMessage?.first == true) passphrase = ""
                        }
                    }) { Text(if (status.e2eServer) "Unlock" else "Turn on") }
                }
            }
            e2eMessage?.let { (ok, text) ->
                Text(text, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Turn off end-to-end encryption?") },
            text = { Text("Notes will be stored on Pogo Pad in plain text again, for all your devices.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    run {
                        e2eMessage = withContext(Dispatchers.IO) {
                            runCatching { core.disableE2E(); true to "End-to-end encryption is off." }
                                .getOrElse { false to (it.message ?: "failed") }
                        }
                    }
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun StatusLine(enabled: Boolean, syncing: Boolean, lastSync: Long, lastError: String) {
    val text = when {
        !enabled -> "Sync is off."
        syncing -> "Syncing…"
        lastError.isNotEmpty() -> "Last sync failed: $lastError"
        lastSync > 0 -> "Last synced " + DateUtils.getRelativeTimeSpanString(lastSync) + "."
        else -> "Not synced yet."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (enabled && lastError.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}
