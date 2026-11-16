package com.galaxya56.ai.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32

data class StudioRecipe(
    val prompt: String,
    val negative: String,
    val modelPath: String,
    val modelName: String,
    val modelHash: String,
    val seed: Long,
    val cfg: Float,
    val steps: Int,
    val sampler: String,
    val width: Int,
    val height: Int,
    val vaeTiling: Boolean,
    val loraPath: String,
    val loraScale: Float,
    val controlNetPath: String = "",
    val controlImagePath: String = "",
    val controlStrength: Float = 0.9f,
    val controlType: String = "none",
    val controlCanny: Boolean = false,
    val backend: String,
    val ms: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = JSONObject().apply {
        put("prompt", prompt)
        put("negative", negative)
        put("modelPath", modelPath)
        put("modelName", modelName)
        put("modelHash", modelHash)
        put("seed", seed)
        put("cfg", cfg.toDouble())
        put("steps", steps)
        put("sampler", sampler)
        put("width", width)
        put("height", height)
        put("vaeTiling", vaeTiling)
        put("loraPath", loraPath)
        put("loraScale", loraScale.toDouble())
        put("controlNetPath", controlNetPath)
        put("controlImagePath", controlImagePath)
        put("controlStrength", controlStrength.toDouble())
        put("controlType", controlType)
        put("controlCanny", controlCanny)
        put("backend", backend)
        put("ms", ms)
        put("createdAt", createdAt)
    }.toString(2)

    companion object {
        fun fromJson(raw: String): StudioRecipe? = runCatching {
            val o = JSONObject(raw)
            StudioRecipe(
                prompt = o.optString("prompt"),
                negative = o.optString("negative"),
                modelPath = o.optString("modelPath"),
                modelName = o.optString("modelName"),
                modelHash = o.optString("modelHash"),
                seed = o.optLong("seed", -1),
                cfg = o.optDouble("cfg", 7.0).toFloat(),
                steps = o.optInt("steps", 8),
                sampler = o.optString("sampler", "euler_a"),
                width = o.optInt("width", 512),
                height = o.optInt("height", 512),
                vaeTiling = o.optBoolean("vaeTiling"),
                loraPath = o.optString("loraPath"),
                loraScale = o.optDouble("loraScale", 0.8).toFloat(),
                controlNetPath = o.optString("controlNetPath"),
                controlImagePath = o.optString("controlImagePath"),
                controlStrength = o.optDouble("controlStrength", 0.9).toFloat(),
                controlType = o.optString("controlType", "none"),
                controlCanny = o.optBoolean("controlCanny"),
                backend = o.optString("backend"),
                ms = o.optLong("ms"),
                createdAt = o.optLong("createdAt")
            )
        }.getOrNull()
    }
}

object ImageMetadataStore {

    fun save(image: File, recipe: StudioRecipe) {
        runCatching {
            val json = recipe.toJson()
            File(image.parentFile, image.nameWithoutExtension + ".json").writeText(json)
            PngTextChunk.write(image, "galaxya56", json)
            android.util.Log.i("A56AI-META", "saved ${image.name} seed=${recipe.seed} hash=${recipe.modelHash.take(12)}")
        }.onFailure { android.util.Log.e("A56AI-META", "metadata save failed: ${it.message}") }
    }

    fun read(image: File): StudioRecipe? {
        val sidecar = File(image.parentFile, image.nameWithoutExtension + ".json")
        if (sidecar.isFile) return StudioRecipe.fromJson(sidecar.readText())
        return PngTextChunk.read(image, "galaxya56")?.let(StudioRecipe::fromJson)
    }

    fun list(dir: File): List<Pair<File, StudioRecipe>> {
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".png", true) }
            ?.mapNotNull { image -> read(image)?.let { image to it } }
            ?.sortedByDescending { it.first.lastModified() }
            ?: emptyList()
    }
}

/** Small PNG tEXt writer; keeps the image bytes untouched. */
object PngTextChunk {

    fun write(file: File, keyword: String, value: String) {
        val bytes = file.readBytes()
        if (bytes.size < 8 || readInt(bytes, 0) != 0x89504e47.toInt()) return
        val data = keyword.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0) + value.toByteArray(Charsets.UTF_8)
        val chunk = ByteArrayOutputStream().use { body ->
            DataOutputStream(body).use { out ->
                out.writeInt(data.size)
                out.write("tEXt".toByteArray(Charsets.US_ASCII))
                out.write(data)
                val crc = CRC32().apply {
                    update("tEXt".toByteArray(Charsets.US_ASCII))
                    update(data)
                }
                out.writeInt(crc.value.toInt())
            }
            body.toByteArray()
        }
        val insertAt = firstIdatOffset(bytes) ?: return
        val out = ByteArray(bytes.size + chunk.size)
        System.arraycopy(bytes, 0, out, 0, insertAt)
        System.arraycopy(chunk, 0, out, insertAt, chunk.size)
        System.arraycopy(bytes, insertAt, out, insertAt + chunk.size, bytes.size - insertAt)
        file.writeBytes(out)
    }

    fun read(file: File, key: String): String? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size < 8) return null
        var pos = 8
        while (pos + 12 <= bytes.size) {
            val len = readInt(bytes, pos)
            if (len < 0 || pos + 12 + len > bytes.size) return null
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            if (type == "tEXt") {
                val data = String(bytes, pos + 8, len, Charsets.ISO_8859_1)
                val zero = data.indexOf('\u0000')
                if (zero > 0 && data.substring(0, zero) == key) return data.substring(zero + 1)
            }
            if (type == "IEND") break
            pos += 12 + len
        }
        return null
    }

    private fun firstIdatOffset(bytes: ByteArray): Int? {
        var pos = 8
        while (pos + 12 <= bytes.size) {
            val len = readInt(bytes, pos)
            if (len < 0 || pos + 12 + len > bytes.size) return null
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") return pos
            if (type == "IEND") return null
            pos += 12 + len
        }
        return null
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xff) shl 24) or
            ((bytes[at + 1].toInt() and 0xff) shl 16) or
            ((bytes[at + 2].toInt() and 0xff) shl 8) or
            (bytes[at + 3].toInt() and 0xff)
}
