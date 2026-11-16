package com.galaxya56.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.AccentSoft
import com.galaxya56.ai.ui.theme.Ink
import com.galaxya56.ai.ui.theme.Surface1
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.TextMuted

private data class TutorialPage(
    val title: String,
    val intro: String,
    val steps: List<String>,
    val tip: String
)

private val pages = listOf(
    TutorialPage(
        title = "In 60 Sekunden zum Bild",
        intro = "Der kürzeste sichere Weg auf dem A56. Nimm beim ersten Lauf 384 × 384 und 4–8 Steps.",
        steps = listOf(
            "Modelle → Installiert: ein SD1.5- oder LCM-GGUF auswählen.",
            "Studio → Bild: der Doctor prüft Familie, RAM und empfiehlt Steps/CFG.",
            "„Recipe anwenden“ tippen, dann einen konkreten englischen Prompt eingeben.",
            "Erzeugen. Beim ersten Lauf wird das Modell geladen; das dauert länger als das Bild.",
            "Ergebnis in der Galerie öffnen und „Recreate“ für denselben Seed testen."
        ),
        tip = "Ein Seed ist deine Zeitmaschine: gleicher Seed + gleiche Einstellungen = vergleichbares Bild."
    ),
    TutorialPage(
        title = "Bildqualität steuern",
        intro = "Steps, CFG, Sampler und Auflösung sind keine Magie, sondern vier getrennte Regler.",
        steps = listOf(
            "Steps: 4 bei LCM/Hyper-SD, 8–12 bei SD1.5. Mehr Steps kosten Zeit, nicht automatisch Qualität.",
            "CFG: 1,5 bei LCM, 6–7 bei SD1.5. Zu hoch verbrennt Farben und Details.",
            "Sampler: lcm für LCM, euler_a als robuster Standard, dpm++2m für mehr Struktur.",
            "VAE-Tiling bei 512er-Bildern einschalten, wenn der Speicher knapp wird.",
            "Bei 512 × 512 bleiben: SD1.5 wurde darauf trainiert; 384 ist der Thermik-Kompromiss."
        ),
        tip = "Erst eine Variable ändern, dann vergleichen. Sonst weißt du nie, welcher Regler geholfen hat."
    ),
    TutorialPage(
        title = "ControlNet",
        intro = "ControlNet gibt dem Bild eine räumliche Vorgabe: Kanten, Tiefe, Pose oder Scribble.",
        steps = listOf(
            "Ein SD1.5-ControlNet (z. B. control_v11p_sd15_canny) unter Modelle installieren.",
            "Studio → ControlNet → Modell wählen. Es wird beim nächsten Bild in den Kontext geladen.",
            "Control-Bild wählen: eine klare Zeichnung, ein Foto oder eine Tiefenkarte.",
            "Stärke startet bei 0,8–1,0. Höher = strenger, niedriger = mehr Prompt-Freiheit.",
            "Canny nur für Kantenmodelle/Union aktivieren; sonst das Bild direkt als Control benutzen."
        ),
        tip = "Wenn das Ergebnis das Control-Bild ignoriert: Stärke erhöhen und den Prompt einfacher halten."
    ),
    TutorialPage(
        title = "Musik über 4 Minuten",
        intro = "Der lokale Psytrance-Renderer ist eine DSP-Engine, kein neuronales Modell. Dafür läuft er zuverlässig und offline.",
        steps = listOf(
            "Studio → Musik. BPM 136–142 ist der Progressive-Psytrance-Korridor.",
            "Dauer auf 4,5 Minuten stellen; 8 Minuten sind ebenfalls möglich, brauchen aber mehr Speicher.",
            "Prompt beschreibt die Stimmung („hypnotic“, „dark“, „melodic“); der Seed steuert das Detail.",
            "„Psytrance erzeugen“ und den Fortschritt beobachten. Das WAV landet in generated/music.",
            "„Anhören“ startet die interne Wiedergabe; die Datei bleibt lokal."
        ),
        tip = "Der Breakdown liegt bei etwa zwei Dritteln; der zweite Drop kommt danach. Genau so ist die Arrangement-Kurve gebaut."
    ),
    TutorialPage(
        title = "Video & Modelle",
        intro = "Video ist auf 8 GB RAM der teuerste Pfad. Kleine Modelle und wenige Frames zuerst.",
        steps = listOf(
            "Wan 2.1 T2V 1.3B ist der kleinste sinnvolle Kandidat, braucht aber viel RAM.",
            "Mit 16 Frames und 512 × 512 starten; jedes zusätzliche Frame kostet Zeit und Speicher.",
            "Das Modell muss als GGUF/Safetensors installiert sein; die Video-Runtime wird nur bei unterstützten Checkpoints aktiv.",
            "Bei Thermal-Status 3 pausieren und das Gerät abkühlen lassen.",
            "Für lange Clips lieber mehrere kurze Segmente erzeugen und später zusammenschneiden."
        ),
        tip = "Ein 16-Frame-Clip ist ein Test, kein Film. Realistisch sind kurze Einstellungen mit 8–12 fps."
    ),
    TutorialPage(
        title = "Speicher, USB-C & NPU",
        intro = "RAM ist die härteste Grenze. Die App nutzt mmap, damit nur benötigte Modellseiten in den RAM kommen.",
        steps = listOf(
            "Große Modelle auf USB-C kopieren: Optionen → Speicher → „Aus USB-C/Ordner importieren“.",
            "Modelle bleiben danach im App-Speicher; der native Loader braucht einen echten Dateipfad.",
            "ZRAM/SWAP kann eine App ohne Root nicht aktivieren. Das System managed es; die App zeigt dir den Ist-Zustand.",
            "NPU: MIDAS-4×-Upscale ist der verifizierte Pfad. Diffusion/LLM laufen über Vulkan und CPU-Fallback.",
            "Bei Speicherdruck zuerst VAE-Tiling, dann kleinere Auflösung, dann kleineres Modell."
        ),
        tip = "mmap ersetzt kein RAM-Upgrade: Es verhindert nur, dass die ganze Datei auf einmal geladen wird."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialSheet(onDismiss: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val current = pages[page.coerceIn(pages.indices)]

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Werkstatt-Handbuch", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Interaktive Anleitung · ${page + 1} von ${pages.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
            LinearProgressIndicator(
                progress = { (page + 1f) / pages.size },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = Accent
            )
            Spacer(Modifier.height(18.dp))
            Text(current.title, style = MaterialTheme.typography.titleMedium)
            Text(current.intro, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(12.dp))
            current.steps.forEachIndexed { index, step ->
                var checked by rememberSaveable(current.title, index) { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(if (checked) AccentSoft else Surface2, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text(
                        step,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (checked) Accent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            Surface(color = Surface2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Werkstatt-Notiz", style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.SemiBold)
                    Text(current.tip, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { page = (page - 1).coerceAtLeast(0) },
                    enabled = page > 0
                ) { Text("Zurück") }
                Spacer(Modifier.weight(1f))
                if (page < pages.lastIndex) {
                    Button(onClick = { page++ }) { Text("Weiter") }
                } else {
                    Button(onClick = onDismiss) { Text("Fertig") }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
