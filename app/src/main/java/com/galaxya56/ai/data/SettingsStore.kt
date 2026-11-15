package com.galaxya56.ai.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val backend: String = "auto",
    val contextSize: Int = 4096,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val systemPromptId: String = "general",
    val customSystemPrompt: String = "",
    val geminiKey: String = "",
    val cloudModel: String = "gemini-2.5-flash",
    val cloudEnabled: Boolean = false,
    val hfToken: String = "",
    val civitaiToken: String = "",
    val nsfwEnabled: Boolean = false,
    val adultConfirmed: Boolean = false,
    val selectedModelPath: String = "",
    val loraScale: Float = 0.8f,
    val maxTokens: Int = 512,
    val gpuLayers: Int = 0
) {
    val systemPrompt: String
        get() = if (customSystemPrompt.isNotBlank()) customSystemPrompt
        else Catalog.promptById(systemPromptId).text

    val cloudReady: Boolean get() = cloudEnabled && geminiKey.isNotBlank()
}

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("galaxya56_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    val value: AppSettings get() = _state.value

    private fun load(): AppSettings = AppSettings(
        backend = prefs.getString("backend", "auto") ?: "auto",
        contextSize = prefs.getInt("contextSize", 4096),
        threads = prefs.getInt("threads", 4),
        temperature = prefs.getFloat("temperature", 0.7f),
        topP = prefs.getFloat("topP", 0.9f),
        topK = prefs.getInt("topK", 40),
        repeatPenalty = prefs.getFloat("repeatPenalty", 1.1f),
        systemPromptId = prefs.getString("systemPromptId", "general") ?: "general",
        customSystemPrompt = prefs.getString("customSystemPrompt", "") ?: "",
        geminiKey = prefs.getString("geminiKey", "") ?: "",
        cloudModel = prefs.getString("cloudModel", "gemini-2.5-flash") ?: "gemini-2.5-flash",
        cloudEnabled = prefs.getBoolean("cloudEnabled", false),
        hfToken = prefs.getString("hfToken", "") ?: "",
        civitaiToken = prefs.getString("civitaiToken", "") ?: "",
        nsfwEnabled = prefs.getBoolean("nsfwEnabled", false),
        adultConfirmed = prefs.getBoolean("adultConfirmed", false),
        selectedModelPath = prefs.getString("selectedModelPath", "") ?: "",
        loraScale = prefs.getFloat("loraScale", 0.8f),
        maxTokens = prefs.getInt("maxTokens", 512),
        gpuLayers = prefs.getInt("gpuLayers", 0)
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit().apply {
            putString("backend", next.backend)
            putInt("contextSize", next.contextSize)
            putInt("threads", next.threads)
            putFloat("temperature", next.temperature)
            putFloat("topP", next.topP)
            putInt("topK", next.topK)
            putFloat("repeatPenalty", next.repeatPenalty)
            putString("systemPromptId", next.systemPromptId)
            putString("customSystemPrompt", next.customSystemPrompt)
            putString("geminiKey", next.geminiKey)
            putString("cloudModel", next.cloudModel)
            putBoolean("cloudEnabled", next.cloudEnabled)
            putString("hfToken", next.hfToken)
            putString("civitaiToken", next.civitaiToken)
            putBoolean("nsfwEnabled", next.nsfwEnabled)
            putBoolean("adultConfirmed", next.adultConfirmed)
            putString("selectedModelPath", next.selectedModelPath)
            putFloat("loraScale", next.loraScale)
            putInt("maxTokens", next.maxTokens)
            putInt("gpuLayers", next.gpuLayers)
        }.apply()
    }
}
