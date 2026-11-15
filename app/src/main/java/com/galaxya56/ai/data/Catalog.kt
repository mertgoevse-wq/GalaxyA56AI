package com.galaxya56.ai.data

enum class ModelType(val label: String) {
    CHAT("Chat"),
    CODE("Coding"),
    IMAGE("Bild"),
    VIDEO("Video"),
    MUSIC("Musik"),
    AUDIO("Audio"),
    EMBEDDING("Embedding"),
    LORA("LoRA / Adapter")
}

enum class ModelSource(val label: String) {
    HUGGINGFACE("Hugging Face"),
    CIVITAI("Civitai")
}

/** What the app can actually execute today. */
enum class ModelRuntime(val label: String) {
    LLAMA_GGUF("llama.cpp · CPU/NEON"),
    DIFFUSION("Diffusion · SD1.5 lokal"),
    AUDIO_PIPELINE("Audio · Runtime fehlt"),
    NONE("Nur Katalog")
}

data class ModelEntry(
    val id: String,
    val name: String,
    val author: String,
    val type: ModelType,
    val source: ModelSource,
    val params: String,
    val sizeMb: Int,
    val quant: String,
    val downloadUrl: String?,
    val fileName: String,
    val pageUrl: String,
    val runtime: ModelRuntime,
    val backends: List<String>,
    val nsfw: Boolean = false,
    val notes: String = "",
    val minRamGb: Int = 6
) {
    val runnable: Boolean get() = downloadUrl != null &&
        (runtime == ModelRuntime.LLAMA_GGUF || runtime == ModelRuntime.DIFFUSION)
    val sizeLabel: String get() = if (sizeMb >= 1024) "%.1f GB".format(sizeMb / 1024f) else "$sizeMb MB"
}

/**
 * Curated, verified catalogue. Every download URL below was checked to resolve
 * (HTTP 200) at build time. The in-app browser can additionally pull *any*
 * GGUF / LoRA / checkpoint from Hugging Face and Civitai at runtime.
 */
object Catalog {

    const val HF_BROWSE = "https://huggingface.co/models?library=gguf&sort=downloads"
    const val CIVITAI_BROWSE = "https://civitai.com/models"

