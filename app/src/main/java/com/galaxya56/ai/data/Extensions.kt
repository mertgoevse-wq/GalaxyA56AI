package com.galaxya56.ai.data

enum class ExtensionStatus(val label: String) {
    READY("integriert"),
    COMPATIBLE("kompatibel"),
    EXPERIMENTAL("experimentell"),
    PLANNED("geplant")
}

data class ExtensionEntry(
    val name: String,
    val kind: String,
    val status: ExtensionStatus,
    val description: String,
    val url: String,
    val hint: String = ""
)

/**
 * Extensions are deliberately separated from checkpoints: they modify or add
 * a capability to an existing runtime. The list is a curated starting point,
 * not a claim that every entry is already wired into this build.
 */
object Extensions {

    val entries: List<ExtensionEntry> = listOf(
        ExtensionEntry(
            name = "ControlNet SD1.5 (Canny/Depth/OpenPose)",
            kind = "Bild-Extension",
            status = ExtensionStatus.READY,
            description = "Räumliche Steuerung für Diffusion: Kanten, Tiefe oder Pose als zusätzliche Bedingung.",
            url = "https://huggingface.co/lllyasviel/ControlNet-v1-1",
            hint = "Studio → ControlNet. Modell laden, Control-Bild wählen, Stärke 0,8–1,0."
        ),
        ExtensionEntry(
            name = "LCM-LoRA für SD1.5",
            kind = "Bild-LoRA",
            status = ExtensionStatus.READY,
            description = "Senkt die nötigen Steps auf 4 und macht SD1.5 auf dem A56 thermisch beherrschbar.",
            url = "https://huggingface.co/latent-consistency/lcm-lora-sdv1-5",
            hint = "Im Studio als Bild-LoRA wählen, Stärke 0,8, Sampler lcm, CFG 1,5."
        ),
        ExtensionEntry(
            name = "IP-Adapter (SD1.5)",
            kind = "Bild-Extension",
            status = ExtensionStatus.COMPATIBLE,
            description = "Übernimmt Stil oder Motiv aus einem Referenzbild, ohne ein eigenes Checkpoint zu trainieren.",
            url = "https://huggingface.co/h94/IP-Adapter",
            hint = "stable-diffusion.cpp unterstützt den Bildpfad; die UI-Auswahl folgt nach dem ControlNet-Test."
        ),
        ExtensionEntry(
            name = "ADetailer / Face Detailer",
            kind = "Bild-Extension",
            status = ExtensionStatus.EXPERIMENTAL,
            description = "Erkennt Gesichter und Details nach der Generierung und rendert sie in einem zweiten Pass.",
            url = "https://github.com/Bing-su/adetailer",
            hint = "Auf 8 GB nur mit kleinem Crop und einem Step-Satz sinnvoll."
        ),
        ExtensionEntry(
            name = "AnimateDiff Motion Adapter",
            kind = "Video-Extension",
            status = ExtensionStatus.COMPATIBLE,
            description = "Bringt zeitliche Konsistenz in SD1.5 und erzeugt kurze 16-Frame-Clips.",
            url = "https://huggingface.co/guoyww/animatediff",
            hint = "Das Motion-Modul ist klein; die Clip-Runtime ist noch nicht im Build."
        ),
        ExtensionEntry(
            name = "Wan 2.1 T2V 1.3B",
            kind = "Video-Modell",
            status = ExtensionStatus.EXPERIMENTAL,
            description = "Kleinstes echtes Text-to-Video-Modell, das auf 8 GB RAM realistisch bleibt.",
            url = "https://huggingface.co/Wan-AI/Wan2.1-T2V-1.3B",
            hint = "Mit 16 Frames und 512 × 512 starten; Thermal-Status beobachten."
        ),
        ExtensionEntry(
            name = "ACE-Step / MusicGen Small",
            kind = "Audio-Modell",
            status = ExtensionStatus.PLANNED,
            description = "Neuronale Musikmodelle für Text-to-Music; deutlich schwerer als der lokale DSP-Renderer.",
            url = "https://huggingface.co/facebook/musicgen-small",
            hint = "Der Psytrance-Renderer läuft bereits offline; neuronale Modelle brauchen eine eigene Runtime."
        ),
        ExtensionEntry(
            name = "Whisper.cpp (ggml)",
            kind = "Audio-Extension",
            status = ExtensionStatus.COMPATIBLE,
            description = "Transkription und Untertitel lokal; winzige Modelle ab 75 MB.",
            url = "https://huggingface.co/ggerganov/whisper.cpp",
            hint = "Whisper Tiny ist bereits im Katalog; die Runtime ist noch nicht verdrahtet."
        ),
        ExtensionEntry(
            name = "LLM-LoRA: Coding / Function Calling",
            kind = "LLM-LoRA",
            status = ExtensionStatus.READY,
            description = "GGUF-LoRAs spezialisieren ein Basismodell auf Code, SQL, Tool-Calls oder eine Sprache.",
            url = "https://huggingface.co/models?other=lora&search=gguf",
            hint = "Modelle → LoRA: nach „coding“, „sql“ oder „function calling“ suchen und auf das geladene GGUF anwenden."
        ),
        ExtensionEntry(
            name = "LLM-LoRA: Deutsch / Zusammenfassen",
            kind = "LLM-LoRA",
            status = ExtensionStatus.READY,
            description = "Sprach- und Aufgabenadapter für kleinere 1B–3B-Modelle, ohne das Basismodell zu ersetzen.",
            url = "https://huggingface.co/models?other=lora&search=german",
            hint = "Nach dem Download in der LoRA-Liste anwenden; Skalierung 0,6–1,0 testen."
        ),
        ExtensionEntry(
            name = "Real-ESRGAN / MIDAS 4× (NPU)",
            kind = "NPU-Extension",
            status = ExtensionStatus.READY,
            description = "Vergrößert ein fertiges Bild 4× auf der NPU; der einzige verifizierte NPU-Pfad auf diesem A56.",
            url = "https://soc-developer.semiconductor.samsung.com/global/development/ai-litecore",
            hint = "Studio → NPU 4× Upscale. Läuft auf dem geräteeigenen MIDAS-.nnc-Modell."
        )
    )

    val categories: List<String> get() = entries.map { it.kind }.distinct()
}
