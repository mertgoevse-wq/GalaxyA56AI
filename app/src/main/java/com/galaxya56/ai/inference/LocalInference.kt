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
    val backend: String = "CPU",
    val layers: Int = 0
)

data class ImageLoadResult(
    val ok: Boolean,
    val model: String = "",
    val path: String = "",
    val error: String = "",
    val threads: Int = 0,
    val loadMs: Long = 0,
    val imageSupport: Boolean = false,
    val videoSupport: Boolean = false,
    val backend: String = "CPU",
    val requestedBackend: String = "auto",
    val fallback: Boolean = false,
    val npuDirect: Boolean = false
)

data class ImageGenerateResult(
    val ok: Boolean,
    val path: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val images: Int = 0,
    val ms: Long = 0,
    val seed: Long = -1,
    val backend: String = "",
    val sampler: String = "",
    val lora: String = "",
    val controlImage: String = "",
    val controlStrength: Float = 0f,
    val controlType: String = "",
    val error: String = ""
)

data class VideoGenerateResult(
    val ok: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val frames: Int = 0,
    val fps: Int = 0,
    val ms: Long = 0,
    val seed: Long = -1,
    val dir: String = "",
    val error: String = ""
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
    external fun nativeEnnProbe(): String
    external fun nativeEnnBenchmark(path: String): String
    external fun nativeEnnUpscale(inputPath: String, outputPath: String, modelPath: String): String
    external fun nativeImageLoad(
        path: String,
        threads: Int,
        backendMode: String,
        motionModulePath: String
    ): String
    external fun nativeImageUnload(): String
    external fun nativeImageLoadControlNet(path: String): String
    external fun nativeImageUnloadControlNet(): String
    external fun nativeImageStatus(): String
    external fun nativeImageGenerate(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfg: Float,
        vaeTiling: Boolean,
        seed: Long,
        sampler: String,
        loraPath: String,
        loraScale: Float,
        controlImage: String,
        controlStrength: Float,
        controlType: String,
        controlCanny: Boolean,
        outputPath: String
    ): String
    external fun nativeImageGenerateVideo(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        frames: Int,
        fps: Int,
        steps: Int,
        cfg: Float,
        seed: Long,
        sampler: String,
        loraPath: String,
        loraScale: Float,
        outputDir: String
    ): String
    external fun nativeImageCancel()

    fun hardware(): HardwareInfo = HardwareInfo.parse(nativeHardwareJson())

    fun nnapiProbe(): String = try {
        nativeNnapiProbe()
    } catch (t: Throwable) {
        """{"ok":false,"error":"${t.message ?: "NNAPI-Probe nicht verfügbar"}"}"""
    }

    fun ennProbe(): String = try {
        nativeEnnProbe()
    } catch (t: Throwable) {
        """{"ok":false,"available":false,"error":"${t.message ?: "ENN nicht verfügbar"}"}"""
    }

    fun ennBenchmark(path: String): String = try {
        nativeEnnBenchmark(path)
    } catch (t: Throwable) {
        """{"ok":false,"error":"${t.message ?: "ENN-Benchmark fehlgeschlagen"}"}"""
    }

    fun ennUpscale(inputPath: String, outputPath: String, modelPath: String): String = try {
        nativeEnnUpscale(inputPath, outputPath, modelPath)
    } catch (t: Throwable) {
        """{"ok":false,"error":"${t.message ?: "NPU-Upscale fehlgeschlagen"}"}"""
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
            backend = o.optString("backend", "CPU"),
            layers = o.optInt("layers")
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

    fun imageLoad(
        path: String,
        threads: Int,
        backendMode: String = "auto",
        motionModulePath: String = ""
    ): ImageLoadResult {
        val o = JSONObject(nativeImageLoad(path, threads, backendMode, motionModulePath))
        return ImageLoadResult(
            ok = o.optBoolean("ok"),
            model = o.optString("model"),
            path = o.optString("path"),
            error = o.optString("error"),
            threads = o.optInt("threads"),
            loadMs = o.optLong("loadMs"),
            imageSupport = o.optBoolean("imageSupport"),
            videoSupport = o.optBoolean("videoSupport"),
            backend = o.optString("backend", "CPU"),
            requestedBackend = o.optString("requestedBackend", backendMode),
            fallback = o.optBoolean("fallback"),
            npuDirect = o.optBoolean("npuDirect")
        )
    }

    fun imageUnload() {
        runCatching { nativeImageUnload() }
    }

    fun imageLoadControlNet(path: String): JSONObject =
        JSONObject(nativeImageLoadControlNet(path))

    fun imageUnloadControlNet(): JSONObject =
        JSONObject(nativeImageUnloadControlNet())

    fun imageStatus(): JSONObject = JSONObject(nativeImageStatus())

    fun imageGenerate(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfg: Float,
        vaeTiling: Boolean,
        seed: Long,
        sampler: String,
        loraPath: String,
        loraScale: Float,
        controlImage: String = "",
        controlStrength: Float = 0.9f,
        controlType: String = "none",
        controlCanny: Boolean = false,
        outputPath: String
    ): ImageGenerateResult {
        val o = JSONObject(
            nativeImageGenerate(
                prompt, negativePrompt, width, height, steps, cfg, vaeTiling, seed,
                sampler, loraPath, loraScale, controlImage, controlStrength, controlType,
                controlCanny, outputPath
            )
        )
        return ImageGenerateResult(
            ok = o.optBoolean("ok"),
            path = o.optString("path"),
            width = o.optInt("width"),
            height = o.optInt("height"),
            images = o.optInt("images"),
            ms = o.optLong("ms"),
            seed = o.optLong("seed", -1),
            backend = o.optString("backend"),
            sampler = o.optString("sampler"),
            lora = o.optString("lora"),
            controlImage = o.optString("controlImage"),
            controlStrength = o.optDouble("controlStrength", 0.9).toFloat(),
            controlType = o.optString("controlType"),
            error = o.optString("error")
        )
    }

    fun imageGenerateVideo(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        frames: Int,
        fps: Int,
        steps: Int,
        cfg: Float,
        seed: Long,
        sampler: String,
        loraPath: String,
        loraScale: Float,
        outputDir: String
    ): VideoGenerateResult {
        val o = JSONObject(
            nativeImageGenerateVideo(
                prompt, negativePrompt, width, height, frames, fps, steps, cfg, seed,
                sampler, loraPath, loraScale, outputDir
            )
        )
        return VideoGenerateResult(
            ok = o.optBoolean("ok"),
            width = o.optInt("width"),
            height = o.optInt("height"),
            frames = o.optInt("frames"),
            fps = o.optInt("fps"),
            ms = o.optLong("ms"),
            seed = o.optLong("seed", -1),
            dir = o.optString("dir"),
            error = o.optString("error")
        )
    }

    fun imageCancel() {
        runCatching { nativeImageCancel() }
    }

    fun modelInfo(): JSONObject = JSONObject(nativeModelInfo())
}