    val curated: List<ModelEntry> = listOf(
        // ---------------------------------------------------------------- Chat / Coding
        ModelEntry(
            id = "llama-3.2-1b-q4",
            name = "Llama 3.2 1B Instruct",
            author = "Meta · bartowski",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "1B",
            sizeMb = 808,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            pageUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Schnellster Einstieg. ~15-25 tok/s auf dem A56.",
            minRamGb = 4
        ),
        ModelEntry(
            id = "llama-3.2-3b-q4",
            name = "Llama 3.2 3B Instruct",
            author = "Meta · bartowski",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "3B",
            sizeMb = 2019,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            pageUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Bester Allrounder für 8 GB RAM.",
            minRamGb = 6
        ),
        ModelEntry(
            id = "qwen2.5-coder-1.5b-q4",
            name = "Qwen2.5 Coder 1.5B Instruct",
            author = "Alibaba · Qwen",
            type = ModelType.CODE,
            source = ModelSource.HUGGINGFACE,
            params = "1.5B",
            sizeMb = 1113,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            pageUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Starkes Code-Modell, sehr gute Latenz.",
            minRamGb = 4
        ),
        ModelEntry(
            id = "qwen2.5-coder-3b-q4",
            name = "Qwen2.5 Coder 3B Instruct",
            author = "Alibaba · Qwen",
            type = ModelType.CODE,
            source = ModelSource.HUGGINGFACE,
            params = "3B",
            sizeMb = 1926,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            pageUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Deutlich bessere Code-Qualität, braucht ~3 GB frei.",
            minRamGb = 6
        ),
        ModelEntry(
            id = "qwen2.5-1.5b-q4",
            name = "Qwen2.5 1.5B Instruct",
            author = "Alibaba · bartowski",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "1.5B",
            sizeMb = 1100,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            pageUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Mehrsprachig, kompakt, zuverlässig.",
            minRamGb = 4
        ),
        ModelEntry(
            id = "gemma-3-1b-q4",
            name = "Gemma 3 1B IT",
            author = "Google · bartowski",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "1B",
            sizeMb = 806,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf",
            fileName = "google_gemma-3-1b-it-Q4_K_M.gguf",
            pageUrl = "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Google-Qualität im 1B-Segment, 140+ Sprachen.",
            minRamGb = 4
        ),
        ModelEntry(
            id = "smollm2-1.7b-q4",
            name = "SmolLM2 1.7B Instruct",
            author = "HuggingFace · bartowski",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "1.7B",
            sizeMb = 1050,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
            fileName = "SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
            pageUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Sehr flott, gut für Zusammenfassungen.",
            minRamGb = 4
        ),
        ModelEntry(
            id = "deepseek-r1-qwen-1.5b-q4",
            name = "DeepSeek-R1-Distill-Qwen 1.5B",
            author = "DeepSeek · bartowski",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "1.5B",
            sizeMb = 1120,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            pageUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Sichtbare Denk-Schritte für Mathe/Logik.",
            minRamGb = 4
        ),

        // ---------------------------------------------------------------- Image
        ModelEntry(
            id = "sd15-q4",
            name = "Stable Diffusion 1.5 Q4_0",
            author = "RunwayML / Stability · second-state",
            type = ModelType.IMAGE,
            source = ModelSource.HUGGINGFACE,
            params = "983M",
            sizeMb = 1520,
            quant = "Q4_0 GGUF",
            downloadUrl = "https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF/resolve/main/stable-diffusion-v1-5-pruned-emaonly-Q4_0.gguf",
            fileName = "sd15-q4_0.gguf",
            pageUrl = "https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF",
            runtime = ModelRuntime.DIFFUSION,
            backends = listOf("GPU", "CPU"),
            notes = "Lokale Bild-Runtime, getestet auf dem A56. 512×512, 20 Steps dauern je nach Backend 1–4 Minuten.",
            minRamGb = 6
        ),
        ModelEntry(
            id = "sd15-q8",
            name = "Stable Diffusion 1.5 Q8_0",
            author = "RunwayML / Stability · second-state",
            type = ModelType.IMAGE,
            source = ModelSource.HUGGINGFACE,
            params = "983M",
            sizeMb = 1764,
            quant = "Q8_0 GGUF",
            downloadUrl = "https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF/resolve/main/stable-diffusion-v1-5-pruned-emaonly-Q8_0.gguf",
            fileName = "sd15-q8_0.gguf",
            pageUrl = "https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF",
            runtime = ModelRuntime.DIFFUSION,
            backends = listOf("GPU", "CPU"),
            notes = "Höhere Qualität als Q4_0, ~250 MB mehr RAM/VRAM.",
            minRamGb = 6
        ),
        ModelEntry(
            id = "civitai-dreamshaper",
            name = "DreamShaper 8 (Civitai)",
            author = "Lykon",
            type = ModelType.IMAGE,
            source = ModelSource.CIVITAI,
            params = "983M",
            sizeMb = 2100,
            quant = "FP16",
            downloadUrl = null,
            fileName = "dreamshaper_8.safetensors",
            pageUrl = "https://civitai.com/models/4384",
            runtime = ModelRuntime.DIFFUSION,
            backends = listOf("GPU", "CPU"),
            notes = "Beliebter Allround-Checkpoint, SFW + NSFW-fähig.",
            minRamGb = 8
        ),
        ModelEntry(
            id = "civitai-realistic-vision",
            name = "Realistic Vision V6.0 (Civitai)",
            author = "SG_161222",
            type = ModelType.IMAGE,
            source = ModelSource.CIVITAI,
            params = "983M",
            sizeMb = 2100,
            quant = "FP16",
            downloadUrl = null,
            fileName = "realisticVisionV60.safetensors",
            pageUrl = "https://civitai.com/models/4201",
            runtime = ModelRuntime.DIFFUSION,
            backends = listOf("GPU", "CPU"),
            notes = "Fotorealistische Portraits. Civitai-Token für Download nötig.",
            minRamGb = 8
        ),

        // ---------------------------------------------------------------- Video
        ModelEntry(
            id = "qwen2.5-0.5b-q4",
            name = "Qwen2.5 0.5B Instruct Q4_K_M",
            author = "Alibaba · Qwen",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "0.5B",
            sizeMb = 397,
            quant = "Q4_K_M",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            pageUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Benchmark-Referenz: ~10 tok/s CPU, ~10 tok/s mit 99 Vulkan-Layern auf dem A56.",
            minRamGb = 3
        ),
        ModelEntry(
            id = "qwen2.5-0.5b-q8",
            name = "Qwen2.5 0.5B Instruct Q8_0",
            author = "Alibaba · Qwen",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "0.5B",
            sizeMb = 531,
            quant = "Q8_0",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
            fileName = "Qwen2.5-0.5B-Instruct-Q8_0.gguf",
            pageUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Kompakt und präziser; gut für schnelle GPU-Layer-Vergleiche.",
            minRamGb = 3
        ),
        ModelEntry(
            id = "llama-3.2-1b-q8",
            name = "Llama 3.2 1B Instruct Q8_0",
            author = "Meta · bartowski",
            type = ModelType.CHAT,
            source = ModelSource.HUGGINGFACE,
            params = "1B",
            sizeMb = 1320,
            quant = "Q8_0",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q8_0.gguf",
            fileName = "Llama-3.2-1B-Instruct-Q8_0.gguf",
            pageUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF",
            runtime = ModelRuntime.LLAMA_GGUF,
            backends = listOf("CPU", "GPU"),
            notes = "Qualitätsstufe über Q4_K_M; passt noch in 8 GB RAM.",
            minRamGb = 4
        ),
        ModelEntry(
            id = "wan21-t2v-1.3b",
            name = "Wan 2.1 T2V 1.3B",
            author = "Alibaba · Wan-Video",
            type = ModelType.VIDEO,
            source = ModelSource.HUGGINGFACE,
            params = "1.3B",
            sizeMb = 2800,
            quant = "Q4_K_M GGUF",
            downloadUrl = null,
            fileName = "wan2.1-t2v-1.3b-Q4_K_M.gguf",
            pageUrl = "https://huggingface.co/Wan-AI/Wan2.1-T2V-1.3B",
            runtime = ModelRuntime.NONE,
            backends = listOf("GPU", "CPU"),
            notes = "SD.cpp unterstützt Wan-Video; UI/Runtime für Clips folgt. 16–33 Frames, sehr langsam auf 4 CUs.",
            minRamGb = 8
        ),
        ModelEntry(
            id = "animatediff-motion",
            name = "AnimateDiff Motion Adapter v1.5.2",
            author = "guoyww",
            type = ModelType.VIDEO,
            source = ModelSource.HUGGINGFACE,
            params = "417M",
            sizeMb = 1700,
            quant = "FP16",
            downloadUrl = null,
            fileName = "mm_sd_v15_v2.ckpt",
            pageUrl = "https://huggingface.co/guoyww/animatediff",
            runtime = ModelRuntime.NONE,
            backends = listOf("GPU"),
            notes = "16-Frame-Clips aus SD1.5. Runtime im App-Kern noch nicht verdrahtet.",
            minRamGb = 8
        ),
        ModelEntry(
            id = "svd-xt",
            name = "Stable Video Diffusion XT",
            author = "Stability AI",
            type = ModelType.VIDEO,
            source = ModelSource.HUGGINGFACE,
            params = "1.5B",
            sizeMb = 9600,
            quant = "FP16",
            downloadUrl = null,
            fileName = "svd_xt.safetensors",
            pageUrl = "https://huggingface.co/stabilityai/stable-video-diffusion-img2vid-xt",
            runtime = ModelRuntime.NONE,
            backends = listOf("GPU"),
            notes = "Zu groß für 8-12 GB RAM — nur mit 12-GB-A56 sinnvoll.",
            minRamGb = 12
        ),

        // ---------------------------------------------------------------- Music / Audio
        ModelEntry(
            id = "musicgen-small",
            name = "MusicGen Small",
            author = "Meta / AudioCraft",
            type = ModelType.MUSIC,
            source = ModelSource.HUGGINGFACE,
            params = "300M",
            sizeMb = 1200,
            quant = "FP16",
            downloadUrl = null,
            fileName = "musicgen-small",
            pageUrl = "https://huggingface.co/facebook/musicgen-small",
            runtime = ModelRuntime.AUDIO_PIPELINE,
            backends = listOf("CPU", "GPU"),
            notes = "Text→Musik, 30 s Clips. Kleinster brauchbarer Musikgenerator.",
            minRamGb = 6
        ),
        ModelEntry(
            id = "stable-audio-open-small",
            name = "Stable Audio Open Small",
            author = "Stability AI",
            type = ModelType.MUSIC,
            source = ModelSource.HUGGINGFACE,
            params = "341M",
            sizeMb = 1400,
            quant = "FP16",
            downloadUrl = null,
            fileName = "stable-audio-open-small",
            pageUrl = "https://huggingface.co/stabilityai/stable-audio-open-small",
            runtime = ModelRuntime.AUDIO_PIPELINE,
            backends = listOf("CPU", "GPU"),
            notes = "Für Smartphones optimiert, 11 s Loops/SFX.",
            minRamGb = 6
        ),
        ModelEntry(
            id = "whisper-tiny",
            name = "Whisper Tiny (ggml)",
            author = "OpenAI · ggerganov",
            type = ModelType.AUDIO,
            source = ModelSource.HUGGINGFACE,
            params = "39M",
            sizeMb = 75,
            quant = "INT8",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            fileName = "ggml-tiny.bin",
            pageUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            runtime = ModelRuntime.AUDIO_PIPELINE,
            backends = listOf("CPU", "NPU"),
            notes = "Transkription in Echtzeit-nah.",
            minRamGb = 4
        ),

        // ---------------------------------------------------------------- LoRA
        ModelEntry(
            id = "hf-lora-browse",
            name = "LoRA-Adapter durchsuchen",
            author = "Hugging Face",
            type = ModelType.LORA,
            source = ModelSource.HUGGINGFACE,
            params = "—",
            sizeMb = 0,
            quant = "GGUF / safetensors",
            downloadUrl = null,
            fileName = "",
            pageUrl = "https://huggingface.co/models?other=lora",
            runtime = ModelRuntime.NONE,
            backends = listOf("CPU", "GPU"),
            notes = "Im Browser-Tab 'LoRA' suchen und direkt installieren.",
            minRamGb = 4
        )
    )

