package com.galaxya56.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxya56.ai.data.Catalog
import com.galaxya56.ai.data.StorageAdvisor
import com.galaxya56.ai.inference.LocalInference
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.TextMuted

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hardware by viewModel.hardware.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val npuProbe by viewModel.npuProbe.collectAsStateWithLifecycle()
    val npuProbeBusy by viewModel.npuProbeBusy.collectAsStateWithLifecycle()
    val ennProbe by viewModel.ennProbe.collectAsStateWithLifecycle()
    val ennBenchmark by viewModel.ennBenchmark.collectAsStateWithLifecycle()
    val ennBusy by viewModel.ennBusy.collectAsStateWithLifecycle()
    val benchmark by viewModel.benchmark.collectAsStateWithLifecycle()
    val benchmarkBusy by viewModel.benchmarkBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var customPrompt by rememberSaveable(settings.customSystemPrompt) { mutableStateOf(settings.customSystemPrompt) }
    var showAdultGate by remember { mutableStateOf(false) }
    var hfToken by rememberSaveable(settings.hfToken) { mutableStateOf(settings.hfToken) }
    var civitaiToken by rememberSaveable(settings.civitaiToken) { mutableStateOf(settings.civitaiToken) }
    var geminiKey by rememberSaveable(settings.geminiKey) { mutableStateOf(settings.geminiKey) }
    var storage by remember { mutableStateOf(StorageAdvisor.inspect(context)) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.importFolderFromTree(uri)
            storage = StorageAdvisor.inspect(context)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsCard("Gerät") {
            LabelledValue("Modell", "${hardware.manufacturer} ${hardware.device}".trim())
            LabelledValue("SoC", hardware.soc)
            LabelledValue("Android", "${hardware.android} (API ${hardware.api})")
            LabelledValue("CPU", hardware.cpuSummary)
            LabelledValue("GPU", "${hardware.gpu} · Vulkan ${hardware.gpuApi}${if (hardware.gpuRayTracing) " · RT" else ""}")
            LabelledValue("NPU", hardware.npuSummary)
            if (hardware.npuNames.isNotBlank()) LabelledValue("NNAPI", hardware.npuNames)
            LabelledValue("RAM", "${hardware.ramAvailMb} / ${hardware.ramTotalMb} MB frei")
            LabelledValue(
                "Thermik",
                if (hardware.thermalC > 0f)
                    "${"%.1f".format(hardware.thermalC)} °C / Throttle ${"%.0f".format(hardware.throttleC)} °C"
                else "Sensor auf Android 17 nicht lesbar (SELinux)",
                hardware.thermalWarning
            )
            LabelledValue("Runtime", LocalInference.backendName())
        }

        SettingsCard("Speicher, ZRAM & USB-C") {
            Text(
                "Android verwaltet ZRAM/SWAP selbst. Eine normale App kann beides ohne Root nicht " +
                    "einschalten; sie kann aber den Ist-Zustand zeigen und Modelle per mmap nur " +
                    "seitenweise in den RAM holen.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            LabelledValue("RAM", "${storage.ramAvailMb} / ${storage.ramTotalMb} MB frei")
            LabelledValue("App-Speicher", "${storage.appFreeMb} MB frei")
            LabelledValue("ZRAM", if (storage.zramMb > 0) "${storage.zramMb} MB" else "nicht sichtbar")
            LabelledValue("Swap", storage.swapSummary)
            storage.removableVolumes.forEach { volume ->
                LabelledValue("USB-C", volume)
            }
            Text(
                "Modelle auf USB-C: Ordner auswählen, die App kopiert GGUF/Safetensors/NNC in " +
                    "den App-Speicher. Danach sind sie offline und ohne Stick nutzbar.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { folderPicker.launch(null) }) { Text("Aus USB-C/Ordner importieren") }
                OutlinedButton(onClick = { storage = StorageAdvisor.inspect(context) }) { Text("Aktualisieren") }
            }
            LabelledValue("Modellordner", storage.modelDir)
        }

        SettingsCard("Backend-Reihenfolge") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto", "npu", "gpu", "cpu").forEach { backend ->
                    val selected = settings.backend == backend
                    Surface(
                        color = if (selected) Accent else Surface2,
                        shape = RoundedCornerShape(50),
                        onClick = { viewModel.updateSettings { it.copy(backend = backend) } }
                    ) {
                        Text(
                            backend.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) com.galaxya56.ai.ui.theme.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Text(
                "Fallback-Kette: NPU (nur kompatible .nnc-Modelle) → Vulkan-GPU → CPU. " +
                    "GGUF-LLMs und SD-Checkpoints haben keinen stabilen NPU-Backend; sie laufen " +
                    "daher automatisch auf Vulkan und bei Fehler auf CPU. Die NPU wird für den " +
                    "MIDAS-4×-Upscaler im Studio genutzt.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
            Button(
                onClick = { viewModel.runNnapiProbe() },
                enabled = !npuProbeBusy,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (npuProbeBusy) "NNAPI-Test läuft …" else "NNAPI/NPU-Test ausführen")
            }
            npuProbe?.let { report ->
                Surface(
                    color = Surface2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text(
                        report,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        SettingsCard("Samsung ENN (NPU)") {
            Text(
                "ENN ist Samsungs öffentliche Neural-API für Exynos. Die Runtime wird zur Laufzeit " +
                    "aus der Vendor-Lib geladen (kein proprietäres Binary im Repo). Auf dem A56 läuft " +
                    "der geräteeigene MIDAS-4×-Upscaler (256→1024) auf der NPU; der Test nutzt ihn.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            LabelledValue("Modellpfad", "Android/data/com.galaxya56.ai/files/models/*.nnc")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.runEnnProbe() }, enabled = !ennBusy) {
                    Text(if (ennBusy) "ENN läuft …" else "ENN/NPU prüfen")
                }
                OutlinedButton(onClick = { viewModel.runEnnBenchmark() }, enabled = !ennBusy) {
                    Text("MIDAS 4× testen")
                }
            }
            ennProbe?.let { report ->
                Surface(
                    color = Surface2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text(report, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(10.dp))
                }
            }
            ennBenchmark?.let { report ->
                Surface(
                    color = Surface2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text(report, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(10.dp))
                }
            }
        }

        SettingsCard("NPU-Upscale") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-4×-Upscale nach Bild", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Nutzt den geräteeigenen MIDAS-ENN-Pfad (256→1024). " +
                            "Wenn der Pfad fehlt, bleibt das erzeugte Bild unverändert.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = settings.npuAutoUpscale,
                    onCheckedChange = { viewModel.updateSettings { s -> s.copy(npuAutoUpscale = it) } }
                )
            }
        }

        SettingsCard("Bild-Runtime (SD1.5)") {
            Text(
                "Lokale Diffusion über stable-diffusion.cpp. Kleinere Auflösung und weniger Steps " +
                    "verhindern Thermal-Throttling; 384×384/8 Steps ist der sichere A56-Default.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            SliderRow(
                label = "Breite",
                value = settings.imageWidth.toFloat(),
                valueText = "${settings.imageWidth} px",
                range = 256f..512f,
                steps = 8,
                onChange = { viewModel.updateSettings { s -> s.copy(imageWidth = it.toInt()) } }
            )
            SliderRow(
                label = "Höhe",
                value = settings.imageHeight.toFloat(),
                valueText = "${settings.imageHeight} px",
                range = 256f..512f,
                steps = 8,
                onChange = { viewModel.updateSettings { s -> s.copy(imageHeight = it.toInt()) } }
            )
            SliderRow(
                label = "Steps",
                value = settings.imageSteps.toFloat(),
                valueText = "${settings.imageSteps}",
                range = 1f..20f,
                steps = 19,
                onChange = { viewModel.updateSettings { s -> s.copy(imageSteps = it.toInt()) } }
            )
            SliderRow(
                label = "CFG",
                value = settings.imageCfg,
                valueText = "%.1f".format(settings.imageCfg),
                range = 1f..12f,
                steps = 21,
                onChange = { viewModel.updateSettings { s -> s.copy(imageCfg = it) } }
            )
            Text("Sampler", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("euler_a", "euler", "dpm++2m", "lcm").forEach { sampler ->
                    val selected = settings.imageSampler == sampler
                    Surface(
                        color = if (selected) Accent else Surface2,
                        shape = RoundedCornerShape(50),
                        onClick = { viewModel.updateSettings { s -> s.copy(imageSampler = sampler) } }
                    ) {
                        Text(
                            sampler,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) com.galaxya56.ai.ui.theme.Ink else TextMuted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VAE-Tiling", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Teilt den VAE-Decoder in Kacheln; senkt den VRAM-Peak, kostet etwas Zeit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = settings.imageVaeTiling,
                    onCheckedChange = { viewModel.updateSettings { s -> s.copy(imageVaeTiling = it) } }
                )
            }
            LabelledValue("Modell", settings.imageModelPath.ifBlank { "auto: SD1.5/SD-Turbo" })
            LabelledValue("Bild-LoRA", settings.imageLoraPath.ifBlank { "keine" })
            if (settings.imageLoraPath.isNotBlank()) {
                SliderRow(
                    label = "LoRA-Stärke",
                    value = settings.imageLoraScale,
                    valueText = "%.2f".format(settings.imageLoraScale),
                    range = 0f..1.5f,
                    steps = 15,
                    onChange = { viewModel.updateSettings { s -> s.copy(imageLoraScale = it) } }
                )
            }
        }

        SettingsCard("GPU-Layer-Benchmark") {
            Text(
                "Lädt das aktive GGUF mit 0/25/50/75/100 % Offload und misst echte tok/s. " +
                    "Nur mit -Pvulkan=true gebauten APKs sinnvoll.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Button(
                onClick = { viewModel.runGpuLayerBenchmark() },
                enabled = !benchmarkBusy,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (benchmarkBusy) "Benchmark läuft …" else "GPU-Layer benchmarken")
            }
            benchmark.forEach { row ->
                LabelledValue(
                    "${row.percent} % · ${row.layers} Layer",
                    if (row.error != null) "Fehler: ${row.error}"
                    else "%.1f tok/s · Prompt %d ms · %s".format(row.tokensPerSecond, row.promptMs, row.backend)
                )
            }
        }

        SettingsCard("Generierung") {
            SliderRow(
                label = "Kontext",
                value = settings.contextSize.toFloat(),
                valueText = "${settings.contextSize} Tokens",
                range = 1024f..8192f,
                steps = 13,
                onChange = { viewModel.updateSettings { s -> s.copy(contextSize = it.toInt()) } }
            )
            SliderRow(
                label = "Threads",
                value = settings.threads.toFloat(),
                valueText = "${settings.threads}",
                range = 1f..(hardware.cpuCores.coerceAtLeast(4).toFloat()),
                steps = 6,
                onChange = { viewModel.updateSettings { s -> s.copy(threads = it.toInt().coerceAtLeast(1)) } }
            )
            SliderRow(
                label = "GPU-Layer (Vulkan)",
                value = settings.gpuLayers.toFloat(),
                valueText = if (settings.gpuLayers == 0) "0 · CPU" else "${settings.gpuLayers} · CPU+GPU",
                range = 0f..99f,
                steps = 0,
                onChange = { viewModel.updateSettings { s -> s.copy(gpuLayers = it.toInt()) } }
            )
            SliderRow(
                label = "Temperatur",
                value = settings.temperature,
                valueText = "%.2f".format(settings.temperature),
                range = 0f..1.5f,
                steps = 14,
                onChange = { viewModel.updateSettings { s -> s.copy(temperature = it) } }
            )
            SliderRow(
                label = "Top-P",
                value = settings.topP,
                valueText = "%.2f".format(settings.topP),
                range = 0.1f..1f,
                steps = 8,
                onChange = { viewModel.updateSettings { s -> s.copy(topP = it) } }
            )
            SliderRow(
                label = "Max. Tokens",
                value = settings.maxTokens.toFloat(),
                valueText = "${settings.maxTokens}",
                range = 128f..2048f,
                steps = 14,
                onChange = { viewModel.updateSettings { s -> s.copy(maxTokens = it.toInt()) } }
            )
        }

        SettingsCard("System-Prompt") {
            Catalog.prompts.forEach { prompt ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.updateSettings { it.copy(systemPromptId = prompt.id, customSystemPrompt = "") } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            prompt.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (settings.systemPromptId == prompt.id && settings.customSystemPrompt.isBlank()) Accent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            OutlinedTextField(
                value = customPrompt,
                onValueChange = {
                    customPrompt = it
                    viewModel.updateSettings { s -> s.copy(customSystemPrompt = it) }
                },
                label = { Text("Eigener Prompt (überschreibt Auswahl)") },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                minLines = 3,
                colors = fieldColors()
            )
        }

        SettingsCard("Cloud (optional)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gemini API", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Chat + Bildgenerierung über Google AI Studio. API-Key, kein Kontopasswort.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = settings.cloudEnabled,
                    onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(cloudEnabled = enabled) } }
                )
            }
            OutlinedTextField(
                value = geminiKey,
                onValueChange = {
                    geminiKey = it
                    viewModel.updateSettings { s -> s.copy(geminiKey = it) }
                },
                label = { Text("Gemini API-Key") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash").forEach { model ->
                    Surface(
                        color = if (settings.cloudModel == model) Accent else Surface2,
                        shape = RoundedCornerShape(50),
                        onClick = { viewModel.updateSettings { it.copy(cloudModel = model) } }
                    ) {
                        Text(
                            model.removePrefix("gemini-"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (settings.cloudModel == model) com.galaxya56.ai.ui.theme.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Text(
                "Hinweis: Ein Google-AI-Pro-Abo gilt für die Gemini-App und lässt sich nicht per E-Mail/Passwort in Dritt-Apps nutzen. " +
                    "Erstelle in Google AI Studio einen API-Key und hinterlege ihn hier.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
            TextButton(onClick = { openUrl(context, "https://aistudio.google.com/apikey") }) {
                Text("API-Key erstellen", style = MaterialTheme.typography.labelSmall)
            }
        }

        SettingsCard("Konten & Tokens") {
            OutlinedTextField(
                value = hfToken,
                onValueChange = {
                    hfToken = it
                    viewModel.updateSettings { s -> s.copy(hfToken = it.trim()) }
                },
                label = { Text("Hugging Face Token (gated/private Modelle)") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors()
            )
            OutlinedTextField(
                value = civitaiToken,
                onValueChange = {
                    civitaiToken = it
                    viewModel.updateSettings { s -> s.copy(civitaiToken = it.trim()) }
                },
                label = { Text("Civitai API-Token (NSFW/Downloads)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors()
            )
            TextButton(onClick = { openUrl(context, "https://civitai.com/user/account") }) {
                Text("Civitai-Token erstellen", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "Tokens liegen lokal in den App-Einstellungen und werden nur an die jeweilige API gesendet.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        SettingsCard("Inhalte für Erwachsene") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NSFW / unzensierte Modelle", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Zeigt Civitai-NSFW-Modelle und Abliterated-/Uncensored-Suchen. Nur für Volljährige, keine illegalen Inhalte.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = settings.nsfwEnabled && settings.adultConfirmed,
                    onCheckedChange = { checked ->
                        if (checked && !settings.adultConfirmed) showAdultGate = true
                        else viewModel.setNsfw(checked)
                    }
                )
            }
        }

        SettingsCard("LoRA-Adapter") {
            val loras = installed.filter { it.isLora }
            if (loras.isEmpty()) {
                Text(
                    "Keine LoRA installiert. Unter Modelle → LoRA nach GGUF-Adaptern suchen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            } else {
                loras.forEach { lora ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(lora.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { viewModel.applyLora(lora.file, settings.loraScale) }) { Text("Anwenden") }
                    }
                }
            }
            SliderRow(
                label = "LoRA-Stärke",
                value = settings.loraScale,
                valueText = "%.2f".format(settings.loraScale),
                range = 0f..1.5f,
                steps = 14,
                onChange = { viewModel.updateSettings { s -> s.copy(loraScale = it) } }
            )
            TextButton(onClick = { viewModel.clearLoras() }) { Text("Alle LoRAs entfernen") }
        }

        SettingsCard("Über") {
            LabelledValue("App", "Local AI Studio · Galaxy A56")
            LabelledValue("Version", "2.0.0 (Build 2)")
            LabelledValue("LLM-Runtime", LocalInference.backendName())
            LabelledValue("Modelle", "${installed.size} Dateien")
            LabelledValue("Speicher", viewModel.modelStore.modelsDir.absolutePath)
            LinkRow("GitHub-Repository", "https://github.com/mertgoevse-wq/GalaxyA56AI")
            LinkRow("llama.cpp", "https://github.com/ggml-org/llama.cpp")
            LinkRow("Hugging Face", Catalog.HF_BROWSE)
            LinkRow("Civitai", Catalog.CIVITAI_BROWSE)
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showAdultGate) {
        AlertDialog(
            onDismissRequest = { showAdultGate = false },
            title = { Text("Bestätigung erforderlich") },
            text = {
                Text(
                    "Ich bin volljährig und möchte NSFW-/unzensierte Modelle sehen. " +
                        "Illegale Inhalte sind ausgeschlossen; die Nutzung erfolgt auf eigene Verantwortung."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.enableAdultContent(true)
                    showAdultGate = false
                }) { Text("Bestätigen") }
            },
            dismissButton = { TextButton(onClick = { showAdultGate = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(color = Surface2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = Accent)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.height(30.dp)
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = com.galaxya56.ai.ui.theme.Surface1,
    unfocusedContainerColor = com.galaxya56.ai.ui.theme.Surface1,
    focusedBorderColor = Accent,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline
)
