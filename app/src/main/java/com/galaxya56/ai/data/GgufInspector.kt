package com.galaxya56.ai.data

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Minimal GGUF header reader. It intentionally reads only names/dimensions and
 * a few well-known metadata values; tensor payloads are never touched.
 */
data class GgufInfo(
    val ok: Boolean = false,
    val error: String = "",
    val version: Int = 0,
    val tensorCount: Int = 0,
    val metadataCount: Int = 0,
    val architecture: String = "",
    val tokenEmbeddingDim: Int = 0,
    val hasAlphasCumprod: Boolean = false,
    val hasVPred: Boolean = false,
    val tensorNamesSample: List<String> = emptyList()
)

object GgufInspector {

    fun inspect(file: File): GgufInfo {
        if (!file.isFile) return GgufInfo(error = "Datei fehlt")
        return runCatching {
            BufferedInputStream(FileInputStream(file), 1 shl 20).use { input ->
                val magic = readBytes(input, 4)
                if (magic == null || magic.decodeToString() != "GGUF") {
                    return GgufInfo(ok = true, error = "Kein GGUF-Header (Safetensors oder beschädigt)")
                }
                val version = readU32(input)
                val tensorCount = readU64(input).toInt()
                val metadataCount = readU64(input).toInt()
                if (tensorCount < 0 || tensorCount > 2_000_000 || metadataCount < 0 || metadataCount > 100_000) {
                    return GgufInfo(ok = true, error = "Unplausible GGUF-Zähler")
                }
                var architecture = ""
                var hasAlphas = false
                var hasVpred = false
                repeat(metadataCount) {
                    val key = readString(input) ?: return@repeat
                    val type = readU32(input)
                    when (type) {
                        8 -> {
                            val value = readString(input).orEmpty()
                            if (key == "general.architecture") architecture = value
                            if (key.contains("v_pred", true) || key.contains("prediction", true)) hasVpred = true
                        }
                        9 -> skipValue(input, type, 0)
                        else -> skipValue(input, type, 0)
                    }
                    if (key.contains("alphas_cumprod", true)) hasAlphas = true
                }

                var tokenDim = 0
                val names = mutableListOf<String>()
                repeat(tensorCount) {
                    val name = readString(input) ?: return@repeat
                    val dims = readU32(input)
                    var firstDim = 0L
                    repeat(dims) {
                        val d = readU64(input)
                        if (it == 0) firstDim = d
                    }
                    readU32(input) // ggml type
                    readU64(input) // data offset
                    if (name.contains("alphas_cumprod", true)) hasAlphas = true
                    if (name.contains("v_pred", true)) hasVpred = true
                    if (name.contains("token_embedding", true)) tokenDim = firstDim.toInt()
                    if (names.size < 40) names += name
                }
                GgufInfo(
                    ok = true,
                    version = version,
                    tensorCount = tensorCount,
                    metadataCount = metadataCount,
                    architecture = architecture,
                    tokenEmbeddingDim = tokenDim,
                    hasAlphasCumprod = hasAlphas,
                    hasVPred = hasVpred,
                    tensorNamesSample = names
                )
            }
        }.getOrElse { GgufInfo(error = it.message ?: "GGUF-Header nicht lesbar") }
    }

    private fun readBytes(input: BufferedInputStream, count: Int): ByteArray? {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(out, read, count - read)
            if (n < 0) return null
            read += n
        }
        return out
    }

    private fun readU32(input: BufferedInputStream): Int {
        val b = readBytes(input, 4) ?: return 0
        return (b[0].toInt() and 0xff) or
            ((b[1].toInt() and 0xff) shl 8) or
            ((b[2].toInt() and 0xff) shl 16) or
            ((b[3].toInt() and 0xff) shl 24)
    }

    private fun readU64(input: BufferedInputStream): Long {
        val b = readBytes(input, 8) ?: return 0
        var v = 0L
        for (i in 0 until 8) v = v or ((b[i].toLong() and 0xffL) shl (8 * i))
        return v
    }

    private fun readString(input: BufferedInputStream): String? {
        val len = readU64(input)
        if (len < 0 || len > 8_000_000) return null
        return readBytes(input, len.toInt())?.decodeToString()
    }

    /** GGUF metadata scalar sizes; arrays contain an element type and count. */
    private fun skipValue(input: BufferedInputStream, type: Int, depth: Int) {
        if (depth > 4) return
        when (type) {
            0, 1 -> readBytes(input, 1)
            2, 3 -> readBytes(input, 2)
            4, 5, 6 -> readBytes(input, 4)
            7 -> readBytes(input, 1)
            8 -> readString(input)
            10, 11, 12 -> readBytes(input, 8)
            9 -> {
                val elementType = readU32(input)
                val count = readU64(input).coerceAtMost(100_000_000)
                repeat(count.toInt()) { skipValue(input, elementType, depth + 1) }
            }
            else -> Unit
        }
    }
}
