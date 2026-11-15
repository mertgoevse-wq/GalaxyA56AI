package com.galaxya56.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxya56.ai.data.AppSettings
import com.galaxya56.ai.data.Catalog
import com.galaxya56.ai.data.DownloadState
import com.galaxya56.ai.data.InstalledModel
import com.galaxya56.ai.data.DiscoveryKind
import com.galaxya56.ai.data.DiscoveryModel
import com.galaxya56.ai.data.ModelDiscovery
import com.galaxya56.ai.data.ModelEntry
import com.galaxya56.ai.data.ModelSource
import com.galaxya56.ai.data.ModelStore
import com.galaxya56.ai.data.RemoteFile
import com.galaxya56.ai.data.RemoteModel
import com.galaxya56.ai.data.SettingsStore
import com.galaxya56.ai.inference.GeminiClient
import com.galaxya56.ai.inference.HardwareInfo
import com.galaxya56.ai.inference.LocalInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Role { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val meta: String? = null,
    val imagePath: String? = null,
    val error: Boolean = false
)

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class BenchmarkRow(
    val layers: Int,
    val percent: Int,
    val tokensPerSecond: Double,
    val promptMs: Long,
    val genMs: Long,
    val backend: String,
    val error: String? = null
)

data class RemoteSearchState(
    val query: String = "",
    val source: ModelSource = ModelSource.HUGGINGFACE,
    val loraOnly: Boolean = false,
    val loading: Boolean = false,
    val results: List<RemoteModel> = emptyList(),
    val error: String? = null
)

