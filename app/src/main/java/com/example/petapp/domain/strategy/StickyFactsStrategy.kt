package com.example.petapp.domain.strategy

import android.util.Log
import com.example.petapp.data.ChatRequest
import com.example.petapp.data.DeepSeekApiService
import com.example.petapp.data.Message
import com.example.petapp.domain.model.StrategyType

class StickyFactsStrategy(
    private val apiService: DeepSeekApiService,
    var keepLastN: Int = 6
) : ContextStrategy {

    override val type = StrategyType.STICKY_FACTS

    private var _facts: String? = null
    override val auxData: String? get() = _facts
    override var onAuxDataUpdated: ((String?) -> Unit)? = null

    var facts: String?
        get() = _facts
        set(v) { _facts = v }

    override suspend fun prepareContext(history: MutableList<Message>) {
        while (history.size > keepLastN) history.removeAt(0)
    }

    override fun buildMessages(history: List<Message>): List<Message> {
        val f = _facts ?: return history
        return listOf(
            Message(role = "system", content = "Ключевые факты из диалога:\n$f")
        ) + history
    }

    override suspend fun afterTurn(history: List<Message>) {
        val updated = extractFacts(history.takeLast(keepLastN * 2))
        if (updated != null) {
            _facts = updated
            onAuxDataUpdated?.invoke(updated)
            Log.d("StickyFactsStrategy", "Facts updated (${updated.length} chars)")
        }
    }

    override fun reset() {
        _facts = null
        onAuxDataUpdated?.invoke(null)
    }

    private suspend fun extractFacts(messages: List<Message>): String? {
        return try {
            val request = ChatRequest(
                model       = "deepseek-v4-flash",
                messages    = listOf(Message(role = "user", content = buildPrompt(messages))),
                maxTokens   = 400,
                temperature = 0.2
            )
            apiService.getChatCompletion(request)
                .choices?.firstOrNull()?.message?.content
        } catch (e: Exception) {
            Log.e("StickyFactsStrategy", "Facts extraction failed: ${e.localizedMessage}")
            null
        }
    }

    private fun buildPrompt(messages: List<Message>): String {
        val existing = _facts
        val sb = StringBuilder()
        if (existing != null) {
            sb.append("Текущий список ключевых фактов:\n$existing\n\n")
            sb.append("Обнови список, добавив новую важную информацию из следующих сообщений. ")
            sb.append("Верни обновлённый список — до 10 коротких пунктов (каждый начинается с «• »).\n\n")
        } else {
            sb.append("Выдели ключевые факты из диалога — до 10 коротких пунктов (каждый с «• »):\n\n")
        }
        messages.forEach { msg ->
            val label = when (msg.role) {
                "user"      -> "Пользователь"
                "assistant" -> "Ассистент"
                else        -> return@forEach
            }
            val text = msg.content?.take(400) ?: return@forEach
            sb.append("$label: $text\n")
        }
        return sb.toString()
    }
}
