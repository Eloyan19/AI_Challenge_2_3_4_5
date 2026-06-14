package com.example.petapp.domain.strategy

import android.util.Log
import com.example.petapp.data.ChatRequest
import com.example.petapp.data.DeepSeekApiService
import com.example.petapp.data.Message
import com.example.petapp.domain.model.StrategyType

class SummaryStrategy(
    private val apiService: DeepSeekApiService,
    var keepLastN: Int = 10
) : ContextStrategy {

    override val type = StrategyType.SUMMARY

    private var _summary: String? = null
    override val auxData: String? get() = _summary
    override var onAuxDataUpdated: ((String?) -> Unit)? = null

    var summary: String?
        get() = _summary
        set(v) { _summary = v }

    override suspend fun prepareContext(history: MutableList<Message>) {
        val excess = history.size - keepLastN
        if (excess <= 0) return

        val toSummarize = history.take(excess)
        val newSummary = generateSummary(toSummarize)
        if (newSummary != null) {
            _summary = newSummary
            repeat(excess) { history.removeAt(0) }
            onAuxDataUpdated?.invoke(newSummary)
            Log.d("SummaryStrategy", "Summary updated, history trimmed to ${history.size}")
        }
    }

    override fun buildMessages(history: List<Message>): List<Message> {
        val s = _summary ?: return history
        return listOf(
            Message(
                role    = "system",
                content = "Краткое содержание предыдущей части диалога (не упоминай, что это summary):\n$s"
            )
        ) + history
    }

    override suspend fun afterTurn(history: List<Message>) = Unit

    override fun reset() {
        _summary = null
        onAuxDataUpdated?.invoke(null)
    }

    private suspend fun generateSummary(messages: List<Message>): String? {
        return try {
            val request = ChatRequest(
                model       = "deepseek-v4-flash",
                messages    = listOf(Message(role = "user", content = buildPrompt(messages))),
                maxTokens   = 600,
                temperature = 0.3
            )
            apiService.getChatCompletion(request)
                .choices?.firstOrNull()?.message?.content
        } catch (e: Exception) {
            Log.e("SummaryStrategy", "Summarization failed: ${e.localizedMessage}")
            null
        }
    }

    private fun buildPrompt(messages: List<Message>): String {
        val existing = _summary
        val sb = StringBuilder()
        if (existing != null) {
            sb.append("Существующее краткое содержание:\n$existing\n\n")
            sb.append("Дополни его следующими новыми сообщениями. ")
            sb.append("Верни обновлённое краткое содержание — 3–6 предложений, сохрани ключевые факты.\n\n")
        } else {
            sb.append("Сделай краткое содержание диалога — 3–6 предложений, сохрани ключевые факты:\n\n")
        }
        messages.forEach { msg ->
            val label = when (msg.role) {
                "user"      -> "Пользователь"
                "assistant" -> "Ассистент"
                "tool"      -> "Инструмент"
                else        -> return@forEach
            }
            val text = msg.content?.take(500) ?: return@forEach
            sb.append("$label: $text\n")
        }
        return sb.toString()
    }
}
