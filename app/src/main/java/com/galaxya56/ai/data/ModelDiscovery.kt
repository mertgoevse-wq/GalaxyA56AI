package com.galaxya56.ai.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class DiscoveryKind(val label: String) {
    ALL("Alle"),
    LLM("LLM"),
    IMAGE("Bildmodelle"),
    LORA("LoRAs")
}

/**
 * A model as shown in the discovery browser. It deliberately keeps NSFW and
 * "abliterated"/uncensored models in the result set; the UI only applies the
 * adult confirmation gate before showing them.
 */
data class DiscoveryModel(
    val id: String,
    val source: ModelSource,
    val name: String,
    val author: String,
    val downloads: Int,
    val likes: Int,
    val nsfw: Boolean,
    val uncensored: Boolean,
    val baseModel: String,
    val kind: String,
    val category: String,
    val pageUrl: String,
    val tags: List<String> = emptyList(),
    val files: List<RemoteFile> = emptyList(),
    val quantizations: List<String> = emptyList(),
    val previewUrls: List<String> = emptyList()
) {
    val isLora: Boolean
        get() = kind.uppercase().let {
            it.contains("LORA") || it.contains("LOCON") || it.contains("DORA")
        } || tags.any {
            it.contains("lora", true) || it.contains("lycoris", true)
        }

    val fileCount: Int get() = files.size
    val quantLabel: String
        get() = if (quantizations.isEmpty()) "Quantisierung: Datei wählen"
        else quantizations.take(4).joinToString(" · ")
}

/**
 * Live discovery for Hugging Face and Civitai. Hugging Face list responses are
 * asked for their sibling filenames so quantizations such as Q4_K_M, Q8_0 or
 * FP16 can be shown without opening every model. Civitai returns all versions
 * and files. Downloads still happen only after an explicit tap.
 */
