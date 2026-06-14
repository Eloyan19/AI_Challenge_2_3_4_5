package com.example.petapp.data

import android.util.Log

class ContextCompressor(private val apiService: DeepSeekApiService) {

    var enabled: Boolean = false
    var keepLastN: Int = 10

    var summary: String? = null

    var onSummaryUpdated: ((String?) -> Unit)? = null

    /**
     * Если история длиннее keepLastN, суммаризирует старые сообщения и обрезает список.
     * Вызывается до начала agentLoop, модифицирует history на месте.
     */
    suspend fun prepareContext(history: MutableList<Message>) {
        val excess = history.size - keepLastN
        if (excess <= 0) return

        val toSummarize = history.take(excess)
        Log.d("ContextCompressor", "Summarizing $excess messages, keeping $keepLastN")

        val newSummary = generateSummary(toSummarize)
        if (newSummary != null) {
            summary = newSummary
            repeat(excess) { history.removeAt(0) }
            onSummaryUpdated?.invoke(newSummary)
            Log.d("ContextCompressor", "Summary updated, history trimmed to ${history.size}")
        }
    }

    /**
     * Строит итоговый список сообщений для запроса:
     * если сжатие включено и summary есть — prepend system-сообщение с summary.
     */
    fun buildMessages(history: List<Message>): List<Message> {
        val s = summary
        if (!enabled || s.isNullOrBlank()) return history
        return listOf(
            Message(
                role    = "system",
                content = "Краткое содержание предыдущей части диалога (не упоминай, что это summary):\n$s"
            )
        ) + history
    }

    fun reset() {
        summary = null
        onSummaryUpdated?.invoke(null)
    }

    // ── Summarization API call ────────────────────────────────────────────────

    private suspend fun generateSummary(messages: List<Message>): String? {
        val prompt = buildSummaryPrompt(messages)
        return try {
            val request = ChatRequest(
                model     = "deepseek-v4-flash",   // всегда flash — дёшево и быстро
                messages  = listOf(Message(role = "user", content = prompt)),
                maxTokens = 600,
                temperature = 0.3
            )
            apiService.getChatCompletion(request)
                .choices?.firstOrNull()?.message?.content
        } catch (e: Exception) {
            Log.e("ContextCompressor", "Summarization failed: ${e.localizedMessage}")
            null
        }
    }

    private fun buildSummaryPrompt(messages: List<Message>): String {
        val existing = summary
        val sb = StringBuilder()

        if (existing != null) {
            sb.append("Существующее краткое содержание диалога:\n$existing\n\n")
            sb.append("Дополни его следующими новыми сообщениями. ")
            sb.append("Верни обновлённое краткое содержание — 3–6 предложений, сохрани ключевые факты и решения.\n\n")
        } else {
            sb.append("Сделай краткое содержание диалога — 3–6 предложений, сохрани ключевые факты и решения:\n\n")
        }

        messages.forEach { msg ->
            val label = when (msg.role) {
                "user"      -> "Пользователь"
                "assistant" -> "Ассистент"
                "tool"      -> "Инструмент"
                else        -> msg.role
            }
            val text = msg.content?.take(500) ?: return@forEach  // пропускаем tool_calls без текста
            sb.append("$label: $text\n")
        }

        return sb.toString()
    }
}
