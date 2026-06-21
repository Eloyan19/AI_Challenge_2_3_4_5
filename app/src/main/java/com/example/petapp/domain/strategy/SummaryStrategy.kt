package com.example.petapp.domain.strategy

import android.util.Log
import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.StrategyType

/**
 * Compresses old messages into a rolling LLM-generated summary.
 *
 * @param llmService Provider-agnostic LLM service used for summarization.
 * @param providerConfig Provides [LlmProviderConfig.backgroundModel] for cheap summarization calls.
 * @param keepLastN Number of messages kept in the live window; the rest are summarized.
 */
class SummaryStrategy(
    private val llmService: LlmService,
    private val providerConfig: LlmProviderConfig,
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
            val request = LlmRequest(
                model       = providerConfig.backgroundModel,
                messages    = listOf(Message(role = "user", content = buildPrompt(messages))),
                maxTokens   = 600,
                temperature = 0.3
            )
            llmService.chat(request).content
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