class ModelDiscovery(private val settings: SettingsStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun search(
        query: String,
        kind: DiscoveryKind,
        source: ModelSource?,
        allowNsfw: Boolean
    ): List<DiscoveryModel> = coroutineScope {
        val hf = async(Dispatchers.IO) {
            if (source == ModelSource.CIVITAI) emptyList()
            else runCatching { huggingFace(query.trim(), kind, allowNsfw) }
                .onFailure { Log.w(TAG, "HF discovery failed: ${it.message}") }
                .getOrDefault(emptyList())
        }
        val civitai = async(Dispatchers.IO) {
            if (source == ModelSource.HUGGINGFACE) emptyList()
            else runCatching { civitai(query.trim(), kind, allowNsfw) }
                .onFailure { Log.w(TAG, "Civitai discovery failed: ${it.message}") }
                .getOrDefault(emptyList())
        }
        (hf.await() + civitai.await())
            .distinctBy { "${it.source}:${it.id}" }
            .sortedWith(compareByDescending<DiscoveryModel> { it.downloads }.thenByDescending { it.likes })
    }

    // ------------------------------------------------------------------ Hugging Face

    private suspend fun huggingFace(
        query: String,
        kind: DiscoveryKind,
        allowNsfw: Boolean
    ): List<DiscoveryModel> = withContext(Dispatchers.IO) {
        val categories = when (kind) {
            DiscoveryKind.ALL -> listOf(DiscoveryKind.LLM, DiscoveryKind.IMAGE, DiscoveryKind.LORA)
            else -> listOf(kind)
        }
        val result = mutableListOf<DiscoveryModel>()
        for (category in categories) {
            val searches = when {
                query.isNotBlank() -> listOf(query)
                category == DiscoveryKind.LLM -> listOf("abliterated", "uncensored gguf")
                category == DiscoveryKind.IMAGE -> buildList {
                    add("stable diffusion")
                    if (allowNsfw) add("nsfw")
                }
                category == DiscoveryKind.LORA -> buildList {
                    add("lora")
                    if (allowNsfw) add("nsfw lora")
                }
                else -> emptyList()
            }
            for (search in searches.distinct()) {
                result += huggingFaceRequest(search, category)
            }
        }
        result.distinctBy { it.id }
    }

    private fun huggingFaceRequest(search: String, kind: DiscoveryKind): List<DiscoveryModel> {
        val url = "https://huggingface.co/api/models".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("search", search)
            .addQueryParameter("limit", "30")
            .addQueryParameter("sort", "downloads")
            .addQueryParameter("direction", "-1")
            .addQueryParameter("expand[]", "siblings")
            .apply {
                when (kind) {
                    DiscoveryKind.LLM -> addQueryParameter("filter", "gguf")
                    DiscoveryKind.LORA -> addQueryParameter("filter", "lora")
                    DiscoveryKind.IMAGE -> addQueryParameter("pipeline_tag", "text-to-image")
                    DiscoveryKind.ALL -> Unit
                }
            }
            .build()

        val request = Request.Builder().url(url)
            .header("User-Agent", "GalaxyA56AI/2.0 (Android)")
            .apply {
                val token = settings.value.hfToken
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }
            .build()

        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string().orEmpty()
        }
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val objectModel = array.optJSONObject(index) ?: return@mapNotNull null
            parseHuggingFaceModel(objectModel, kind)
        }
    }

    private fun parseHuggingFaceModel(o: JSONObject, requestedKind: DiscoveryKind): DiscoveryModel? {
        val id = o.optString("modelId").ifBlank { o.optString("id") }
        if (id.isBlank()) return null
        val tags = o.optJSONArray("tags")?.let { array ->
            (0 until array.length()).map { array.optString(it) }
        } ?: emptyList()

        val files = o.optJSONArray("siblings")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val file = array.optJSONObject(index) ?: return@mapNotNull null
                val path = file.optString("rfilename")
                val ext = extensionOf(path)
                if (ext !in acceptedExtensions(requestedKind)) return@mapNotNull null
                if (requestedKind == DiscoveryKind.IMAGE && path.contains('/')) return@mapNotNull null
                if (requestedKind == DiscoveryKind.LLM && path.contains('/')) return@mapNotNull null
                RemoteFile(
                    name = path.substringAfterLast('/'),
                    sizeMb = (file.optLong("size") / (1024L * 1024L)).takeIf { it > 0 } ?: 0L,
                    url = "https://huggingface.co/$id/resolve/main/${encodePath(path)}",
                    quant = quantOf(path),
                    format = ext.uppercase()
                )
            }.distinctBy { it.name }
        } ?: emptyList()

        val uncensored = isUncensored(id, tags, files.map { it.name })
        val nsfw = tags.any {
            it.contains("nsfw", true) || it.contains("not-for-all-audiences", true)
        } || id.contains("nsfw", true) || textSuggestsNsfw(id)
        val baseModel = tags.firstOrNull { it.startsWith("base_model:") }
            ?.substringAfter(':')
            ?.substringAfterLast('/')
            ?: inferBaseModel(id, tags)
        val category = when (requestedKind) {
            DiscoveryKind.LLM -> "LLM"
            DiscoveryKind.IMAGE -> "Bildmodell"
            DiscoveryKind.LORA -> if (looksLikeImageModel(baseModel, tags)) "Bild-LoRA" else "Sprach-LoRA"
            DiscoveryKind.ALL -> ""
        }
        val kindLabel = when (requestedKind) {
            DiscoveryKind.LLM -> "GGUF"
            DiscoveryKind.IMAGE -> "Checkpoint"
            DiscoveryKind.LORA -> "LoRA"
            DiscoveryKind.ALL -> "Modell"
        }
        return DiscoveryModel(
            id = id,
            source = ModelSource.HUGGINGFACE,
            name = id.substringAfterLast('/'),
            author = o.optString("author").ifBlank { id.substringBefore('/') },
            downloads = o.optInt("downloads"),
            likes = o.optInt("likes"),
            nsfw = nsfw,
            uncensored = uncensored,
            baseModel = baseModel,
            kind = kindLabel,
            category = category,
            pageUrl = "https://huggingface.co/$id",
            tags = tags.take(16),
            files = files,
            quantizations = files.mapNotNull { it.quant }.distinct()
        )
    }

    // ------------------------------------------------------------------ Civitai

    private suspend fun civitai(
        query: String,
        kind: DiscoveryKind,
        allowNsfw: Boolean
    ): List<DiscoveryModel> = withContext(Dispatchers.IO) {
        val builder = "https://civitai.com/api/v1/models".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", "30")
            .addQueryParameter("sort", "Most Downloaded")
            .addQueryParameter("nsfw", if (allowNsfw) "true" else "false")
        if (query.isNotBlank()) builder.addQueryParameter("query", query)
        civitaiTypes(kind).forEach { builder.addQueryParameter("types", it) }

        val request = Request.Builder().url(builder.build())
            .header("User-Agent", "GalaxyA56AI/2.0 (Android)")
            .apply {
                val token = settings.value.civitaiToken
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }
            .build()

        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) "" else response.body?.string().orEmpty()
        }
        val items = runCatching { JSONObject(body).optJSONArray("items") }.getOrNull()
            ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { index ->
            val o = items.optJSONObject(index) ?: return@mapNotNull null
            parseCivitaiModel(o)
        }
    }

    private fun parseCivitaiModel(o: JSONObject): DiscoveryModel? {
        if (o.optInt("id") <= 0) return null
        val tags = o.optJSONArray("tags")?.let { array ->
            (0 until array.length()).map { array.optString(it) }
        } ?: emptyList()
        val versions = o.optJSONArray("modelVersions") ?: JSONArray()
        val files = mutableListOf<RemoteFile>()
        val previews = mutableListOf<String>()
        var baseModel = ""
        for (vi in 0 until versions.length()) {
            val version = versions.optJSONObject(vi) ?: continue
            if (baseModel.isBlank()) baseModel = version.optString("baseModel")
            version.optJSONArray("images")?.let { images ->
                for (ii in 0 until images.length()) {
                    val image = images.optJSONObject(ii) ?: continue
                    val url = image.optString("url")
                    if (url.isNotBlank() && previews.size < 6) previews += url
                }
            }
            val versionFiles = version.optJSONArray("files") ?: continue
            for (fi in 0 until versionFiles.length()) {
                val f = versionFiles.optJSONObject(fi) ?: continue
                val name = f.optString("name")
                val ext = extensionOf(name)
                if (ext !in acceptedExtensions(DiscoveryKind.ALL)) continue
                val downloadUrl = f.optString("downloadUrl")
                if (downloadUrl.isBlank()) continue
                files += RemoteFile(
                    name = name,
                    sizeMb = (f.optDouble("sizeKB") / 1024.0).toLong(),
                    url = downloadUrl,
                    quant = quantOf(name),
                    format = ext.uppercase()
                )
            }
        }
        val type = o.optString("type").uppercase()
        val category = when (type) {
            "CHECKPOINT" -> "Bildmodell"
            "LLM" -> "LLM"
            "LORA", "LOCON", "DORA" -> "Bild-LoRA"
            else -> if (looksLikeImageModel(baseModel, tags)) "Bildmodell" else "Modell"
        }
        val nsfw = o.optBoolean("nsfw") || tags.any {
            it.contains("nsfw", true) || it.contains("not-for-all-audiences", true)
        }
        val name = o.optString("name")
        val uncensored = isUncensored(name, tags, files.map { it.name })
        return DiscoveryModel(
            id = "civitai-${o.optInt("id")}",
            source = ModelSource.CIVITAI,
            name = name,
            author = o.optJSONObject("creator")?.optString("username").orEmpty(),
            downloads = o.optJSONObject("stats")?.optInt("downloadCount") ?: 0,
            likes = o.optJSONObject("stats")?.optInt("favoriteCount") ?: 0,
            nsfw = nsfw,
            uncensored = uncensored,
            baseModel = baseModel,
            kind = type.ifBlank { "Modell" },
            category = category,
            pageUrl = "https://civitai.com/models/${o.optInt("id")}",
            tags = tags.take(16),
            files = files.distinctBy { it.url },
            quantizations = files.mapNotNull { it.quant }.distinct(),
            previewUrls = previews.distinct()
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun civitaiTypes(kind: DiscoveryKind): List<String> = when (kind) {
        DiscoveryKind.ALL -> listOf("Checkpoint", "LORA", "LoCon", "DoRA", "LLM")
        DiscoveryKind.IMAGE -> listOf("Checkpoint")
        DiscoveryKind.LLM -> listOf("LLM")
        DiscoveryKind.LORA -> listOf("LORA", "LoCon", "DoRA")
    }

    private fun acceptedExtensions(kind: DiscoveryKind): Set<String> = when (kind) {
        DiscoveryKind.LLM -> setOf("gguf")
        DiscoveryKind.IMAGE -> setOf("gguf", "safetensors", "ckpt", "pt")
        DiscoveryKind.LORA -> setOf("gguf", "safetensors", "pt", "bin")
        DiscoveryKind.ALL -> setOf("gguf", "safetensors", "ckpt", "pt", "bin")
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") {
            URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
        }

    private fun quantOf(name: String): String? {
        val match = QUANT.find(name) ?: return null
        return match.value.uppercase()
    }

    private fun isUncensored(name: String, tags: List<String>, files: List<String>): Boolean {
        val haystack = (listOf(name) + tags + files).joinToString(" ").lowercase()
        return UNCENSORED_TERMS.any { haystack.contains(it) }
    }

    private fun textSuggestsNsfw(text: String): Boolean =
        NSFW_TERMS.any { text.contains(it, true) }

    private fun inferBaseModel(id: String, tags: List<String>): String {
        val haystack = (id + " " + tags.joinToString(" ")).lowercase()
        return when {
            haystack.contains("flux") -> "Flux"
            haystack.contains("pony") -> "Pony"
            haystack.contains("illustrious") -> "Illustrious"
            haystack.contains("sdxl") || haystack.contains("xl") -> "SDXL"
            haystack.contains("sd15") || haystack.contains("sd 1.5") -> "SD 1.5"
            haystack.contains("qwen") -> "Qwen"
            haystack.contains("llama") -> "Llama"
            haystack.contains("gemma") -> "Gemma"
            else -> ""
        }
    }

    private fun looksLikeImageModel(baseModel: String, tags: List<String>): Boolean {
        val haystack = (baseModel + " " + tags.joinToString(" ")).lowercase()
        return IMAGE_TERMS.any { haystack.contains(it) }
    }

    companion object {
        private const val TAG = "A56AI-DISCOVER"
        private val QUANT = Regex(
            """(?i)(IQ\d_[A-Z0-9_]+|Q\d_K(?:_[A-Z0-9]+)?|Q\d_\d|F16|BF16|FP16|FP8|FP32|INT8)"""
        )
        private val UNCENSORED_TERMS = listOf(
            "abliterated", "uncensored", "unfiltered", "unliberated",
            "jailbreak", "decensored", "nsfw", "not-for-all-audiences"
        )
        private val NSFW_TERMS = listOf("nsfw", "adult", "erotic", "hentai", "nude")
        private val IMAGE_TERMS = listOf(
            "stable diffusion", "stable-diffusion", "sdxl", "sd 1.5", "sd15",
            "flux", "pony", "illustrious", "diffusers", "text-to-image"
        )
    }
}
