package com.galaxya56.ai.data

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

enum class ModelHealth { READY, WARNING, INCOMPATIBLE, UNKNOWN }

data class ImageRecipe(
    val steps: Int,
    val cfg: Float,
    val sampler: String,
    val width: Int,
    val height: Int,
    val vaeTiling: Boolean
)

data class ModelDoctorReport(
    val path: String,
    val name: String,
    val health: ModelHealth,
    val family: String,
    val reason: String,
    val recipe: ImageRecipe?,
    val hash: String,
    val sizeMb: Long,
    val ramFreeMb: Long,
    val storageFreeMb: Long,
    val gguf: GgufInfo?,
    val notes: List<String> = emptyList()
) {
    val isImageModel: Boolean get() = recipe != null
}

/**
 * Checks a local checkpoint before loading it and derives a conservative
 * generation recipe. The checks are deliberately independent from the UI so
 * the same result can later be attached to a download or a gallery entry.
 */
class ModelDoctor(private val context: Context) {

    private val hashes = ConcurrentHashMap<String, String>()

    suspend fun inspect(file: File, baseModel: String = ""): ModelDoctorReport =
        withContext(Dispatchers.IO) {
            val name = file.name
            val lowered = "$baseModel $name".lowercase()
            val sizeMb = file.length() / (1024L * 1024L)
            val ramFreeMb = freeRamMb()
            val storageFreeMb = freeStorageMb(file.parentFile ?: context.filesDir)
            val notes = mutableListOf<String>()

            if (!file.isFile) {
                return@withContext ModelDoctorReport(
                    file.absolutePath, name, ModelHealth.UNKNOWN, "unbekannt",
                    "Datei nicht gefunden", null, "", 0, ramFreeMb, storageFreeMb, null
                )
            }

            val isGguf = name.endsWith(".gguf", true)
            val info = if (isGguf) GgufInspector.inspect(file) else null
            if (info != null && !info.ok) notes += "GGUF-Header: ${info.error}"
            if (info != null && info.metadataCount == 0) {
                notes += "Keine GGUF-Metadaten; Architektur wird aus Tensornamen geschätzt."
            }
            if (info?.tokenEmbeddingDim == 1024) notes += "SD2.x-Architektur (1024er Text-Embedding)."
            if (info?.tokenEmbeddingDim == 768) notes += "SD1.x-Architektur (768er Text-Embedding)."

            val isLlm = LLM_MARKERS.any { lowered.contains(it) }
            if (isLlm) {
                return@withContext ModelDoctorReport(
                    file.absolutePath, name, ModelHealth.READY, "LLM",
                    "GGUF-LLM; kein Bild-Recipe nötig", null, sha256(file), sizeMb,
                    ramFreeMb, storageFreeMb, info, notes
                )
            }

            val isTurbo = lowered.contains("turbo")
            val isLcm = lowered.contains("lcm") || lowered.contains("hyper-sd") || lowered.contains("hypersd")
            val isSdxl = lowered.contains("sdxl") || lowered.contains("pony") ||
                lowered.contains("illustrious") || (baseModel.lowercase().contains("xl"))
            val tokenDim = info?.tokenEmbeddingDim ?: 0

            if (isTurbo && (info == null || (!info.hasAlphasCumprod && !info.hasVPred))) {
                val why = "Turbo-Export ohne alphas_cumprod/v_pred-Metadaten. " +
                    "stable-diffusion.cpp kann daraus keinen gültigen 1-Step-Sampler bauen."
                notes += why
                Log.i("A56AI-DOCTOR", "model=$name family=SD-Turbo health=INCOMPATIBLE reason=$why")
                return@withContext ModelDoctorReport(
                    file.absolutePath, name, ModelHealth.INCOMPATIBLE, "SD-Turbo",
                    "Inkompatibler Turbo-Export", null, sha256(file), sizeMb,
                    ramFreeMb, storageFreeMb, info, notes
                )
            }

            val recipe = when {
                isLcm -> ImageRecipe(4, 1.5f, "lcm", 512, 512, false)
                isTurbo -> ImageRecipe(4, 1.0f, "euler_a", 512, 512, false)
                isSdxl -> ImageRecipe(20, 7.0f, "dpm++2m", 1024, 1024, true)
                tokenDim == 1024 -> ImageRecipe(8, 7.0f, "euler_a", 512, 512, false)
                else -> ImageRecipe(8, 7.0f, "euler_a", 512, 512, false)
            }
            val family = when {
                isLcm -> "SD1.5 LCM/Hyper"
                isTurbo -> "SD-Turbo"
                isSdxl -> "SDXL/Pony/Illustrious"
                tokenDim == 1024 -> "SD2.x"
                else -> "SD1.x"
            }
            var health = ModelHealth.READY
            var reason = "Recipe aus Architektur und Metadaten abgeleitet"
            if (info == null && !name.endsWith(".gguf", true)) {
                health = ModelHealth.WARNING
                reason = "Safetensors: Metadaten werden erst beim Laden geprüft"
            }
            if (info?.metadataCount == 0 && isGguf) {
                health = ModelHealth.WARNING
                reason = "Keine Metadaten; Recipe ist eine Heuristik"
            }
            if (recipe.width > 512 && ramFreeMb < 3000) {
                health = ModelHealth.WARNING
                reason = "SDXL ist bei ${ramFreeMb} MB freiem RAM riskant; Tiling aktivieren"
            }
            val requiredRam = (sizeMb * 1.25).toLong()
            if (ramFreeMb in 1..requiredRam) {
                health = ModelHealth.WARNING
                notes += "Modell ist ${sizeMb} MB groß, frei sind ${ramFreeMb} MB RAM."
            }
            if (storageFreeMb in 1..(sizeMb / 4)) {
                notes += "Wenig Speicherplatz: ${storageFreeMb} MB frei."
            }

            Log.i(
                "A56AI-DOCTOR",
                "model=$name family=$family health=$health steps=${recipe.steps} cfg=${recipe.cfg} " +
                    "sampler=${recipe.sampler} tokenDim=$tokenDim alphas=${info?.hasAlphasCumprod} " +
                    "vpred=${info?.hasVPred} ram=${ramFreeMb}MB storage=${storageFreeMb}MB"
            )
            ModelDoctorReport(
                file.absolutePath, name, health, family, reason, recipe, sha256(file),
                sizeMb, ramFreeMb, storageFreeMb, info, notes
            )
        }

