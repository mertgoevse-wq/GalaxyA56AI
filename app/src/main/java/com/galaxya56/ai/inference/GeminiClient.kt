package com.galaxya56.ai.inference

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Optional cloud path. Uses a Google AI Studio API key — never an account
 * password. Works with the free tier and with billed API access.
 */
class GeminiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun streamChat(
        apiKey: String,
        model: String,
        system: String,
        prompt: String,
        temperature: Float,
        maxTokens: Int,
        onToken: (String) -> Unit
    ): String {
        if (apiKey.isBlank()) return "Kein API-Key hinterlegt"

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature.toDouble())
                put("maxOutputTokens", maxTokens)
                put("topP", 0.95)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(jsonType))
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = response.body?.string().orEmpty().take(400)
                    return "Gemini HTTP ${response.code}: $error"
                }
                val source = response.body?.source() ?: return "Leere Antwort"
                val full = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    runCatching {
                        val obj = JSONObject(payload)
                        val candidates = obj.optJSONArray("candidates") ?: return@runCatching
                        val parts = candidates.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts") ?: return@runCatching
                        for (i in 0 until parts.length()) {
                            val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                            if (text.isNotEmpty()) {
                                full.append(text)
                                onToken(text)
                            }
                        }
                    }
                }
                full.toString()
            }
        } catch (t: Throwable) {
            "Netzwerkfehler: ${t.message}"
        }
    }

    /** Returns the saved PNG file, or throws with a readable message. */
    fun generateImage(apiKey: String, prompt: String, outputDir: File): File {
        require(apiKey.isNotBlank()) { "Kein API-Key hinterlegt" }
        outputDir.mkdirs()

        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put("generationConfig", JSONObject().put(
                "responseModalities", JSONArray().put("TEXT").put("IMAGE")
            ))
        }

        val model = "gemini-2.5-flash-image"
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(jsonType))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini HTTP ${response.code}: ${raw.take(300)}")
            }
            val parts = JSONObject(raw)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Keine Bilddaten in der Antwort")

            for (i in 0 until parts.length()) {
                val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val data = inline.optString("data")
                if (data.isBlank()) continue
                val file = File(outputDir, "gen_${System.currentTimeMillis()}.png")
                file.writeBytes(Base64.decode(data, Base64.DEFAULT))
                return file
            }
            throw IllegalStateException("Antwort enthielt kein Bild")
        }
    }
}
