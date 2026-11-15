package com.galaxya56.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxya56.ai.data.Catalog
import com.galaxya56.ai.data.DownloadStatus
import com.galaxya56.ai.data.InstalledModel
import com.galaxya56.ai.data.ModelEntry
import com.galaxya56.ai.data.ModelSource
import com.galaxya56.ai.data.ModelType
import com.galaxya56.ai.data.RemoteFile
import com.galaxya56.ai.data.RemoteModel
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.Surface1
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.TextMuted

private val tabs = listOf("Installiert", "Katalog", "Hugging Face", "Civitai", "LoRA")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(viewModel: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var loraSearch by rememberSaveable { mutableStateOf(false) }
    var showAdultGate by remember { mutableStateOf(false) }

    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val remote by viewModel.remote.collectAsStateWithLifecycle()
    val remoteFiles by viewModel.remoteFiles.collectAsStateWithLifecycle()
    val fileSheet by viewModel.fileSheet.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Accent,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        val activeDownloads = downloads.values.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.ERROR }
        if (activeDownloads.isNotEmpty()) {
            activeDownloads.forEach { state ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "${state.name} · ${state.downloadedMb}/${state.totalMb} MB · ${"%.1f".format(state.speedMBps)} MB/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.status == DownloadStatus.ERROR) {
                        Text(state.error ?: "Fehler", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                            color = Accent
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        when (tab) {
            0 -> InstalledTab(viewModel, installed, onBrowse = { tab = 2 })
            1 -> CatalogTab(viewModel, context)
            2, 4 -> {
                val currentLora = tab == 4
                Column(Modifier.fillMaxSize()) {
                    SearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { viewModel.searchRemote(query, ModelSource.HUGGINGFACE, currentLora) },
                        placeholder = if (currentLora) "LoRA suchen (z. B. style, detail)" else "GGUF suchen (z. B. qwen coder, abliterated)",
                        loading = remote.loading
                    )
                    if (currentLora) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = true, onClick = {}, label = { Text("nur LoRA") })
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Adapter werden als GGUF-LoRA geladen.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                    if (remote.error != null) {
                        Text(remote.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp))
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(remote.results, key = { "${it.source}-${it.id}" }) { model ->
                            RemoteRow(model, settings.nsfwEnabled) { viewModel.openFileSheet(model) }
                        }
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    SearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { viewModel.searchRemote(query, ModelSource.CIVITAI, false) },
                        placeholder = "Civitai durchsuchen (Checkpoints, LoRAs)",
                        loading = remote.loading
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NSFW anzeigen", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(8.dp))
                        Switch(
                            checked = settings.nsfwEnabled && settings.adultConfirmed,
                            onCheckedChange = { checked ->
                                if (checked && !settings.adultConfirmed) showAdultGate = true
                                else viewModel.setNsfw(checked)
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { openUrl(context, Catalog.CIVITAI_BROWSE) }) {
                            Text("civitai.com", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (settings.civitaiToken.isBlank()) {
                        Text(
                            "Für viele Downloads (und alle NSFW-Inhalte) verlangt Civitai einen API-Token. Hinterlege ihn unter Optionen → Konten.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    if (remote.error != null) {
                        Text(remote.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp))
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(remote.results, key = { "${it.source}-${it.id}" }) { model ->
                            RemoteRow(model, settings.nsfwEnabled) { viewModel.openFileSheet(model) }
                        }
                    }
                }
            }
        }
    }

    val sheet = fileSheet
    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.closeFileSheet() }, containerColor = Surface1) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
                Text(sheet.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${sheet.author} · ${sheet.kind}${if (sheet.baseModel.isNotBlank()) " · ${sheet.baseModel}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openUrl(context, sheet.pageUrl) }) { Text("Quelle öffnen") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 10.dp))

                val files = remoteFiles[sheet.id].orEmpty()
                if (files.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                        Spacer(Modifier.size(10.dp))
                        Text("Dateiliste wird geladen … (oder keine Dateien gefunden)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                } else {
                    files.forEach { file ->
                        FileRow(file, sheet) { viewModel.downloadRemote(sheet, file) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAdultGate) {
        AlertDialog(
            onDismissRequest = { showAdultGate = false },
            title = { Text("Inhalte für Erwachsene") },
            text = {
                Text(
                    "NSFW-Modelle und erotische Inhalte sind nur für volljährige Nutzer bestimmt. " +
                        "Illegale Inhalte — insbesondere Darstellungen Minderjähriger — sind ausdrücklich ausgeschlossen. " +
                        "Die Verantwortung für die Nutzung liegt bei dir."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.enableAdultContent(true)
                    showAdultGate = false
                }) { Text("Ich bin 18+ und einverstanden") }
            },
            dismissButton = { TextButton(onClick = { showAdultGate = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    loading: Boolean
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedBorderColor = Accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        IconButton(onClick = onSearch, enabled = !loading) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
            else Icon(Icons.Filled.Search, contentDescription = "Suchen", tint = Accent)
        }
    }
}

@Composable
private fun InstalledTab(viewModel: AppViewModel, installed: List<InstalledModel>, onBrowse: () -> Unit) {
    if (installed.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyHint("Noch nichts installiert", "Lade ein GGUF-Modell aus dem Katalog oder direkt von Hugging Face.")
            Button(onClick = onBrowse) { Text("Zum Katalog") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        items(installed, key = { it.file.absolutePath }) { model ->
            Surface(
                color = Surface2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(model.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${model.sizeMb} MB · ${if (model.isLora) "LoRA" else if (model.name.endsWith(".gguf", true)) "GGUF" else "Datei"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!model.isLora && model.name.endsWith(".gguf", true)) {
                            OutlinedButton(onClick = { viewModel.loadModelNow(model.file.absolutePath) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Laden")
                            }
                            Spacer(Modifier.size(8.dp))
                        }
                        if (model.isLora) {
                            OutlinedButton(onClick = { viewModel.applyLora(model.file, viewModel.settings.value.loraScale) }) {
                                Text("Anwenden")
                            }
                            Spacer(Modifier.size(8.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.deleteInstalled(model) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Löschen", tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogTab(viewModel: AppViewModel, context: android.content.Context) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        ModelType.entries.forEach { type ->
            val entries = Catalog.forType(type)
            if (entries.isEmpty()) return@forEach
            item {
                Text(
                    type.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
            }
            items(entries, key = { it.id }) { entry ->
                CuratedCard(entry, downloads[entry.id]?.status) {
                    if (entry.downloadUrl != null) viewModel.installCurated(entry)
                    else openUrl(context, entry.pageUrl)
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { openUrl(context, Catalog.HF_BROWSE) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Alle GGUF-Modelle auf Hugging Face")
            }
        }
    }
}

@Composable
private fun CuratedCard(entry: ModelEntry, status: DownloadStatus?, onAction: () -> Unit) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (entry.nsfw) Pill("18+", MaterialTheme.colorScheme.error.copy(alpha = 0.2f), MaterialTheme.colorScheme.error)
            }
            Text(
                "${entry.author} · ${entry.params} · ${entry.quant} · ${entry.sizeLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            if (entry.notes.isNotBlank()) {
                Text(entry.notes, style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(entry.runtime.label, Surface1, if (entry.runtime == com.galaxya56.ai.data.ModelRuntime.LLAMA_GGUF) Accent else TextMuted)
                entry.backends.forEach { Pill(it, Surface1) }
                Spacer(Modifier.weight(1f))
                when (status) {
                    DownloadStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                    else -> TextButton(onClick = onAction) {
                        Text(if (entry.downloadUrl != null) "Download" else "Quelle")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteRow(model: RemoteModel, nsfwAllowed: Boolean, onClick: () -> Unit) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (model.nsfw) {
                    Spacer(Modifier.size(6.dp))
                    Pill("18+", MaterialTheme.colorScheme.error.copy(alpha = 0.2f), MaterialTheme.colorScheme.error)
                }
            }
            Text(
                listOfNotNull(
                    model.author.takeIf { it.isNotBlank() },
                    model.kind.takeIf { it.isNotBlank() },
                    model.baseModel.takeIf { it.isNotBlank() },
                    "↓ ${model.downloads}"
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            if (model.nsfw && !nsfwAllowed) {
                Text("NSFW — in Civitai aktivieren", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FileRow(file: RemoteFile, model: RemoteModel, onDownload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (file.sizeMb > 0) "${file.sizeMb} MB · ${model.source.label}" else model.source.label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        IconButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, contentDescription = "Herunterladen", tint = Accent)
        }
    }
}