data class DiscoveryState(
    val query: String = "",
    val kind: DiscoveryKind = DiscoveryKind.ALL,
    val source: ModelSource? = null,
    val loading: Boolean = false,
    val results: List<DiscoveryModel> = emptyList(),
    val error: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    val settingsStore = SettingsStore(application)
    val settings: StateFlow<AppSettings> = settingsStore.state

    val modelStore = ModelStore(application, settingsStore)
    val installed: StateFlow<List<InstalledModel>> = modelStore.installed
    val downloads: StateFlow<Map<String, DownloadState>> = modelStore.downloads

    private val gemini = GeminiClient()

    private val _hardware = MutableStateFlow(HardwareInfo())
    val hardware: StateFlow<HardwareInfo> = _hardware

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _activeId = MutableStateFlow("")
    val activeId: StateFlow<String> = _activeId

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _loadedModel = MutableStateFlow<String?>(null)
    val loadedModel: StateFlow<String?> = _loadedModel

    private val _npuProbe = MutableStateFlow<String?>(null)
    val npuProbe: StateFlow<String?> = _npuProbe

    private val _npuProbeBusy = MutableStateFlow(false)
    val npuProbeBusy: StateFlow<Boolean> = _npuProbeBusy

    private val _ennProbe = MutableStateFlow<String?>(null)
    val ennProbe: StateFlow<String?> = _ennProbe

    private val _ennBenchmark = MutableStateFlow<String?>(null)
    val ennBenchmark: StateFlow<String?> = _ennBenchmark

    private val _ennBusy = MutableStateFlow(false)
    val ennBusy: StateFlow<Boolean> = _ennBusy

    private val _benchmark = MutableStateFlow<List<BenchmarkRow>>(emptyList())
    val benchmark: StateFlow<List<BenchmarkRow>> = _benchmark

    private val _benchmarkBusy = MutableStateFlow(false)
    val benchmarkBusy: StateFlow<Boolean> = _benchmarkBusy

    private val _remote = MutableStateFlow(RemoteSearchState())
    val remote: StateFlow<RemoteSearchState> = _remote

    private val _remoteFiles = MutableStateFlow<Map<String, List<RemoteFile>>>(emptyMap())
    val remoteFiles: StateFlow<Map<String, List<RemoteFile>>> = _remoteFiles

    private val _fileSheet = MutableStateFlow<RemoteModel?>(null)
    val fileSheet: StateFlow<RemoteModel?> = _fileSheet

    private val discoveryRepo = ModelDiscovery(settingsStore)
    private val _discovery = MutableStateFlow(DiscoveryState())
    val discovery: StateFlow<DiscoveryState> = _discovery
    private val _discoverySheet = MutableStateFlow<DiscoveryModel?>(null)
    val discoverySheet: StateFlow<DiscoveryModel?> = _discoverySheet

    private val _studioImage = MutableStateFlow<File?>(null)
    val studioImage: StateFlow<File?> = _studioImage

    private val _studioBusy = MutableStateFlow(false)
    val studioBusy: StateFlow<Boolean> = _studioBusy

    private val _studioError = MutableStateFlow<String?>(null)
    val studioError: StateFlow<String?> = _studioError

    private val _localImageModel = MutableStateFlow<String?>(null)
    val localImageModel: StateFlow<String?> = _localImageModel

    private var loadedImagePath: String = ""
    private var loadedPath: String = ""
    private var loadedBackend: String = "CPU"
    private var debugEnnModelPath: String? = null

    init {
        loadConversations()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { _hardware.value = LocalInference.hardware() }
                .onFailure { _status.value = "Native Hardware-Probe nicht verfügbar: ${it.message}" }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(6000)
                if (!_isGenerating.value) {
                    runCatching { _hardware.value = LocalInference.hardware() }
                }
            }
        }
    }

    // ------------------------------------------------------------------ settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    /**
     * Loads the active GGUF once per GPU-layer split and records real tok/s.
     * Runs on Dispatchers.IO and leaves the last configuration loaded.
     */
    fun runGpuLayerBenchmark() {
        if (_benchmarkBusy.value) return
        val s = settings.value
        val path = loadedPath.ifBlank {
            s.selectedModelPath.ifBlank {
                installed.value.firstOrNull { !it.isLora }?.file?.absolutePath.orEmpty()
            }
        }
        if (path.isBlank()) {
            _status.value = "Benchmark: zuerst ein GGUF-Modell auswaehlen"
            return
        }
        _benchmarkBusy.value = true
        _benchmark.value = emptyList()
        viewModelScope.launch {
            val rows = mutableListOf<BenchmarkRow>()
            var layerCount = 24
            try {
                withContext(Dispatchers.IO) {
                    val probe = LocalInference.load(path, 2048, s.threads, 0)
                    if (!probe.ok) {
                        rows += BenchmarkRow(0, 0, 0.0, 0, 0, "-", probe.error)
                        return@withContext
                    }
                    layerCount = probe.layers.coerceAtLeast(4)
                    loadedPath = path
                    _loadedModel.value = probe.description
                    val splits = listOf(0, 25, 50, 75, 100)
                    for (percent in splits) {
                        val layers = if (percent == 100) 999 else layerCount * percent / 100
                        val load = LocalInference.load(path, 2048, s.threads, layers)
                        if (!load.ok) {
                            rows += BenchmarkRow(layers, percent, 0.0, 0, 0, "-", load.error)
                            continue
                        }
                        loadedBackend = load.backend
                        val result = LocalInference.generate(
                            prompt = "Say hello in five short words.",
                            maxTokens = 32,
                            temperature = 0.0f,
                            topP = 1.0f,
                            topK = 1,
                            repeatPenalty = 1.0f
                        ) { }
                        rows += BenchmarkRow(
                            layers = if (percent == 100) load.layers else layers,
                            percent = percent,
                            tokensPerSecond = result.tokensPerSecond,
                            promptMs = result.promptMs,
                            genMs = result.genMs,
                            backend = load.backend,
                            error = if (result.ok) null else result.error
                        )
                        _benchmark.value = rows.toList()
                    }
                }
                _status.value = "Benchmark fertig: ${rows.count { it.error == null }} Messungen, ${layerCount} Layer"
            } catch (t: Throwable) {
                _status.value = "Benchmark fehlgeschlagen: ${t.message}"
            } finally {
                _benchmark.value = rows.toList()
                _benchmarkBusy.value = false
            }
        }
    }

    fun runNnapiProbe() {
        if (_npuProbeBusy.value) return
        _npuProbeBusy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = LocalInference.nnapiProbe()
            val pretty = runCatching {
                val o = JSONObject(result)
                buildString {
                    appendLine(if (o.optBoolean("ok")) "Ausfuehrung: OK" else "Fehler: ${o.optString("error")}")
                    if (!o.optBoolean("npu", o.optBoolean("ok"))) {
                        appendLine("NPU: nicht exponiert (${o.optString("reason", "unbekannt")})")
                    }
                    appendLine("Gewaehlt: ${o.optString("chosen")} (${o.optString("chosenType")})")
                    if (o.has("latencyMs")) appendLine("Latenz: ${"%.2f".format(o.optDouble("latencyMs"))} ms")
                    val out = o.optJSONArray("output")
                    if (out != null) {
                        val values = (0 until out.length()).joinToString(", ") { "%.0f".format(out.optDouble(it)) }
                        appendLine("Ausgabe: " + values + if (o.optBoolean("correct")) "  [korrekt]" else "  [falsch]")
                    }
                    val devs = o.optJSONArray("devices")
                    if (devs != null && devs.length() > 0) {
                        appendLine()
                        appendLine("NNAPI-Geraete:")
                        for (i in 0 until devs.length()) {
                            val d = devs.getJSONObject(i)
                            appendLine("- ${d.optString("name")} - ${d.optString("type")} - FL ${d.optInt("featureLevel")}")
                        }
                    }
                }
            }.getOrDefault(result)
            _npuProbe.value = pretty
            _npuProbeBusy.value = false
        }
    }

    /** Debug hook for the missing-model error path. */
    fun setDebugEnnModelPath(path: String?) {
        debugEnnModelPath = path
    }

    /**
     * Samsung ENN is the vendor's public NPU API on Exynos. It is loaded
     * dynamically from the public vendor library; .nnc models come from the
     * official exynos-eco samples (e.g. Real-ESRGAN x4).
     */
    fun runEnnProbe() {
        if (_ennBusy.value) return
        _ennBusy.value = true
        _ennBenchmark.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = LocalInference.ennProbe()
            _ennProbe.value = prettyEnnReport(result)
            _ennBusy.value = false
        }
    }

    fun runEnnBenchmark() {
        if (_ennBusy.value) return
        val model = resolveEnnModel()
        if (model == null) {
            _ennBenchmark.value = "Kein .nnc-Modell gefunden.\n\nErwartet: " +
                "/vendor/etc/midas/SRIBMidas_aiUPSCALER_4X_V210_FP16_rose.nnc " +
                "oder Android/data/com.galaxya56.ai/files/models/*.nnc"
            return
        }
        _ennBusy.value = true
        _ennBenchmark.value = "Lade ${model.name} …"
        viewModelScope.launch(Dispatchers.IO) {
            val result = LocalInference.ennBenchmark(model.absolutePath)
            _ennBenchmark.value = prettyEnnReport(result)
            _ennBusy.value = false
        }
    }

    private fun resolveEnnModel(): File? {
        debugEnnModelPath?.let { return File(it) }
        // The device ships this MIDAS 4x model specifically for S5E8845/S5E8855
        // (Exynos 1380/1580), compiled for the same NCP 26 NPU driver.
        val midas = File("/vendor/etc/midas/SRIBMidas_aiUPSCALER_4X_V210_FP16_rose.nnc")
        if (midas.exists()) return midas
        val app = getApplication<Application>()
        val dirs = listOfNotNull(app.getExternalFilesDir(null), app.filesDir)
        for (dir in dirs) {
            val models = File(dir, "models")
            models.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".nnc", true) }
                ?.let { return it }
        }
        return null
    }

    private fun prettyEnnReport(raw: String): String = runCatching {
        val o = JSONObject(raw)
        buildString {
            if (o.optBoolean("ok") && o.optBoolean("available")) {
                appendLine("ENN: verfügbar")
                if (o.optString("framework").isNotBlank()) appendLine("Framework: ${o.optString("framework")}")
                if (o.optString("unifiedFw").isNotBlank()) appendLine("Unified FW: ${o.optString("unifiedFw")}")
                if (o.optString("npuFw").isNotBlank()) appendLine("NPU-FW: ${o.optString("npuFw")}")
                if (o.has("perfMode")) appendLine("Perf-Modus: ${o.optInt("perfMode")}")
            } else if (!o.optBoolean("ok")) {
                appendLine("ENN: Fehler")
                if (o.has("stage")) appendLine("Stage: ${o.optString("stage")} (Code ${o.optInt("code")})")
                appendLine(o.optString("error", raw))
            }
            if (o.has("execMs")) {
                appendLine()
                appendLine("Modell: ${o.optString("model")}")
                if (o.optString("nnc").isNotBlank()) appendLine("NNC: ${o.optString("nnc")}")
                if (o.optString("npuCompiler").isNotBlank()) appendLine("NPU-Compiler: ${o.optString("npuCompiler")}")
                appendLine("Laden: ${o.optLong("loadMs")} ms · Ausführen: ${o.optLong("execMs")} ms")
                appendLine("Buffer: ${o.optInt("nIn")} in / ${o.optInt("nOut")} out")
                o.optJSONObject("input")?.let { bi ->
                    appendLine("Input: ${bi.optInt("width")}×${bi.optInt("height")}×${bi.optInt("channel")} · ${bi.optInt("size")} B")
                }
                o.optJSONObject("output")?.let { bo ->
                    appendLine("Output: ${bo.optInt("width")}×${bo.optInt("height")}×${bo.optInt("channel")} · ${bo.optInt("size")} B")
                }
                appendLine("Checksum: ${o.optLong("checksum")} · ${o.optLong("nonZero")} Nicht-Null-Bytes")
                if (o.optBoolean("ok")) appendLine("→ echte ENN-Ausführung auf dem Gerät")
            }
        }.trim()
    }.getOrDefault(raw)

    fun enableAdultContent(confirmed: Boolean) {
        updateSettings { it.copy(adultConfirmed = confirmed, nsfwEnabled = confirmed) }
    }

    fun setNsfw(enabled: Boolean) {
        updateSettings { it.copy(nsfwEnabled = enabled && it.adultConfirmed) }
    }

    // ------------------------------------------------------------------ conversations

    fun newConversation() {
        val conv = Conversation(id = "c${System.currentTimeMillis()}", title = "Neuer Chat")
        _conversations.update { listOf(conv) + it }
        _activeId.value = conv.id
        saveConversations()
    }

    fun openConversation(id: String) {
        _activeId.value = id
    }

    fun deleteConversation(id: String) {
        _conversations.update { list -> list.filterNot { it.id == id } }
        if (_activeId.value == id) _activeId.value = _conversations.value.firstOrNull()?.id.orEmpty()
        saveConversations()
    }

    fun ensureConversation(): String {
        val current = _conversations.value.firstOrNull { it.id == _activeId.value }
        if (current != null) return current.id
        val conv = _conversations.value.firstOrNull() ?: Conversation(
            id = "c${System.currentTimeMillis()}", title = "Neuer Chat"
        )
        if (_conversations.value.isEmpty()) _conversations.update { listOf(conv) }
        _activeId.value = conv.id
        return conv.id
    }

    private fun addMessage(conversationId: String, message: ChatMessage) {
        _conversations.update { list ->
            list.map { conv ->
                if (conv.id != conversationId) conv
                else {
                    val title = if (conv.messages.none { it.role == Role.USER } && message.role == Role.USER) {
                        message.text.trim().take(46).ifBlank { "Neuer Chat" }
                    } else conv.title
                    conv.copy(title = title, messages = conv.messages + message)
                }
            }
        }
    }

    private fun updateMessage(conversationId: String, messageId: Long, transform: (ChatMessage) -> ChatMessage) {
        _conversations.update { list ->
            list.map { conv ->
                if (conv.id != conversationId) conv
                else conv.copy(messages = conv.messages.map { if (it.id == messageId) transform(it) else it })
            }
        }
    }

    fun clearActive() {
        val id = ensureConversation()
        _conversations.update { list -> list.map { if (it.id == id) it.copy(messages = emptyList(), title = "Neuer Chat") else it } }
        saveConversations()
    }

    // ------------------------------------------------------------------ generation

    fun send(text: String) {
        if (text.isBlank() || _isGenerating.value) return
        val conversationId = ensureConversation()
        val userMessage = ChatMessage(System.nanoTime(), Role.USER, text.trim())
        val assistantMessage = ChatMessage(System.nanoTime() + 1, Role.ASSISTANT, "")
        addMessage(conversationId, userMessage)
        addMessage(conversationId, assistantMessage)

        _isGenerating.value = true
        _status.value = "Denke nach …"

        viewModelScope.launch {
            try {
                val current = settings.value
                if (current.cloudReady) {
                    generateCloud(conversationId, assistantMessage.id, current)
                } else {
                    generateLocal(conversationId, assistantMessage.id, current)
                }
            } catch (t: Throwable) {
                updateMessage(conversationId, assistantMessage.id) {
                    it.copy(text = it.text.ifBlank { "Fehler: ${t.message}" }, error = true, meta = null)
                }
            } finally {
                _isGenerating.value = false
                _status.value = ""
                saveConversations()
            }
        }
    }

    fun stopGeneration() {
        LocalInference.stop()
        _status.value = "Gestoppt"
        _isGenerating.value = false
    }

    private suspend fun generateLocal(conversationId: String, messageId: Long, s: AppSettings) {
        val path = resolveModelPath(s)
        if (path.isBlank()) {
            updateMessage(conversationId, messageId) {
                it.copy(
                    text = "Kein lokales Modell installiert.\n\nÖffne „Modelle“ → Katalog oder Hugging Face und lade ein GGUF-Modell (z. B. Qwen2.5 Coder 1.5B, ~1,1 GB). Danach hier erneut senden.",
                    error = true
                )
            }
            return
        }

        val threads = s.threads.coerceIn(1, (_hardware.value.cpuCores.takeIf { c -> c > 0 } ?: 4))
        if (loadedPath != path) {
            _status.value = "Lade Modell …"
            val result = withContext(Dispatchers.IO) { LocalInference.load(path, s.contextSize, threads, s.gpuLayers) }
            if (!result.ok) {
                updateMessage(conversationId, messageId) {
                    it.copy(text = "Modell konnte nicht geladen werden: ${result.error}", error = true)
                }
                return
            }
            loadedPath = path
            loadedBackend = result.backend
            _loadedModel.value = result.description
            _status.value = "Modell geladen · ${result.context} Tokens Kontext"
        }

        val prompt = PromptBuilder.build(s.systemPrompt, conversationHistory(conversationId, messageId), path)
        _status.value = "Generiere lokal ($loadedBackend) …"

        val started = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
            LocalInference.generate(
                prompt = prompt,
                maxTokens = s.maxTokens,
                temperature = s.temperature,
                topP = s.topP,
                topK = s.topK,
                repeatPenalty = s.repeatPenalty
            ) { token ->
                updateMessage(conversationId, messageId) { it.copy(text = it.text + token) }
            }
        }

        val wall = System.currentTimeMillis() - started
        val meta = buildString {
            append("${result.tokens} Tokens")
            append(" · ")
            append("%.1f tok/s".format(result.tokensPerSecond))
            append(" · ${result.promptMs} ms Prompt")
            append(" · ${wall / 1000.0}s gesamt")
            append(" · $loadedBackend")
        }

        updateMessage(conversationId, messageId) {
            val finalText = when {
                result.text.isNotBlank() -> result.text
                result.error.isNotBlank() -> "Fehler: ${result.error}"
                result.contextFull -> "Der Kontext ist voll. Erhöhe ihn in den Einstellungen oder starte einen neuen Chat."
                else -> "(keine Ausgabe)"
            }
            it.copy(text = finalText, meta = meta, error = result.text.isBlank() && result.error.isNotBlank())
        }
    }

    private suspend fun generateCloud(conversationId: String, messageId: Long, s: AppSettings) {
        _status.value = "Cloud: ${s.cloudModel}"
        val prompt = conversationHistory(conversationId, messageId).joinToString("\n\n") { m ->
            when (m.role) {
                Role.USER -> "Nutzer: ${m.text}"
                Role.ASSISTANT -> "Assistent: ${m.text}"
                Role.SYSTEM -> ""
            }
        }
        val result = withContext(Dispatchers.IO) {
            gemini.streamChat(
                apiKey = s.geminiKey,
                model = s.cloudModel,
                system = s.systemPrompt,
                prompt = prompt,
                temperature = s.temperature,
                maxTokens = s.maxTokens
            ) { token ->
                updateMessage(conversationId, messageId) { it.copy(text = it.text + token) }
            }
        }

        if (result.startsWith("Gemini HTTP") || result.startsWith("Netzwerkfehler") || result.startsWith("Kein API-Key")) {
            updateMessage(conversationId, messageId) { it.copy(text = result, error = true) }
        } else {
            updateMessage(conversationId, messageId) {
                it.copy(text = it.text.ifBlank { result }, meta = "Cloud · ${s.cloudModel}")
            }
        }
    }

    private fun conversationHistory(conversationId: String, excludeMessageId: Long): List<ChatMessage> {
        return _conversations.value.firstOrNull { it.id == conversationId }
            ?.messages
            ?.filter { it.id != excludeMessageId && it.role != Role.SYSTEM }
            ?.filter { it.text.isNotBlank() }
            ?: emptyList()
    }

    private fun resolveModelPath(s: AppSettings): String {
        if (s.selectedModelPath.isNotBlank() && File(s.selectedModelPath).exists()) return s.selectedModelPath
        return modelStore.installed.value.firstOrNull { !it.isLora && it.name.endsWith(".gguf", true) }
            ?.file?.absolutePath.orEmpty()
    }

    fun selectModel(path: String) {
        if (loadedPath != path && loadedPath.isNotBlank()) {
            LocalInference.unload()
            loadedPath = ""
            _loadedModel.value = null
        }
        updateSettings { it.copy(selectedModelPath = path) }
    }

    fun loadModelNow(path: String) {
        viewModelScope.launch {
            val s = settings.value
            _status.value = "Lade Modell …"
            val result = withContext(Dispatchers.IO) {
                LocalInference.load(path, s.contextSize, s.threads, s.gpuLayers)
            }
            if (result.ok) {
                loadedPath = path
                _loadedModel.value = result.description
                updateSettings { it.copy(selectedModelPath = path) }
                _status.value = "Geladen: ${result.description}"
            } else {
                _status.value = "Fehler: ${result.error}"
            }
        }
    }

    // ------------------------------------------------------------------ LoRA

    fun applyLora(file: File, scale: Float) {
        viewModelScope.launch {
            _status.value = "Wende LoRA an …"
            val s = settings.value
            if (loadedPath.isBlank()) {
                val path = resolveModelPath(s)
                val load = withContext(Dispatchers.IO) { LocalInference.load(path, s.contextSize, s.threads, s.gpuLayers) }
                if (!load.ok) {
                    _status.value = "Erst Modell laden: ${load.error}"
                    return@launch
                }
                loadedPath = path
            }
            val result = withContext(Dispatchers.IO) { LocalInference.applyLora(file.absolutePath, scale) }
            _status.value = if (result.contains("\"ok\":true")) "LoRA aktiv (${file.name})" else "LoRA: $result"
        }
    }

    fun clearLoras() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LocalInference.clearLoras() }
            _status.value = "LoRAs entfernt"
        }
    }

    // ------------------------------------------------------------------ studio

    fun generateImage(prompt: String, negative: String) {
        if (prompt.isBlank() || _studioBusy.value) return
        val s = settings.value
        if (!s.cloudReady) {
            _studioError.value = "Bildgenerierung braucht aktuell entweder einen Gemini-API-Key (Einstellungen → Cloud) oder eine lokale Diffusions-Runtime, die in diesem Build noch nicht enthalten ist."
            return
        }
        _studioBusy.value = true
        _studioError.value = null
        viewModelScope.launch {
            try {
                val full = if (negative.isBlank()) prompt else "$prompt\n\nAvoid: $negative"
                val file = withContext(Dispatchers.IO) {
                    gemini.generateImage(s.geminiKey, full, modelStore.outputDir)
                }
                _studioImage.value = file
            } catch (t: Throwable) {
                _studioError.value = t.message
            } finally {
                _studioBusy.value = false
            }
        }
    }

    /** Real local txt2img through stable-diffusion.cpp (Vulkan or CPU). */
    fun generateLocalImage(prompt: String, negative: String) {
        if (prompt.isBlank() || _studioBusy.value) return
        val s = settings.value
        val model = s.imageModelPath.ifBlank {
            installed.value.firstOrNull {
                !it.isLora && it.file.name.let { name ->
                    name.endsWith(".gguf", true) || name.endsWith(".safetensors", true)
                } && it.file.name.let { name ->
                    name.contains("sd15", true) || name.contains("stable-diffusion", true) ||
                        name.contains("sd-turbo", true) || name.contains("sdturbo", true)
                }
            }?.file?.absolutePath.orEmpty()
        }
        if (model.isBlank()) {
            _studioError.value = "Kein lokales Diffusionsmodell gefunden. SD1.5-GGUF im Modelle-Tab herunterladen oder in Einstellungen → Studio eintragen."
            return
        }
        val output = File(modelStore.outputDir, "sd_${System.currentTimeMillis()}.png")
        _studioBusy.value = true
        _studioError.value = null
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    if (loadedImagePath != model) {
                        val load = LocalInference.imageLoad(model, s.threads.coerceAtMost(4))
                        if (!load.ok) throw IllegalStateException(load.error.ifBlank { "Modell konnte nicht geladen werden" })
                        loadedImagePath = model
                        _localImageModel.value = "${load.model} · ${if (load.videoSupport) "Bild+Video" else "Bild"} · ${load.loadMs} ms"
                    }
                    val result = LocalInference.imageGenerate(
                        prompt = prompt,
                        negativePrompt = negative,
                        width = s.imageWidth,
                        height = s.imageHeight,
                        steps = s.imageSteps,
                        cfg = s.imageCfg,
                        vaeTiling = s.imageVaeTiling,
                        seed = -1,
                        outputPath = output.absolutePath
                    )
                    if (!result.ok) throw IllegalStateException(result.error.ifBlank { "Generierung fehlgeschlagen" })
                    val mode = if (model.contains("turbo", true)) "Turbo" else "SD"
                    _status.value = "$mode lokal: ${result.width}x${result.height} in ${result.ms / 1000.0}s"
                    File(result.path.ifBlank { output.absolutePath })
                }
                _studioImage.value = file
            } catch (t: Throwable) {
                _studioError.value = t.message
            } finally {
                _studioBusy.value = false
            }
        }
    }

    /** 4x upscale of the current studio image with the on-device MIDAS NPU model. */
    fun upscaleStudioImage() {
        android.util.Log.d("A56AI-UI", "upscaleStudioImage entered busy=${_studioBusy.value} image=${_studioImage.value?.absolutePath}")
        val source = _studioImage.value
        if (source == null) {
            _studioError.value = "Erst ein Bild erzeugen oder laden."
            return
        }
        if (_studioBusy.value) return
        val model = resolveEnnModel()
        if (model == null || !model.isFile) {
            _studioError.value = "ENN-Modell fehlt: ${model?.absolutePath ?: "MIDAS .nnc"}"
            return
        }
        val output = File(modelStore.outputDir, "npu_upscale_${System.currentTimeMillis()}.png")
        _studioBusy.value = true
        _studioError.value = null
        android.util.Log.d("A56AI-UI", "NPU upscale busy=true model=${model.absolutePath}")
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    LocalInference.ennUpscale(
                        source.absolutePath, output.absolutePath, model.absolutePath
                    )
                }
                val o = JSONObject(result)
                if (!o.optBoolean("ok")) {
                    throw IllegalStateException(o.optString("error", "NPU-Upscale fehlgeschlagen"))
                }
                _studioImage.value = File(o.optString("path", output.absolutePath))
                _status.value = "NPU 4×: ${o.optInt("width")}×${o.optInt("height")} in ${o.optLong("ms")} ms"
            } catch (t: Throwable) {
                _studioError.value = t.message
            } finally {
                _studioBusy.value = false
                android.util.Log.d("A56AI-UI", "NPU upscale busy=false error=${_studioError.value}")
            }
        }
    }

    /** Debug automation hook: stage an existing image for the Studio NPU test. */
    fun setStudioImage(path: String) {
        val file = File(path)
        android.util.Log.d("A56AI-UI", "setStudioImage path=$path exists=${file.exists()} canRead=${file.canRead()}")
        if (!file.exists()) {
            _studioError.value = "Bild nicht gefunden: $path"
            return
        }
        _studioImage.value = file
        _studioError.value = null
    }

    fun clearStudioImage() {
        _studioImage.value = null
        _studioError.value = null
    }

    // ------------------------------------------------------------------ model browser

    /**
     * Discovery is intentionally unfiltered except for the adult gate: the
     * catalog returns NSFW, abliterated and uncensored models and the user
     * decides which sources/categories to display.
     */
    fun discover(query: String, kind: DiscoveryKind, source: ModelSource?) {
        val allowNsfw = settings.value.nsfwEnabled && settings.value.adultConfirmed
        _discovery.value = _discovery.value.copy(
            query = query,
            kind = kind,
            source = source,
            loading = true,
            error = null
        )
        viewModelScope.launch {
            val results = discoveryRepo.search(query, kind, source, allowNsfw)
            // Ignore a stale response when the user typed/changed filters while
            // the previous request was still running.
            val state = _discovery.value
            if (state.query != query || state.kind != kind || state.source != source) return@launch
            val visible = results.filter { allowNsfw || !it.nsfw }
            _discovery.value = state.copy(
                loading = false,
                results = visible,
                error = if (results.isEmpty()) {
                    "Keine Treffer. Quelle prüfen, Suchbegriff ändern oder Token hinterlegen."
                } else null
            )
        }
    }

    fun openDiscoverySheet(model: DiscoveryModel) {
        _discoverySheet.value = model
    }

    fun closeDiscoverySheet() {
        _discoverySheet.value = null
    }

    fun downloadDiscovery(model: DiscoveryModel, file: RemoteFile) {
        val url = if (model.source == ModelSource.CIVITAI) modelStore.civitaiDownloadUrl(file) else file.url
        modelStore.download(
            id = "${model.id}:${file.name}",
            displayName = file.name,
            url = url,
            fileName = file.name,
            isLora = model.isLora || file.name.contains("lora", true)
        )
    }

    fun selectImageModel(path: String) {
        loadedImagePath = ""
        _localImageModel.value = null
        updateSettings { it.copy(imageModelPath = path) }
    }

    fun searchRemote(query: String, source: ModelSource, loraOnly: Boolean) {
        _remote.value = _remote.value.copy(query = query, source = source, loraOnly = loraOnly, loading = true, error = null)
        viewModelScope.launch {
            val results = if (source == ModelSource.HUGGINGFACE) {
                modelStore.searchHuggingFace(query, ggufOnly = !loraOnly, loraOnly = loraOnly)
            } else {
                modelStore.searchCivitai(query, allowNsfw = settings.value.nsfwEnabled)
            }
            _remote.value = _remote.value.copy(
                loading = false,
                results = results.filter { !it.nsfw || settings.value.nsfwEnabled },
                error = if (results.isEmpty()) "Keine Treffer (oder Netzwerk/Tarif blockiert)." else null
            )
        }
    }

    fun openFileSheet(model: RemoteModel) {
        _fileSheet.value = model
        if (model.files.isNotEmpty()) {
            _remoteFiles.update { it + (model.id to model.files) }
            return
        }
        if (model.source != ModelSource.HUGGINGFACE) return
        viewModelScope.launch {
            val files = modelStore.huggingFaceFiles(model.id)
            _remoteFiles.update { it + (model.id to files) }
        }
    }

    fun closeFileSheet() {
        _fileSheet.value = null
    }

    fun downloadRemote(model: RemoteModel, file: RemoteFile) {
        val url = if (model.source == ModelSource.CIVITAI) modelStore.civitaiDownloadUrl(file) else file.url
        modelStore.download(
            id = "${model.id}:${file.name}",
            displayName = file.name,
            url = url,
            fileName = file.name,
            isLora = file.name.contains("lora", true) || model.kind == "LORA"
        )
    }

    fun installCurated(entry: ModelEntry) {
        val url = entry.downloadUrl ?: return
        modelStore.download(entry.id, entry.name, url, entry.fileName)
    }

    fun deleteInstalled(model: InstalledModel) {
        if (model.file.absolutePath == loadedPath) {
            LocalInference.unload()
            loadedPath = ""
            _loadedModel.value = null
        }
        modelStore.delete(model)
    }

    fun refreshModels() = modelStore.refreshInstalled()

    // ------------------------------------------------------------------ persistence

    private fun saveConversations() {
        val context = getApplication<Application>()
        val array = JSONArray()
        _conversations.value.take(30).forEach { conv ->
            val messages = JSONArray()
            conv.messages.forEach { m ->
                messages.put(JSONObject().apply {
                    put("id", m.id)
                    put("role", m.role.name)
                    put("text", m.text)
                    m.meta?.let { put("meta", it) }
                    m.imagePath?.let { put("image", it) }
                    put("error", m.error)
                })
            }
            array.put(JSONObject().apply {
                put("id", conv.id)
                put("title", conv.title)
                put("created", conv.createdAt)
                put("messages", messages)
            })
        }
        context.getSharedPreferences("galaxya56_chats", 0)
            .edit().putString("conversations", array.toString()).apply()
    }

    private fun loadConversations() {
        val context = getApplication<Application>()
        val raw = context.getSharedPreferences("galaxya56_chats", 0)
            .getString("conversations", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val list = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val msgs = o.optJSONArray("messages") ?: JSONArray()
                Conversation(
                    id = o.optString("id"),
                    title = o.optString("title", "Chat"),
                    createdAt = o.optLong("created"),
                    messages = (0 until msgs.length()).map { mi ->
                        val m = msgs.getJSONObject(mi)
                        ChatMessage(
                            id = m.optLong("id"),
                            role = runCatching { Role.valueOf(m.optString("role")) }.getOrDefault(Role.ASSISTANT),
                            text = m.optString("text"),
                            meta = m.optString("meta").takeIf { it.isNotBlank() },
                            imagePath = m.optString("image").takeIf { it.isNotBlank() },
                            error = m.optBoolean("error")
                        )
                    }
                )
            }
            _conversations.value = list
            _activeId.value = list.firstOrNull()?.id.orEmpty()
        }
    }
}
