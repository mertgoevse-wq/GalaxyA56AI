package com.galaxya56.ai.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxya56.ai.data.Catalog
import com.galaxya56.ai.data.ModelEntry
import com.galaxya56.ai.data.ModelHealth
import com.galaxya56.ai.data.ModelType
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.AccentSoft
import com.galaxya56.ai.ui.theme.Surface1
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.TextMuted
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(viewModel: AppViewModel) {
    var mode by rememberSaveable { mutableStateOf(ModelType.IMAGE) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var negative by rememberSaveable { mutableStateOf("") }
    var showImageModels by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val busy by viewModel.studioBusy.collectAsStateWithLifecycle()
    val error by viewModel.studioError.collectAsStateWithLifecycle()
    val image by viewModel.studioImage.collectAsStateWithLifecycle()
    val localImageModel by viewModel.localImageModel.collectAsStateWithLifecycle()
    val doctor by viewModel.doctor.collectAsStateWithLifecycle()
    val doctorBusy by viewModel.doctorBusy.collectAsStateWithLifecycle()
    val lastRecipe by viewModel.lastRecipe.collectAsStateWithLifecycle()
    val gallery by viewModel.gallery.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ModelType.IMAGE to "Bild", ModelType.VIDEO to "Video", ModelType.MUSIC to "Musik").forEach { (type, label) ->
                val selected = mode == type
                Surface(
                    color = if (selected) Accent else Surface2,
                    shape = RoundedCornerShape(50),
                    onClick = { mode = type }
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) com.galaxya56.ai.ui.theme.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            when (mode) {
                ModelType.IMAGE -> "Bilder erzeugen"
                ModelType.VIDEO -> "Videos erzeugen"
                else -> "Musik & Audio"
            },
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            when (mode) {
                ModelType.IMAGE -> "Text → Bild. Lokal über stable-diffusion.cpp (SD1.5, Vulkan/CPU) oder optional Cloud mit Gemini-Key."
                ModelType.VIDEO -> "Text/Bild → Clip. Auf dem A56 sinnvoll: AnimateDiff (SD1.5, 16 Frames). Runtime folgt."
                else -> "Text → Musik, TTS und ASR. Kleinste Kandidaten: MusicGen Small, Stable Audio Open Small, Whisper."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (mode == ModelType.IMAGE) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showImageModels = true }) {
                    Text(
                        "Modell: " + (settings.imageModelPath.substringAfterLast('/').ifBlank { "Auto (SD1.5)" })
                            .take(26),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Pill(
                    "VAE-Tiling ${if (settings.imageVaeTiling) "an" else "aus"}",
                    if (settings.imageVaeTiling) AccentSoft else Surface2,
                    if (settings.imageVaeTiling) Accent else TextMuted
                ) {
                    viewModel.updateSettings { it.copy(imageVaeTiling = !it.imageVaeTiling) }
                }
                Pill("CFG ${"%.1f".format(settings.imageCfg)}", Surface2)
                Pill("${settings.imageSteps} Steps", Surface2)
                Pill(settings.imageSampler, Surface2)
            }
            if (doctorBusy) {
                Text("Modell-Doctor prüft GGUF, RAM und Speicher …", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(top = 6.dp))
            }
            doctor?.let { report ->
                val color = when (report.health) {
                    ModelHealth.READY -> Accent
                    ModelHealth.WARNING -> MaterialTheme.colorScheme.tertiary
                    ModelHealth.INCOMPATIBLE -> MaterialTheme.colorScheme.error
                    ModelHealth.UNKNOWN -> TextMuted
                }
                Surface(
                    color = Surface2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Doctor: ${report.health.name} · ${report.family}", color = color, style = MaterialTheme.typography.labelSmall)
                        Text(report.reason, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            "${report.sizeMb} MB Modell · ${report.ramFreeMb} MB RAM frei · ${report.storageFreeMb} MB Speicher frei",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        if (report.hash.isNotBlank()) {
                            Text("Hash ${report.hash.take(16)}…", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        report.recipe?.let { recipe ->
                            Text(
                                "Empfehlung: ${recipe.steps} Steps · CFG ${recipe.cfg} · ${recipe.sampler} · ${recipe.width}×${recipe.height}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            if (report.health != ModelHealth.INCOMPATIBLE) {
                                TextButton(onClick = { viewModel.applyDoctorRecipe(report) }) { Text("Recipe anwenden") }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(if (mode == ModelType.MUSIC) "Musik-Beschreibung" else "Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedBorderColor = Accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        if (mode == ModelType.IMAGE) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (settings.imageSeed < 0) "" else settings.imageSeed.toString(),
                    onValueChange = { text ->
                        val value = text.trim().toLongOrNull() ?: -1L
                        viewModel.updateSettings { it.copy(imageSeed = value) }
                    },
                    label = { Text("Seed (-1 = zufällig)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                OutlinedButton(onClick = { viewModel.updateSettings { it.copy(imageSeed = -1L) } }) {
                    Text("Zufall")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = negative,
                onValueChange = { negative = it },
                label = { Text("Negativer Prompt (optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.generateLocalImage(prompt, negative) },
                enabled = prompt.isNotBlank() && !busy && mode == ModelType.IMAGE,
                modifier = Modifier.weight(1f)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (busy) "Generiere …" else "Lokal SD1.5")
            }
            OutlinedButton(
                onClick = { viewModel.generateImage(prompt, negative) },
                enabled = prompt.isNotBlank() && !busy && mode == ModelType.IMAGE && settings.cloudReady
            ) {
                Text("Cloud")
            }
        }
        if (mode == ModelType.IMAGE) {
            Text(
                when {
                    busy -> "Diffusion läuft auf CPU/GPU. ${settings.imageWidth}×${settings.imageHeight}, ${settings.imageSteps} Steps – auf der GPU deutlich schneller als auf der CPU."
                    localImageModel != null -> "Lokales Modell: $localImageModel"
                    else -> "Lokales SD1.5-GGUF wird beim ersten Bild automatisch geladen (Modell in Modelle → Bild)."
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (mode != ModelType.IMAGE) {
            Text(
                "Für diesen Modus ist in diesem Build noch keine lokale Runtime eingebunden. Die Modelle unten sind die passenden Kandidaten und lassen sich bereits herunterladen.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (!settings.cloudReady && mode == ModelType.IMAGE) {
            Text(
                "Kein Cloud-Key hinterlegt. Optionen → Cloud → Gemini API-Key.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }

        val currentImage = image
        if (currentImage != null) {
            Spacer(Modifier.height(14.dp))
            val bitmap = remember(currentImage.absolutePath) {
                BitmapFactory.decodeFile(currentImage.absolutePath)?.asImageBitmap()
            }
            if (bitmap != null) {
                Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Generiertes Bild",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        android.util.Log.d("A56AI-UI", "NPU button clicked busy=$busy")
                        viewModel.upscaleStudioImage()
                    },
                    enabled = !busy
                ) {
                    Text(if (busy) "NPU läuft …" else "NPU 4× Upscale")
                }
                OutlinedButton(onClick = { showGallery = true }) { Text("Galerie") }
                if (lastRecipe != null) {
                    TextButton(onClick = { viewModel.recreateLastImage() }, enabled = !busy) { Text("Recreate") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { openUrl(context, "file://${currentImage.absolutePath}") }) { Text("Datei") }
                TextButton(onClick = { viewModel.clearStudioImage() }) { Text("Entfernen") }
            }
            Text(currentImage.absolutePath, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            lastRecipe?.let { recipe ->
                Text(
                    "Seed ${recipe.seed} · ${recipe.steps} Steps · CFG ${recipe.cfg} · ${recipe.sampler} · ${recipe.modelName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text("Passende Modelle", style = MaterialTheme.typography.titleMedium)
        Text(
            "Aus dem Katalog — Downloads direkt oder über die Quellseite.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(Modifier.height(8.dp))

        Catalog.forType(mode).forEach { entry ->
            StudioModelRow(viewModel, entry, context)
        }

        if (mode == ModelType.MUSIC) {
            Catalog.forType(ModelType.AUDIO).forEach { entry ->
                StudioModelRow(viewModel, entry, context)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showImageModels) {
        ModalBottomSheet(onDismissRequest = { showImageModels = false }, containerColor = Surface1) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Diffusionsmodell", style = MaterialTheme.typography.titleMedium)
                Text(
                    "SD1.5, SD-Turbo oder ein anderes lokales GGUF/Safetensors-Checkpoint.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                Surface(
                    color = Surface2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    onClick = {
                        viewModel.selectImageModel("")
                        showImageModels = false
                    }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Automatisch", fontWeight = FontWeight.SemiBold)
                        Text("Bevorzugt ein installiertes SD1.5-/SD-Turbo-GGUF.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                installed.filter {
                    !it.isLora && (it.name.endsWith(".gguf", true) || it.name.endsWith(".safetensors", true))
                }.forEach { model ->
                    Surface(
                        color = if (settings.imageModelPath == model.file.absolutePath) AccentSoft else Surface2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        onClick = {
                            viewModel.selectImageModel(model.file.absolutePath)
                            showImageModels = false
                        }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(model.name, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text("${model.sizeMb} MB", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
                Text("Bild-LoRA", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Surface(
                    color = if (settings.imageLoraPath.isBlank()) AccentSoft else Surface2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    onClick = { viewModel.updateSettings { it.copy(imageLoraPath = "") } }
                ) { Text("Keine LoRA", modifier = Modifier.padding(12.dp)) }
                installed.filter { it.isLora && (it.name.endsWith(".safetensors", true) || it.name.endsWith(".gguf", true)) }
                    .forEach { lora ->
                        Surface(
                            color = if (settings.imageLoraPath == lora.file.absolutePath) AccentSoft else Surface2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            onClick = { viewModel.updateSettings { it.copy(imageLoraPath = lora.file.absolutePath) } }
                        ) { Text(lora.name, modifier = Modifier.padding(12.dp), maxLines = 1) }
                    }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showGallery) {
        ModalBottomSheet(onDismissRequest = { showGallery = false }, containerColor = Surface1) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Seed-/Metadaten-Galerie", style = MaterialTheme.typography.titleMedium)
                if (gallery.isEmpty()) {
                    Text("Noch keine Bilder mit Metadaten.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                gallery.take(20).forEach { item ->
                    Surface(
                        color = Surface2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(item.recipe.prompt.take(90), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Seed ${item.recipe.seed} · ${item.recipe.steps} Steps · CFG ${item.recipe.cfg} · ${item.recipe.sampler} · ${item.recipe.modelName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Row {
                                TextButton(onClick = { viewModel.loadStudioImage(item.image); showGallery = false }) { Text("Laden") }
                                TextButton(onClick = { viewModel.recreateImage(item); showGallery = false }) { Text("Recreate") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StudioModelRow(viewModel: AppViewModel, entry: ModelEntry, context: android.content.Context) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${entry.params} · ${entry.quant} · ${entry.sizeLabel} · empfohlen ab ${entry.minRamGb} GB RAM",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(entry.notes, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { openUrl(context, entry.pageUrl) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Quelle")
                }
                if (entry.downloadUrl != null) {
                    TextButton(onClick = { viewModel.installCurated(entry) }) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}
