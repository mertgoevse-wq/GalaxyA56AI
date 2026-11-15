package com.galaxya56.ai.ui

/**
 * Formats the conversation with the template the loaded GGUF family expects.
 * llama.cpp does not apply a chat template itself, so this happens on the
 * Kotlin side based on the model file name.
 */
object PromptBuilder {

    private enum class Template { CHATML, LLAMA3, GEMMA, PHI }

    fun build(system: String, messages: List<ChatMessage>, modelPath: String): String {
        val template = templateFor(modelPath)
        val history = messages.filter { it.text.isNotBlank() }
        return when (template) {
            Template.CHATML -> buildString {
                append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
                history.forEach { m ->
                    val role = if (m.role == Role.USER) "user" else "assistant"
                    append("<|im_start|>").append(role).append('\n')
                    append(m.text.trim()).append("<|im_end|>\n")
                }
                append("<|im_start|>assistant\n")
            }
            Template.LLAMA3 -> buildString {
                append("<|begin_of_text|>")
                append("<|start_header_id|>system<|end_header_id|>\n\n").append(system).append("<|eot_id|>")
                history.forEach { m ->
                    val role = if (m.role == Role.USER) "user" else "assistant"
                    append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                    append(m.text.trim()).append("<|eot_id|>")
                }
                append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            }
            Template.GEMMA -> buildString {
                append("<start_of_turn>user\n")
                append(system).append("\n\n")
                history.forEachIndexed { index, m ->
                    if (m.role == Role.USER) {
                        append(m.text.trim()).append("<end_of_turn>\n<start_of_turn>model\n")
                    } else {
                        append(m.text.trim()).append("<end_of_turn>\n")
                        if (index != history.lastIndex) append("<start_of_turn>user\n")
                    }
                }
            }
            Template.PHI -> buildString {
                append("<|system|>\n").append(system).append("<|end|>\n")
                history.forEach { m ->
                    val role = if (m.role == Role.USER) "user" else "assistant"
                    append("<|").append(role).append("|>\n").append(m.text.trim()).append("<|end|>\n")
                }
                append("<|assistant|>\n")
            }
        }
    }

    private fun templateFor(path: String): Template {
        val name = path.lowercase()
        return when {
            name.contains("gemma") -> Template.GEMMA
            name.contains("phi") -> Template.PHI
            name.contains("llama-3") || name.contains("llama3") -> Template.LLAMA3
            else -> Template.CHATML
        }
    }
}
