package com.galaxya56.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxya56.ai.data.Catalog
import com.galaxya56.ai.data.InstalledModel
import com.galaxya56.ai.inference.HardwareInfo
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.AccentSoft
import com.galaxya56.ai.ui.theme.AccentText
import com.galaxya56.ai.ui.theme.Ink
import com.galaxya56.ai.ui.theme.Line
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
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (messages.isEmpty()) {
            HeroEmpty(
                modifier = Modifier.weight(1f),
                onSuggestion = { suggestion ->
                    viewModel.newConversation()
                    viewModel.send(suggestion)
                },
                onOpenModels = onOpenModels
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageRow(message, context)
                }
            }
        }

        if (generating) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .background(AccentSoft.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(Accent, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(
                    status.ifBlank { "Lokales Modell denkt nach …" },
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { viewModel.stopGeneration() }) { Text("Stop") }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill(
                text = if (settings.cloudReady) "☁ ${settings.cloudModel}"
                else "◈ ${activeLocalModelName(settings.selectedModelPath, installed)}",
                color = if (settings.cloudReady) Surface2 else AccentSoft,
                contentColor = if (settings.cloudReady) TextMuted else AccentText,
                onClick = { showModelSheet = true }
            )
            Pill(
                text = "System: ${Catalog.promptById(settings.systemPromptId).label}",
                color = Surface2,
                contentColor = TextMuted,
                onClick = { showPromptSheet = true }
            )
            Pill(text = "T ${"%.2f".format(settings.temperature)}", color = Surface2)
            Pill(text = "${settings.maxTokens} max", color = Surface2)
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
            hardware = hardware,
            local = !settings.cloudReady
        )
    }

    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }, containerColor = Surface1) {
            ModelSheet(
                viewModel = viewModel,
                installed = installed,
                selectedPath = settings.selectedModelPath,
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
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("System-Prompt", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bestimmt Haltung, Format und Grenzen des Modells. Gilt lokal und in der Cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Catalog.prompts.forEach { prompt ->
                    val active = settings.systemPromptId == prompt.id && settings.customSystemPrompt.isBlank()
                    Surface(
                        color = if (active) AccentSoft else Surface2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable {
                                viewModel.updateSettings {
                                    it.copy(systemPromptId = prompt.id, customSystemPrompt = "")
                                }
                                showPromptSheet = false
                            }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(prompt.label, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                                Text(
                                    prompt.text.take(100) + "…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    maxLines = 2
                                )
                            }
                            if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 10.dp))
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
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun com.galaxya56.ai.data.AppSettings.cloudKeyPresent(): Boolean = geminiKey.isNotBlank()

private fun activeLocalModelName(path: String, installed: List<InstalledModel>): String {
    if (path.isNotBlank()) return path.substringAfterLast('/').take(26)
    return installed.firstOrNull { it.name.endsWith(".gguf", true) }?.name?.take(26) ?: "Modell wählen"
}

@Composable
private fun HeroEmpty(
    modifier: Modifier,
    onSuggestion: (String) -> Unit,
    onOpenModels: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(AccentSoft.copy(alpha = 0.68f), Ink, Ink)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(color = Accent, shape = RoundedCornerShape(50)) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, tint = Ink, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("LOKAL AUF DEM A56", color = Ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Dein Modell.\nDein Gerät.\nDeine Regeln.",
                style = MaterialTheme.typography.headlineSmall,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * 1.05f
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Chatte mit GGUF-Modellen direkt auf dem Galaxy A56 — ohne Cloud-Zwang, " +
                    "mit Vulkan-Beschleunigung und Zugriff auf offene Modelle von Hugging Face und Civitai.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("GGUF", Surface2, AccentText)
                Pill("Vulkan", Surface2, AccentText)
                Pill("LoRA", Surface2, AccentText)
                Pill("NSFW-fähig", Surface2, AccentText)
            }
            Spacer(Modifier.height(22.dp))
            Text("Schnell starten", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val suggestions = listOf(
                Suggestion(Icons.Outlined.Code, "App bauen", "Baue mir eine Compose-App für Notizen mit lokalem Speicher."),
                Suggestion(Icons.Outlined.AutoAwesome, "Erklären", "Erkläre mir Kotlin Coroutines an einem konkreten Beispiel."),
                Suggestion(Icons.Outlined.Lightbulb, "Denken", "Hilf mir, ein neues Feature für meine Android-App zu planen."),
                Suggestion(Icons.Outlined.Image, "Bild-Prompt", "Entwirf einen englischen Diffusions-Prompt für ein cineastisches Produktfoto.")
            )
            suggestions.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { suggestion ->
                        SuggestionCard(
                            suggestion,
                            Modifier.weight(1f),
                            onClick = { onSuggestion(suggestion.prompt) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(14.dp))
            OutlinedButtonLike(
                text = "Modelle entdecken — Bild, LLM & LoRA",
                onClick = onOpenModels
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Tipp: Unter „Entdecken“ findest du auch abliterated, uncensored und NSFW-Modelle. " +
                    "Die 18+-Freigabe ist nur eine Altersbestätigung, kein Inhaltsfilter.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class Suggestion(
    val icon: ImageVector,
    val title: String,
    val prompt: String
)

@Composable
private fun SuggestionCard(suggestion: Suggestion, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = Surface2.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(13.dp)) {
            Icon(suggestion.icon, contentDescription = null, tint = Accent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(9.dp))
            Text(suggestion.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                suggestion.prompt,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OutlinedButtonLike(text: String, onClick: () -> Unit) {
    Surface(
        color = Surface2.copy(alpha = 0.8f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.size(9.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("→", color = Accent, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, context: android.content.Context) {
    if (message.role == Role.USER) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text(
                "DU",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
            )
            Surface(
                color = AccentSoft,
                shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .border(1.dp, Accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp))
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AccentText,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)
                )
            }
        }
        return
    }

    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(30.dp)
                .background(if (message.error) MaterialTheme.colorScheme.error else Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("A56", color = Ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "A56",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (message.error) MaterialTheme.colorScheme.error else Accent
                )
                message.meta?.let {
                    Text(
                        "  ·  $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (message.text.isBlank()) {
                Text("●  ●  ●", color = Accent, style = MaterialTheme.typography.bodyLarge)
            } else {
                SelectionContainer { MessageContent(message.text) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
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
    hardware: HardwareInfo,
    local: Boolean
) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .border(1.dp, if (generating) Accent.copy(alpha = 0.5f) else Line, RoundedCornerShape(24.dp))
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(if (local) Accent else MaterialTheme.colorScheme.secondary, CircleShape))
                Spacer(Modifier.size(6.dp))
                Text(
                    if (local) "Lokal · privat" else "Cloud · Gemini",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(Modifier.weight(1f))
                Text("Enter sendet · Shift+Enter Zeile", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(start = 5.dp, end = 5.dp, top = 0.dp, bottom = 5.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nachricht an dein Modell …", color = TextMuted) },
                    maxLines = 7,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                IconButton(
                    onClick = { if (generating) onStop() else onSend() },
                    modifier = Modifier
                        .size(42.dp)
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
    }
    Text(
        "${hardware.cpuSummary} · RAM ${hardware.ramAvailMb} MB frei · GPU ${hardware.gpu.ifBlank { "?" }}",
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ModelSheet(
    viewModel: AppViewModel,
    installed: List<InstalledModel>,
    selectedPath: String,
    cloudReady: Boolean,
    cloudModel: String,
    onSelectLocal: (String) -> Unit,
    onSelectCloud: () -> Unit,
    onManage: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Modell wählen", style = MaterialTheme.typography.titleMedium)
        Text(
            "Lokal auf dem A56 oder optional in der Cloud.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
        )

        Surface(
            color = if (cloudReady) Surface2 else Surface1,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (cloudReady) Line else Line, RoundedCornerShape(14.dp))
                .clickable { onSelectCloud() }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).background(Surface3, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("☁", color = Accent) }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Gemini Cloud", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (cloudReady) cloudModel else "API-Key in den Optionen hinterlegen",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("LOKALE GGUF-MODELLE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(4.dp))

        val gguf = installed.filter { !it.isLora && it.name.endsWith(".gguf", true) }
        if (gguf.isEmpty()) {
            EmptyHint("Kein GGUF installiert", "Unter „Modelle“ → Entdecken herunterladen.")
        } else {
            gguf.forEach { model ->
                val active = selectedPath == model.file.absolutePath
                Surface(
                    color = if (active) AccentSoft else Surface2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSelectLocal(model.file.absolutePath) }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                model.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                "${model.sizeMb} MB · GGUF",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        if (active) Icon(Icons.Filled.Check, contentDescription = "Aktiv", tint = Accent)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onManage) { Text("Modelle entdecken") }
        }
        Spacer(Modifier.height(12.dp))
    }
}
