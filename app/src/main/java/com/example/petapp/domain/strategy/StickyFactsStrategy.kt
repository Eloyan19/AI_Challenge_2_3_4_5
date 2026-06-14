package com.example.petapp.domain.strategy

import android.util.Log
import com.example.petapp.data.ChatRequest
import com.example.petapp.data.DeepSeekApiService
import com.example.petapp.data.Message
import com.example.petapp.domain.model.StrategyType

/**
 * Maintains a compact bullet-point list of key facts extracted after every agent turn.
 *
 * **How it works:**
 * 1. [prepareContext] trims the live history to [keepLastN] messages (same as sliding window).
 * 2. [buildMessages] prepends the current facts as a `"system"` message so the model
 *    always has key context even when old messages are dropped.
 * 3. [afterTurn] extracts/updates facts from the last `keepLastN * 2` messages via
 *    `deepseek-v4-flash`. Up to 10 bullet points are produced (each starting with `• `).
 *
 * Facts are persisted to the database via [onAuxDataUpdated] and restored by
 * [com.example.petapp.ui.MainViewModel] on app restart.
 *
 * @param apiService DeepSeek API used for fact extraction (billed against `deepseek-v4-flash`).
 * @param keepLastN Number of most-recent messages kept as the live window.
 */
class StickyFactsStrategy(
    private val apiService: DeepSeekApiService,
    var keepLastN: Int = 6
) : ContextStrategy {

    override val type = StrategyType.STICKY_FACTS

    private var _facts: String? = null
    override val auxData: String? get() = _facts
    override var onAuxDataUpdated: ((String?) -> Unit)? = null

    /** Allows [com.example.petapp.ui.MainViewModel] to restore previously saved facts. */
    var facts: String?
        get() = _facts
        set(v) { _facts = v }

    /** Trims the live history to [keepLastN] by discarding oldest messages from the front. */
    override suspend fun prepareContext(history: MutableList<Message>) {
        while (history.size > keepLastN) history.removeAt(0)
    }

    /**
     * Prepends current facts as a `"system"` message if they are available.
     * The facts block is sent on every request so the model retains key context
     * even after messages have been trimmed from history.
     */
    override fun buildMessages(history: List<Message>): List<Message> {
        val f = _facts ?: return history
        return listOf(
            Message(role = "system", content = "Ключевые факты из диалога:\n$f")
        ) + history
    }

    /**
     * Asynchronously extracts/updates facts from the most recent messages after a successful turn.
     * Uses up to `keepLastN * 2` messages to have enough context without hitting token limits.
     * Silent failure — if the LLM call fails, existing facts are preserved unchanged.
     */
    override suspend fun afterTurn(history: List<Message>) {
        val updated = extractFacts(history.takeLast(keepLastN * 2))
        if (updated != null) {
            _facts = updated
            onAuxDataUpdated?.invoke(updated)
            Log.d("StickyFactsStrategy", "Facts updated (${updated.length} chars)")
        }
    }

    /** Clears the in-memory facts list and notifies the persistence callback. */
    override fun reset() {
        _facts = null
        onAuxDataUpdated?.invoke(null)
    }

    /**
     * Calls the LLM to produce an updated list of up to 10 key facts from [messages].
     * If existing [_facts] are present the prompt asks the model to merge them with new content.
     * Returns null on failure so the caller skips the update.
     */
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