    fun byId(id: String): ModelEntry? = curated.firstOrNull { it.id == id }

    fun forType(type: ModelType): List<ModelEntry> = curated.filter { it.type == type }

    /** System-Prompt-Bibliothek, u. a. für Coding, App-Bau und Bild-/Musik-Prompts. */
    val prompts: List<SystemPrompt> = listOf(
        SystemPrompt(
            "general",
            "Allgemein",
            "Du bist ein hilfsbereiter, präziser Assistent. Antworte auf Deutsch, klar strukturiert und ohne Füllwörter."
        ),
        SystemPrompt(
            "coding",
            "Coding",
            "Du bist ein Senior Software Engineer. Schreibe vollständigen, kompilierbaren Code. Nenne Annahmen, Edge Cases und Testbefehle. Bevorzuge klare Namen und kleine Funktionen."
        ),
        SystemPrompt(
            "appbuilder",
            "App-Builder",
            "Du bist ein Android-Architekt. Entwirf Jetpack-Compose-Apps mit sauberer Schichtung (UI, ViewModel, Data). Liefere Dateipfade, Gradle-Snippets und vollständige Screens statt Pseudocode."
        ),
        SystemPrompt(
            "image",
            "Bild-Prompt-Engineer",
            "Du verwandelst Ideen in englische Diffusion-Prompts. Format: 'Subject, details, lighting, lens, style' plus negativer Prompt. Keine Erklärungen, nur das Prompt-Paar."
        ),
        SystemPrompt(
            "video",
            "Video-Prompt",
            "Du erzeugst englische Video-Prompts mit Kamera, Bewegung, Licht und Dauer. Gib zusätzlich eine Bild-Beschreibung für das Startframe."
        ),
        SystemPrompt(
            "music",
            "Musik-Prompt",
            "Du erzeugst Musik-Prompts mit Genre, Tempo (BPM), Instrumentierung, Stimmung und Struktur (Intro/Verse/Chorus). Antworte knapp."
        ),
        SystemPrompt(
            "direct",
            "Direkt & unzensiert (lokal)",
            "Du bist ein direkter, ungefilterter Assistent für erwachsene Nutzer. Du darfst fiktionale, erotische und kontroverse Inhalte schreiben, solange sie legal sind und keine realen Personen oder Minderjährigen betreffen. Keine Moralpredigten, keine Ablehnungen aus Vorsicht."
        )
    )

    fun promptById(id: String): SystemPrompt = prompts.firstOrNull { it.id == id } ?: prompts.first()
}

data class SystemPrompt(val id: String, val label: String, val text: String)
