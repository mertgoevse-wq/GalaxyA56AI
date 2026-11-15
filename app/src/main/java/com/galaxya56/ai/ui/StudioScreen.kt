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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxya56.ai.data.Catalog
import com.galaxya56.ai.data.ModelEntry
import com.galaxya56.ai.data.ModelType
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.TextMuted
import java.io.File

@Composable
fun StudioScreen(viewModel: AppViewModel) {
    var mode by rememberSaveable { mutableStateOf(ModelType.IMAGE) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var negative by rememberSaveable { mutableStateOf("") }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.studioBusy.collectAsStateWithLifecycle()
    val error by viewModel.studioError.collectAsStateWithLifecycle()
    val image by viewModel.studioImage.collectAsStateWithLifecycle()
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
                ModelType.IMAGE -> "Text → Bild. Cloud läuft sofort mit API-Key; lokale SD1.5-Unterstützung ist vorbereitet, aber in diesem Build noch nicht aktiv."
                ModelType.VIDEO -> "Text/Bild → Clip. Auf dem A56 sinnvoll: AnimateDiff (SD1.5, 16 Frames). Runtime folgt."
                else -> "Text → Musik, TTS und ASR. Kleinste Kandidaten: MusicGen Small, Stable Audio Open Small, Whisper."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp)
        )

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.generateImage(prompt, negative) },
                enabled = prompt.isNotBlank() && !busy && mode == ModelType.IMAGE,
                modifier = Modifier.weight(1f)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (busy) "Generiere …" else "Bild generieren")
            }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { openUrl(context, "file://${currentImage.absolutePath}") }) { Text("Datei") }
                TextButton(onClick = { viewModel.clearStudioImage() }) { Text("Entfernen") }
            }
            Text(currentImage.absolutePath, style = MaterialTheme.typography.labelSmall, color = TextMuted)
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
