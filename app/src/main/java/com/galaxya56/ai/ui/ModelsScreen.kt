package com.galaxya56.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxya56.ai.data.AppSettings
import com.galaxya56.ai.data.Catalog
import com.galaxya56.ai.data.DiscoveryKind
import com.galaxya56.ai.data.DiscoveryModel
import com.galaxya56.ai.data.ExtensionEntry
import com.galaxya56.ai.data.ExtensionStatus
import com.galaxya56.ai.data.Extensions
import com.galaxya56.ai.data.ModelDoctor
import com.galaxya56.ai.data.DownloadStatus
import com.galaxya56.ai.data.InstalledModel
import com.galaxya56.ai.data.ModelEntry
import com.galaxya56.ai.data.ModelSource
import com.galaxya56.ai.data.ModelType
import com.galaxya56.ai.data.RemoteFile
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.AccentSoft
import com.galaxya56.ai.ui.theme.Line
import com.galaxya56.ai.ui.theme.Surface1
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.Surface3
import com.galaxya56.ai.ui.theme.TextMuted
import kotlinx.coroutines.delay

private val tabs = listOf("Installiert", "Entdecken", "Katalog", "LoRA", "Extensions")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(viewModel: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(DiscoveryKind.ALL) }
    var sourceFilter by rememberSaveable { mutableStateOf<ModelSource?>(null) }
    var showAdultGate by remember { mutableStateOf(false) }

    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val sheet by viewModel.discoverySheet.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Automated discovery: opening the tab and typing both refresh results.
    // LoRA tab is a focused view over the same federated search.
    LaunchedEffect(tab, query, kind, sourceFilter, settings.nsfwEnabled, settings.adultConfirmed) {
        if (tab != 1 && tab != 3) return@LaunchedEffect
        delay(if (query.isBlank()) 80 else 450)
        viewModel.discover(
            query = query,
            kind = if (tab == 3) DiscoveryKind.LORA else kind,
            source = sourceFilter
        )
    }

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
                    onClick = {
                        tab = index
                        if (index == 3) kind = DiscoveryKind.LORA
                    },
                    text = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        val activeDownloads = downloads.values.filter {
            it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.ERROR
        }
        if (activeDownloads.isNotEmpty()) {
            activeDownloads.forEach { state ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "${state.name} · ${state.downloadedMb}/${state.totalMb} MB · ${
                            "%.1f".format(state.speedMBps)
                        } MB/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.status == DownloadStatus.ERROR) {
                        Text(
                            state.error ?: "Fehler",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
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
            0 -> InstalledTab(viewModel, installed, onBrowse = { tab = 1 })
            1 -> DiscoveryTab(
                query = query,
                onQueryChange = { query = it },
                kind = kind,
                onKindChange = { kind = it },
                source = sourceFilter,
                onSourceChange = { sourceFilter = it },
                remote = discovery,
                settings = settings,
                onOpen = { viewModel.openDiscoverySheet(it) },
                onRefresh = {
                    viewModel.discover(
                        query = query,
                        kind = if (tab == 3) DiscoveryKind.LORA else kind,
                        source = sourceFilter
                    )
                },
                onRequestAdultGate = { showAdultGate = true },
                onToggleNsfw = { enabled -> viewModel.setNsfw(enabled) },
                forcedLora = false
            )
            2 -> CatalogTab(viewModel, context)
            4 -> ExtensionsTab(onOpenLora = { tab = 3 })
            else -> DiscoveryTab(
                query = query,
                onQueryChange = { query = it },
                kind = DiscoveryKind.LORA,
                onKindChange = {},
                source = sourceFilter,
                onSourceChange = { sourceFilter = it },
                remote = discovery,
                settings = settings,
                onOpen = { viewModel.openDiscoverySheet(it) },
                onRefresh = {
                    viewModel.discover(query, DiscoveryKind.LORA, sourceFilter)
                },
                onRequestAdultGate = { showAdultGate = true },
                onToggleNsfw = { enabled -> viewModel.setNsfw(enabled) },
                forcedLora = true
            )
        }
    }

    val activeSheet = sheet
    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeDiscoverySheet() },
            containerColor = Surface1
        ) {
            DiscoverySheet(
                model = activeSheet,
                context = context,
                onDownload = { file -> viewModel.downloadDiscovery(activeSheet, file) }
            )
        }
    }

    if (showAdultGate) {
        AlertDialog(
            onDismissRequest = { showAdultGate = false },
            title = { Text("Erwachsenen-Inhalte freischalten") },
            text = {
                Text(
                    "Die Suche filtert keine Modelle oder Tags vor. NSFW-, abliterated- und " +
                        "uncensored-Modelle werden nach der Bestätigung unverändert angezeigt. " +
                        "Illegale Inhalte — insbesondere Darstellungen Minderjähriger — bleiben " +
                        "ausgeschlossen. Die Verantwortung für die Nutzung liegt bei dir."
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
private fun DiscoveryTab(
    query: String,
    onQueryChange: (String) -> Unit,
    kind: DiscoveryKind,
    onKindChange: (DiscoveryKind) -> Unit,
    source: ModelSource?,
    onSourceChange: (ModelSource?) -> Unit,
    remote: DiscoveryState,
    settings: AppSettings,
    onOpen: (DiscoveryModel) -> Unit,
    onRefresh: () -> Unit,
    onRequestAdultGate: () -> Unit,
    onToggleNsfw: (Boolean) -> Unit,
    forcedLora: Boolean
) {
    Column(Modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onRefresh,
            placeholder = if (forcedLora) "LoRA suchen (Bild oder Sprache)" else "Modelle suchen, z. B. abliterated, SDXL, Qwen",
            loading = remote.loading
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!forcedLora) {
                DiscoveryKind.entries.forEach { entry ->
                    FilterChip(
                        selected = kind == entry,
                        onClick = { onKindChange(entry) },
                        label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            } else {
                FilterChip(selected = true, onClick = {}, label = { Text("LoRAs · Bild + Sprache") })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quelle", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            SourceChip("Beide", source == null) { onSourceChange(null) }
            SourceChip("Hugging Face", source == ModelSource.HUGGINGFACE) {
                onSourceChange(ModelSource.HUGGINGFACE)
            }
            SourceChip("Civitai", source == ModelSource.CIVITAI) {
                onSourceChange(ModelSource.CIVITAI)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("18+ / NSFW / unzensiert", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (settings.adultConfirmed) {
                        "Keine Vorauswahl; alle Treffer der Quellen werden angezeigt."
                    } else {
                        "Altersfreigabe nötig. Keine spezielle Filterung nach Kategorien."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Switch(
                checked = settings.nsfwEnabled && settings.adultConfirmed,
                onCheckedChange = { checked ->
                    if (checked && !settings.adultConfirmed) onRequestAdultGate()
                    else onToggleNsfw(checked)
                }
            )
        }

        if (remote.loading && remote.results.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                Spacer(Modifier.height(10.dp))
                Text("Quellen werden automatisch durchsucht …", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }

        if (!remote.loading && remote.results.isEmpty()) {
            EmptyHint(
                if (remote.error != null) "Keine passenden Modelle" else "Noch keine Suche",
                remote.error ?: "Suchbegriff eingeben oder Quelle wechseln."
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onRefresh) { Text("Erneut suchen") }
                if (!settings.adultConfirmed) {
                    OutlinedButton(onClick = onRequestAdultGate) { Text("18+ freischalten") }
                }
            }
        }

        if (remote.results.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${remote.results.size} Treffer · ${if (source == null) "beide Quellen" else source.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(Modifier.weight(1f))
                if (remote.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Neu suchen", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(remote.results, key = { "${it.source}-${it.id}" }) { model ->
                DiscoveryCard(model, onOpen = { onOpen(model) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) AccentSoft else Surface2,
        contentColor = if (selected) Accent else TextMuted,
        shape = RoundedCornerShape(50),
        onClick = onClick
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun DiscoveryCard(model: DiscoveryModel, onOpen: () -> Unit) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        Column {
            model.previewUrls.firstOrNull()?.let { preview ->
                RemoteImage(
                    url = preview,
                    contentDescription = "Beispielbild für ${model.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            }
            Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (model.nsfw) Badge("18+", MaterialTheme.colorScheme.error)
                if (model.uncensored) {
                    Spacer(Modifier.size(4.dp))
                    Badge("uncensored", Accent)
                }
            }
            Text(
                listOfNotNull(
                    model.author.takeIf { it.isNotBlank() },
                    model.category.takeIf { it.isNotBlank() },
                    model.kind.takeIf { it.isNotBlank() },
                    model.baseModel.takeIf { it.isNotBlank() },
                    "↓ ${model.downloads}"
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 3.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 7.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Badge(model.source.label, Surface3)
                model.quantizations.take(5).forEach { quant -> Badge(quant, Surface1) }
                if (model.files.isNotEmpty()) {
                    Badge("${model.files.size} Dateien", Surface1)
                }
            }
            if (model.tags.isNotEmpty()) {
                Text(
                    model.tags.take(8).joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            }
        }
    }
}

@Composable
private fun ExtensionsTab(onOpenLora: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Extensions & Adapter", style = MaterialTheme.typography.headlineSmall)
            Text(
                "ControlNet, LoRAs, Video-/Audio-Adapter und NPU-Beschleuniger. " +
                    "Status „integriert“ bedeutet: in diesem Build nutzbar.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            OutlinedButton(onClick = onOpenLora, modifier = Modifier.fillMaxWidth()) {
                Text("LoRA-Suche öffnen (Bild + Sprache)")
            }
            Spacer(Modifier.height(8.dp))
        }
        items(Extensions.entries, key = { it.name }) { entry ->
            ExtensionCard(entry)
        }
        item {
            Text(
                "Weitere Extensions lassen sich über Hugging Face und Civitai suchen. " +
                    "Die App lädt nur Dateien, die sie auch ausführen kann; alles andere bleibt Quelle.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted,
                modifier = Modifier.padding(top = 12.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExtensionCard(entry: ExtensionEntry) {
    val context = LocalContext.current
    val statusColor = when (entry.status) {
        ExtensionStatus.READY -> Accent
        ExtensionStatus.COMPATIBLE -> MaterialTheme.colorScheme.secondary
        ExtensionStatus.EXPERIMENTAL -> MaterialTheme.colorScheme.tertiary
        ExtensionStatus.PLANNED -> TextMuted
    }
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Badge(entry.status.label, statusColor)
            }
            Text(entry.kind, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(entry.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            if (entry.hint.isNotBlank()) {
                Text(entry.hint, style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 4.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { openUrl(context, entry.url) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Quelle")
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (color == Surface1 || color == Surface3) TextMuted else color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DiscoverySheet(
    model: DiscoveryModel,
    context: android.content.Context,
    onDownload: (RemoteFile) -> Unit
) {
    Column(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(model.name, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(
                model.author.takeIf { it.isNotBlank() },
                model.category.takeIf { it.isNotBlank() },
                model.kind.takeIf { it.isNotBlank() },
                model.baseModel.takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openUrl(context, model.pageUrl) }) { Text("Quelle öffnen") }
            if (model.nsfw) Badge("18+", MaterialTheme.colorScheme.error)
            if (model.uncensored) Badge("uncensored", Accent)
        }
        if (model.previewUrls.isNotEmpty()) {
            Text(
                "Beispiele von Civitai",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 12.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                items(model.previewUrls) { url ->
                    RemoteImage(
                        url = url,
                        contentDescription = "Beispiel",
                        modifier = Modifier.width(220.dp).height(220.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
        if (model.quantizations.isNotEmpty()) {
            Text(
                "Quantisierungen: " + model.quantizations.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        ModelDoctor.remoteAdvice(model.name, model.baseModel)?.let { advice ->
            Text(
                "Doctor-Empfehlung: $advice",
                style = MaterialTheme.typography.labelSmall,
                color = if (advice.startsWith("Turbo")) MaterialTheme.colorScheme.tertiary else Accent,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 10.dp))

        if (model.files.isEmpty()) {
            EmptyHint(
                "Keine herunterladbare Datei",
                "Die Quelle liefert für dieses Modell kein GGUF/Safetensors/Checkpoint. " +
                    "Öffne die Quellseite oder wähle ein anderes Modell."
            )
        } else {
            model.files.forEach { file ->
                FileRow(file, model, onDownload = { onDownload(file) })
            }
        }
        Spacer(Modifier.height(24.dp))
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
            EmptyHint("Noch nichts installiert", "Lade ein Modell über Entdecken oder den Katalog.")
            Button(onClick = onBrowse) { Text("Zum Katalog") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
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
private fun FileRow(file: RemoteFile, model: DiscoveryModel, onDownload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    file.quant,
                    file.format.takeIf { it.isNotBlank() },
                    if (file.sizeMb > 0) "${file.sizeMb} MB" else "Größe beim Download",
                    model.source.label
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        IconButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, contentDescription = "Herunterladen", tint = Accent)
        }
    }
}
