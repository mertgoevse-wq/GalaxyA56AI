package com.galaxya56.ai.inference

import org.json.JSONObject

interface TokenCallback {
    fun onToken(text: String)
}

data class LoadResult(
    val ok: Boolean,
    val error: String = "",
    val description: String = "",
    val context: Int = 0,
    val params: Long = 0,
    val sizeBytes: Long = 0,
    val backend: String = "CPU"
)

data class GenerateResult(
    val ok: Boolean,
    val text: String,
    val tokens: Int,
    val promptMs: Long,
    val genMs: Long,
    val stopped: Boolean,
    val contextFull: Boolean,
    val error: String = ""
) {
    val tokensPerSecond: Double
        get() = if (genMs > 0) tokens * 1000.0 / genMs else 0.0
}

/**
 * Thin JNI façade over the vendored llama.cpp. All calls block; invoke them on
 * Dispatchers.IO.
 */
object LocalInference {

    init {
        System.loadLibrary("galaxya56")
    }

    external fun nativeHardwareJson(): String
    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int, gpuLayers: Int): String
    external fun nativeUnload(): String
    external fun nativeModelInfo(): String
    external fun nativeApplyLora(path: String, scale: Float): String
    external fun nativeClearLoras(): String
    external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenCallback
    ): String
    external fun nativeStop()
    external fun nativeBackend(): String
    external fun nativeNnapiProbe(): String

    fun hardware(): HardwareInfo = HardwareInfo.parse(nativeHardwareJson())

    fun nnapiProbe(): String = try {
        nativeNnapiProbe()
    } catch (t: Throwable) {
        """{"ok":false,"error":"${t.message ?: "NNAPI-Probe nicht verfügbar"}"}"""
    }

    fun backendName(): String = try {
        nativeBackend()
    } catch (t: Throwable) {
        "native unavailable"
    }

    fun load(path: String, contextSize: Int, threads: Int, gpuLayers: Int = 0): LoadResult {
        val json = nativeLoadModel(path, contextSize, threads, gpuLayers)
        val o = JSONObject(json)
        return LoadResult(
            ok = o.optBoolean("ok"),
            error = o.optString("error"),
            description = o.optString("description"),
            context = o.optInt("context"),
            params = o.optLong("params"),
            sizeBytes = o.optLong("sizeBytes"),
            backend = o.optString("backend", "CPU")
        )
    }

    fun unload() {
        runCatching { nativeUnload() }
    }

    fun applyLora(path: String, scale: Float): String = nativeApplyLora(path, scale)

    fun clearLoras(): String = nativeClearLoras()

    fun stop() {
        runCatching { nativeStop() }
    }

    fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        onToken: (String) -> Unit
    ): GenerateResult {
        val callback = object : TokenCallback {
            override fun onToken(text: String) = onToken(text)
        }
        val json = nativeGenerate(prompt, maxTokens, temperature, topP, topK, repeatPenalty, callback)
        val o = JSONObject(json)
        return GenerateResult(
            ok = o.optBoolean("ok"),
            text = o.optString("text"),
            tokens = o.optInt("tokens"),
            promptMs = o.optLong("promptMs"),
            genMs = o.optLong("ms"),
            stopped = o.optBoolean("stopped"),
            contextFull = o.optBoolean("contextFull"),
            error = o.optString("error")
        )
    }

    fun modelInfo(): JSONObject = JSONObject(nativeModelInfo())
}
