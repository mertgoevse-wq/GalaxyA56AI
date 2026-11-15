package com.galaxya56.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxya56.ai.data.Catalog
import com.galaxya56.ai.inference.HardwareInfo
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.Ink
import com.galaxya56.ai.ui.theme.Surface1
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.Surface3
import com.galaxya56.ai.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: AppViewModel, onOpenModels: () -> Unit) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val hardware by viewModel.hardware.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val conversation = conversations.firstOrNull { it.id == activeId }
    val messages = conversation?.messages.orEmpty()
    val listState = rememberLazyListState()

    var input by rememberSaveable { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var showPromptSheet by remember { mutableStateOf(false) }
    var customPrompt by rememberSaveable { mutableStateOf("") }

    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastLength) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (messages.isEmpty()) {
            EmptyChatState(
                modifier = Modifier.weight(1f),
                onSuggestion = { suggestion ->
                    viewModel.newConversation()
                    viewModel.send(suggestion)
                }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageRow(message, context)
                }
            }
        }

        if (generating) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(Accent, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(
                    status.ifBlank { "Generiere …" },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.stopGeneration() }) { Text("Stop") }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill(
                text = if (settings.cloudReady) "☁ ${settings.cloudModel}" else "◈ ${installed.firstOrNull { it.name.endsWith(".gguf", true) }?.name?.take(22) ?: "Modell wählen"}",
                color = Surface2,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { showModelSheet = true }
            )
            Pill(
                text = "System: ${Catalog.promptById(settings.systemPromptId).label}",
                color = Surface2,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { showPromptSheet = true }
            )
            Spacer(Modifier.weight(1f))
            Pill(text = "T ${"%.2f".format(settings.temperature)}", color = Surface2)
        }

        Composer(
            value = input,
            onValueChange = { input = it },
            generating = generating,
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    input = ""
                    viewModel.send(text)
                }
            },
            onStop = { viewModel.stopGeneration() },
            hardware = hardware
        )
    }

    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }, containerColor = Surface1) {
            ModelSheet(
                viewModel = viewModel,
                installed = installed,
                cloudReady = settings.cloudKeyPresent(),
                cloudModel = settings.cloudModel,
                onSelectLocal = { path ->
                    viewModel.selectModel(path)
                    viewModel.updateSettings { it.copy(cloudEnabled = false) }
                    showModelSheet = false
                },
                onSelectCloud = {
                    if (settings.geminiKey.isBlank()) {
                        showModelSheet = false
                        onOpenModels()
                    } else {
                        viewModel.updateSettings { it.copy(cloudEnabled = true) }
                        showModelSheet = false
                    }
                },
                onManage = {
                    showModelSheet = false
                    onOpenModels()
                }
            )
        }
    }

    if (showPromptSheet) {
        ModalBottomSheet(onDismissRequest = { showPromptSheet = false }, containerColor = Surface1) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("System-Prompt", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Legt fest, wie das Modell antwortet. Gilt für lokale und Cloud-Modelle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Catalog.prompts.forEach { prompt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateSettings {
                                    it.copy(systemPromptId = prompt.id, customSystemPrompt = "")
                                }
                                showPromptSheet = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                prompt.label,
                                fontWeight = if (settings.systemPromptId == prompt.id) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                prompt.text.take(90) + "…",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        if (settings.systemPromptId == prompt.id && settings.customSystemPrompt.isBlank()) {
                            Text("aktiv", color = Accent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    label = { Text("Eigener System-Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        viewModel.updateSettings { it.copy(customSystemPrompt = customPrompt) }
                        showPromptSheet = false
                    }) { Text("Übernehmen") }
                }
            }
        }
    }
}

private fun com.galaxya56.ai.data.AppSettings.cloudKeyPresent(): Boolean = geminiKey.isNotBlank()

@Composable
private fun EmptyChatState(modifier: Modifier, onSuggestion: (String) -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(58.dp).background(Accent, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("A56", color = Ink, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Was möchtest du bauen?",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Läuft lokal auf dem Gerät — Coding, Text, Bilder, Musik. Modelle von Hugging Face & Civitai.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        listOf(
            "Baue mir eine Compose-App für Notizen",
            "Erkläre mir Kotlin Coroutines an einem Beispiel",
            "Schreibe einen Unit-Test für diese Funktion: …",
            "Entwirf einen Diffusions-Prompt für ein Produktfoto"
        ).forEach { suggestion ->
            Surface(
                color = Surface2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable { onSuggestion(suggestion) }
            ) {
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, context: android.content.Context) {
    if (message.role == Role.USER) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = Surface3,
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        return
    }

    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(28.dp)
                .background(if (message.error) MaterialTheme.colorScheme.error else Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = Ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            if (message.text.isBlank()) {
                Text("…", color = TextMuted)
            } else {
                SelectionContainer {
                    MessageContent(message.text)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                message.meta?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { copyToClipboard(context, message.text) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Antwort kopieren",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    hardware: HardwareInfo
) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nachricht an das lokale Modell …", color = TextMuted) },
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
            IconButton(
                onClick = { if (generating) onStop() else onSend() },
                modifier = Modifier
                    .size(40.dp)
                    .background(if (generating) Surface3 else Accent, CircleShape)
            ) {
                Icon(
                    if (generating) Icons.Filled.Stop else Icons.Filled.Send,
                    contentDescription = if (generating) "Stop" else "Senden",
                    tint = if (generating) MaterialTheme.colorScheme.onSurface else Ink,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    Text(
        "${hardware.cpuSummary} · NPU: ${if (hardware.npu) "ja" else "nein"} · GPU: ${hardware.gpu.ifBlank { "?" }}",
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
private fun ModelSheet(
    viewModel: AppViewModel,
    installed: List<com.galaxya56.ai.data.InstalledModel>,
    cloudReady: Boolean,
    cloudModel: String,
    onSelectLocal: (String) -> Unit,
    onSelectCloud: () -> Unit,
    onManage: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Modell wählen", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        Surface(
            color = Surface2,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable { onSelectCloud() }
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Gemini Cloud", fontWeight = FontWeight.SemiBold)
                Text(
                    if (cloudReady) cloudModel else "API-Key in den Optionen hinterlegen",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Lokale GGUF-Modelle", style = MaterialTheme.typography.labelSmall, color = TextMuted)

        val gguf = installed.filter { it.name.endsWith(".gguf", true) }
        if (gguf.isEmpty()) {
            EmptyHint("Kein GGUF installiert", "Unter „Modelle“ → Katalog oder Hugging Face herunterladen.")
        } else {
            gguf.forEach { model ->
                Surface(
                    color = Surface2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSelectLocal(model.file.absolutePath) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(model.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Text("${model.sizeMb} MB", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onManage) { Text("Modelle verwalten") }
        }
        Spacer(Modifier.height(12.dp))
    }
}
