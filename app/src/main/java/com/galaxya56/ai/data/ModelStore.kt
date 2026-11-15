package com.galaxya56.ai.data

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

enum class DownloadStatus { RUNNING, DONE, ERROR, CANCELLED }

data class DownloadState(
    val id: String,
    val name: String,
    val progress: Float = 0f,
    val downloadedMb: Long = 0,
    val totalMb: Long = 0,
    val speedMBps: Double = 0.0,
    val status: DownloadStatus = DownloadStatus.RUNNING,
    val error: String? = null
)

data class InstalledModel(
    val file: File,
    val name: String,
    val sizeMb: Long,
    val isLora: Boolean
)

data class RemoteFile(
    val name: String,
    val sizeMb: Long,
    val url: String
)

data class RemoteModel(
    val id: String,
    val source: ModelSource,
    val name: String,
    val author: String,
    val downloads: Int,
    val likes: Int,
    val nsfw: Boolean,
    val baseModel: String,
    val kind: String,
    val pageUrl: String,
    val tags: List<String> = emptyList(),
    val files: List<RemoteFile> = emptyList()
)

class ModelStore(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val modelsDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "models")
    val lorasDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "loras")
    val outputDir: File = File(context.filesDir, "generated")

    private val _installed = MutableStateFlow<List<InstalledModel>>(emptyList())
    val installed: StateFlow<List<InstalledModel>> = _installed

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads

    init {
        modelsDir.mkdirs()
        lorasDir.mkdirs()
        outputDir.mkdirs()
        refreshInstalled()
    }

    fun refreshInstalled() {
        val all = mutableListOf<InstalledModel>()
        for ((dir, isLora) in listOf(modelsDir to false, lorasDir to true)) {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".part").not()) {
                    all += InstalledModel(
                        file = f,
                        name = f.name,
                        sizeMb = f.length() / (1024L * 1024L),
                        isLora = isLora
                    )
                }
            }
        }
        _installed.value = all.sortedBy { it.name.lowercase() }
    }

    fun delete(model: InstalledModel): Boolean {
        val ok = model.file.delete()
        refreshInstalled()
        return ok
    }

    fun cancel(id: String) {
        val state = _downloads.value[id] ?: return
        _downloads.value = _downloads.value + (id to state.copy(status = DownloadStatus.CANCELLED))
    }

    fun download(
        id: String,
        displayName: String,
        url: String,
        fileName: String,
        isLora: Boolean = false,
        token: String? = null
    ) {
        if (_downloads.value[id]?.status == DownloadStatus.RUNNING) return

        _downloads.value = _downloads.value + (id to DownloadState(id = id, name = displayName))

        scope.launch {
            val dir = if (isLora) lorasDir else modelsDir
            dir.mkdirs()
            val target = File(dir, sanitize(fileName))
            val part = File(dir, sanitize(fileName) + ".part")

            try {
                val free = StatFs(dir.absolutePath).availableBytes
                if (free < 200L * 1024L * 1024L) {
                    throw IllegalStateException("Zu wenig Speicherplatz frei")
                }

                val parsed = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Ungültige URL")
                val request = Request.Builder()
                    .url(parsed)
                    .header("User-Agent", "GalaxyA56AI/2.0 (Android)")
                    .apply {
                        val existing = if (part.exists()) part.length() else 0L
                        if (existing > 0) header("Range", "bytes=$existing-")
                        val auth = token?.takeIf { it.isNotBlank() }
                            ?: if (url.contains("huggingface.co")) settings.value.hfToken else settings.value.civitaiToken
                        if (!auth.isNullOrBlank()) header("Authorization", "Bearer $auth")
                    }
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IllegalStateException("HTTP ${response.code} — ${response.message}")
                    }
                    val body = response.body ?: throw IllegalStateException("Leere Antwort")
                    val append = response.code == 206
                    val total = (if (append) part.length() else 0L) + (body.contentLength().coerceAtLeast(0L))

                    body.byteStream().use { input ->
                        java.io.FileOutputStream(part, append).buffered().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            var written = if (append) part.length() else 0L
                            var lastUpdate = 0L
                            var lastBytes = written
                            var lastTime = System.currentTimeMillis()

                            while (true) {
                                if (_downloads.value[id]?.status == DownloadStatus.CANCELLED) {
                                    return@launch
                                }
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                written += read

                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 300) {
                                    val dt = (now - lastTime).coerceAtLeast(1) / 1000.0
                                    val speed = (written - lastBytes) / dt / (1024.0 * 1024.0)
                                    _downloads.value = _downloads.value + (id to DownloadState(
                                        id = id,
                                        name = displayName,
                                        progress = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f,
                                        downloadedMb = written / (1024L * 1024L),
                                        totalMb = total / (1024L * 1024L),
                                        speedMBps = speed,
                                        status = DownloadStatus.RUNNING
                                    ))
                                    lastUpdate = now
                                    lastBytes = written
                                    lastTime = now
                                }
                            }
                        }
                    }
                }

                if (_downloads.value[id]?.status == DownloadStatus.CANCELLED) {
                    return@launch
                }

                if (target.exists()) target.delete()
                if (!part.renameTo(target)) throw IllegalStateException("Datei konnte nicht abgelegt werden")

                _downloads.value = _downloads.value + (id to DownloadState(
                    id = id, name = displayName, progress = 1f,
                    downloadedMb = target.length() / (1024L * 1024L),
                    totalMb = target.length() / (1024L * 1024L),
                    status = DownloadStatus.DONE
                ))
                refreshInstalled()
            } catch (t: Throwable) {
                _downloads.value = _downloads.value + (id to DownloadState(
                    id = id, name = displayName, status = DownloadStatus.ERROR,
                    error = t.message ?: t.javaClass.simpleName
                ))
                if (part.exists() && part.length() == 0L) part.delete()
            }
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(180)

    // ------------------------------------------------------------------ Hugging Face

    suspend fun searchHuggingFace(query: String, ggufOnly: Boolean = true, loraOnly: Boolean = false): List<RemoteModel> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://huggingface.co/api/models".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("search", query)
                    .addQueryParameter("limit", "30")
                    .addQueryParameter("sort", "downloads")
                    .addQueryParameter("direction", "-1")
                    .apply {
                        if (loraOnly) addQueryParameter("filter", "lora")
                        else if (ggufOnly) addQueryParameter("filter", "gguf")
                    }
                    .build()

                val request = Request.Builder().url(url)
                    .header("User-Agent", "GalaxyA56AI/2.0")
                    .apply {
                        val token = settings.value.hfToken
                        if (token.isNotBlank()) header("Authorization", "Bearer $token")
                    }
                    .build()

                val body = http.newCall(request).execute().use { it.body?.string().orEmpty() }
                val array = JSONArray(body)
                (0 until array.length()).mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("modelId")
                    if (id.isBlank()) return@mapNotNull null
                    val tags = o.optJSONArray("tags")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }
                    } ?: emptyList()
                    // "abliterated"/"uncensored" are not sexual per se � keep them visible without the 18+ gate.
                    val nsfw = tags.any { it.contains("nsfw", true) || it.contains("not-for-all-audiences", true) } ||
                        id.contains("nsfw", true)
                    RemoteModel(
                        id = id,
                        source = ModelSource.HUGGINGFACE,
                        name = id.substringAfterLast('/'),
                        author = o.optString("author", id.substringBefore('/')),
                        downloads = o.optInt("downloads"),
                        likes = o.optInt("likes"),
                        nsfw = nsfw,
                        baseModel = tags.firstOrNull { it.startsWith("base_model:") }?.substringAfter(':').orEmpty(),
                        kind = if (loraOnly) "LoRA" else "GGUF",
                        pageUrl = "https://huggingface.co/$id",
                        tags = tags.take(12)
                    )
                }
            } catch (t: Throwable) {
                emptyList()
            }
        }

    suspend fun huggingFaceFiles(modelId: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        try {
            val url = "https://huggingface.co/api/models/$modelId".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("blobs", "true")
                .build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "GalaxyA56AI/2.0")
                .apply {
                    val token = settings.value.hfToken
                    if (token.isNotBlank()) header("Authorization", "Bearer $token")
                }
                .build()
            val body = http.newCall(request).execute().use { it.body?.string().orEmpty() }
            val siblings = JSONObject(body).optJSONArray("siblings") ?: JSONArray()
            (0 until siblings.length()).mapNotNull { i ->
                val f = siblings.optJSONObject(i) ?: return@mapNotNull null
                val name = f.optString("rfilename")
                if (!name.endsWith(".gguf", true)) return@mapNotNull null
                RemoteFile(
                    name = name,
                    sizeMb = f.optLong("size") / (1024L * 1024L),
                    url = "https://huggingface.co/$modelId/resolve/main/$name"
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ Civitai

    suspend fun searchCivitai(query: String, allowNsfw: Boolean): List<RemoteModel> = withContext(Dispatchers.IO) {
        try {
            val url = "https://civitai.com/api/v1/models".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("limit", "24")
                .addQueryParameter("types", "Checkpoint,LORA")
                .addQueryParameter("sort", "Most Downloaded")
                .addQueryParameter("nsfw", if (allowNsfw) "true" else "false")
                .build()

            val request = Request.Builder().url(url)
                .header("User-Agent", "GalaxyA56AI/2.0")
                .apply {
                    val token = settings.value.civitaiToken
                    if (token.isNotBlank()) header("Authorization", "Bearer $token")
                }
                .build()

            val body = http.newCall(request).execute().use { it.body?.string().orEmpty() }
            val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
            (0 until items.length()).mapNotNull { i ->
                val o = items.optJSONObject(i) ?: return@mapNotNull null
                val nsfw = o.optBoolean("nsfw")
                if (nsfw && !allowNsfw) return@mapNotNull null

                val versions = o.optJSONArray("modelVersions") ?: JSONArray()
                val version = if (versions.length() > 0) versions.optJSONObject(0) else null
                val files = version?.optJSONArray("files") ?: JSONArray()
                val fileList = (0 until files.length()).mapNotNull { fi ->
                    val f = files.optJSONObject(fi) ?: return@mapNotNull null
                    val name = f.optString("name")
                    if (!name.endsWith(".safetensors", true) && !name.endsWith(".gguf", true) && !name.endsWith(".pt", true)) {
                        return@mapNotNull null
                    }
                    RemoteFile(
                        name = name,
                        sizeMb = (f.optDouble("sizeKB") / 1024.0).toLong(),
                        url = f.optString("downloadUrl")
                    )
                }
                RemoteModel(
                    id = "civitai-${o.optInt("id")}",
                    source = ModelSource.CIVITAI,
                    name = o.optString("name"),
                    author = o.optJSONObject("creator")?.optString("username").orEmpty(),
                    downloads = o.optJSONObject("stats")?.optInt("downloadCount") ?: 0,
                    likes = o.optJSONObject("stats")?.optInt("favoriteCount") ?: 0,
                    nsfw = nsfw,
                    baseModel = version?.optString("baseModel").orEmpty(),
                    kind = o.optString("type"),
                    pageUrl = "https://civitai.com/models/${o.optInt("id")}",
                    files = fileList
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun civitaiDownloadUrl(file: RemoteFile): String {
        val token = settings.value.civitaiToken
        if (token.isBlank() || file.url.isBlank()) return file.url
        val sep = if (file.url.contains("?")) "&" else "?"
        return "${file.url}${sep}token=$token"
    }
}