    fun freeRamMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024L * 1024L)
    }

    fun freeStorageMb(dir: File): Long = runCatching {
        StatFs(dir.absolutePath).availableBytes / (1024L * 1024L)
    }.getOrDefault(0L)

    /**
     * Stable, fast fingerprint. Large checkpoints are sampled at both ends;
     * hashing 1.5 GB on every generation would stall the UI for seconds.
     */
    fun sha256(file: File): String = hashes.getOrPut(file.absolutePath + ":" + file.length()) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val sample = 4L * 1024 * 1024
            digest.update(file.length().toString().toByteArray())
            file.inputStream().buffered(1 shl 20).use { input ->
                val buffer = ByteArray(1 shl 20)
                var remaining = sample
                while (remaining > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    remaining -= n
                }
            }
            if (file.length() > sample * 2) {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    raf.seek(file.length() - sample)
                    val buffer = ByteArray(1 shl 20)
                    var remaining = sample
                    while (remaining > 0) {
                        val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        remaining -= n
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    companion object {
        /** Recipe hint from HF/Civitai metadata before a download. */
        fun remoteAdvice(name: String, baseModel: String): String? {
            val haystack = "$name $baseModel".lowercase()
            return when {
                haystack.contains("lcm") || haystack.contains("hyper-sd") ->
                    "LCM/Hyper-SD: 4 Steps · CFG 1.5 · Sampler lcm"
                haystack.contains("turbo") ->
                    "Turbo: nach Download prüfen – Exporte ohne alphas_cumprod/v_pred sind inkompatibel"
                haystack.contains("sdxl") || haystack.contains("pony") || haystack.contains("illustrious") ->
                    "SDXL: 20 Steps · CFG 7 · 1024×1024 · Tiling empfohlen"
                haystack.contains("sd15") || haystack.contains("stable-diffusion") ->
                    "SD1.5: 8 Steps · CFG 7 · Sampler euler_a · 512×512"
                else -> null
            }
        }

        private val LLM_MARKERS = listOf(
            "qwen", "llama", "gemma", "phi-", "mistral", "mixtral", "deepseek", "smollm"
        )
    }
}
