package com.example.petapp.domain.strategy

import android.util.Log
import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.LongTermMemoryEntry
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.StrategyType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 3-layer memory strategy:
 * - Layer 1 (Short-term): delegated to [innerStrategy] — can be Noop, SlidingWindow, or StickyFacts
 * - Layer 2 (Working): auto-extracted task context after each turn (persisted via [onAuxDataUpdated])
 * - Layer 3 (Long-term): persistent facts across sessions (injected from DB, read-only here)
 *
 * All layer transitions are logged under the single tag [TAG] so they can be filtered together
 * in Logcat with: tag:MemoryLayers
 *
 * @param innerStrategy Controls Layer 1 trimming and message wrapping. Defaults to SlidingWindow(8)
 *   to preserve the original behaviour when constructed without an explicit inner strategy.
 * @param shortTermWindow Controls how many messages are passed to the Layer 2 working-memory
 *   extractor in [afterTurn]. Independent of Layer 1 — the inner strategy handles trimming.
 */
class MemoryLayersStrategy(
    private val llmService: LlmService,
    private val providerConfig: LlmProviderConfig,
    val innerStrategy: ContextStrategy = SlidingWindowStrategy(8),
    var shortTermWindow: Int = 8,
    var minTurnWords: Int = MIN_TURN_WORDS,
    private val gson: Gson = Gson()
) : ContextStrategy {

    override val type = StrategyType.MEMORY_LAYERS

    // Layer 2: Working memory — only set externally via restoreWorkingMemory(); internally via afterTurn/reset.
    private var _workingMemory: String? = null
    val workingMemory: String? get() = _workingMemory

    fun restoreWorkingMemory(value: String?) {
        _workingMemory = value
        Log.d(TAG, "L2 restored: ${value?.length ?: 0} chars")
    }

    // Layer 3: Long-term memory — injected externally, read-only inside strategy logic.
    private var _longTermMemory: String? = null

    fun setLongTermMemory(value: String?) {
        _longTermMemory = value
        if (value != null) Log.d(TAG, "L3 set: ${value.length} chars")
        else               Log.d(TAG, "L3 cleared")
    }

    override val auxData: String? get() = _workingMemory
    override var onAuxDataUpdated: ((String?) -> Unit)? = null

    // Layer 1: delegate to inner strategy
    override suspend fun prepareContext(history: MutableList<Message>) {
        val before = history.size
        innerStrategy.prepareContext(history)
        val after = history.size
        Log.d(TAG, "L1 prepareContext [${innerStrategy.type.name}]: $before → $after msgs")
    }

    // Build: [long-term system] + [working system] + inner strategy result
    // When inner = StickyFacts: prefix + [facts system msg] + history
    // When inner = Sliding/Noop: prefix + history
    override fun buildMessages(history: List<Message>): List<Message> {
        val prefix = buildList {
            _longTermMemory?.let {
                add(Message(role = "system", content = "=== ДОЛГОВРЕМЕННАЯ ПАМЯТЬ (профиль и знания пользователя) ===\n$it"))
            }
            _workingMemory?.let {
                add(Message(role = "system", content = "=== РАБОЧАЯ ПАМЯТЬ (текущая сессия) ===\n$it"))
            }
        }
        val innerResult = innerStrategy.buildMessages(history)
        val total = prefix + innerResult
        Log.d(TAG, "L3=${if (_longTermMemory != null) "✓(${_longTermMemory!!.length}ch)" else "✗"} " +
                   "L2=${if (_workingMemory  != null) "✓(${_workingMemory!!.length}ch)"  else "✗"} " +
                   "L1=[${innerStrategy.type.name}] ${history.size} msgs → total ${total.size} msgs to LLM")
        return total
    }

    // After each turn: Layer 1 post-processing first, then Layer 2 working memory extraction
    override suspend fun afterTurn(history: List<Message>) {
        Log.d(TAG, "afterTurn: history=${history.size} msgs, inner=${innerStrategy.type.name}")
        innerStrategy.afterTurn(history)

        val lastUser      = history.lastOrNull { it.role == "user" }
        val lastAssistant = history.lastOrNull { it.role == "assistant" }
        val userWords      = lastUser?.content?.split(Regex("\\s+"))?.count { it.isNotEmpty() } ?: 0
        val assistantWords = lastAssistant?.content?.split(Regex("\\s+"))?.count { it.isNotEmpty() } ?: 0
        val totalWords = userWords + assistantWords

        if (!isTurnSignificant(history)) {
            Log.d(TAG, "L2 skipped: turn not significant ($totalWords words < $minTurnWords threshold)")
            return
        }
        Log.d(TAG, "L2 extracting working memory ($totalWords words, ${history.takeLast(shortTermWindow).size} msgs fed to LLM)")
        val updated = extractWorkingMemory(history.takeLast(shortTermWindow))
        if (updated != null) {
            _workingMemory = updated
            onAuxDataUpdated?.invoke(updated)
            Log.d(TAG, "L2 updated: ${updated.length} chars")
        } else {
            Log.w(TAG, "L2 extraction returned null (LLM call failed?)")
        }
    }

    /**
     * Returns true only when the last turn contains enough content to justify an LLM extraction call.
     *
     * Skips when:
     * - Fewer than 2 user/assistant messages with content exist (too early to extract context).
     * - The last assistant message has no text (tool-call-only turn, nothing to extract).
     * - The combined word count of the last user + last assistant messages is below
     *   [MIN_TURN_WORDS] (trivial exchange: greetings, one-word answers, acknowledgments).
     */
    private fun isTurnSignificant(history: List<Message>): Boolean {
        val contentMessages = history.filter {
            (it.role == "user" || it.role == "assistant") && !it.content.isNullOrBlank()
        }
        if (contentMessages.size < 2) return false

        val lastAssistant = history.lastOrNull { it.role == "assistant" }
        if (lastAssistant?.content.isNullOrBlank()) return false

        val lastUser = history.lastOrNull { it.role == "user" }
        val userWords      = lastUser?.content?.split(Regex("\\s+"))?.count { it.isNotEmpty() } ?: 0
        val assistantWords = lastAssistant.content!!.split(Regex("\\s+")).count { it.isNotEmpty() }
        return (userWords + assistantWords) >= minTurnWords
    }

    // Reset clears Layer 1 inner strategy and working memory; long-term survives
    override fun reset() {
        Log.d(TAG, "reset: clearing L1 + L2 (L3 survives)")
        innerStrategy.reset()
        _workingMemory = null
        onAuxDataUpdated?.invoke(null)
    }

    // Called externally to extract long-term facts
    suspend fun extractLongTermFacts(history: List<Message>): List<LongTermMemoryEntry>? {
        Log.d(TAG, "L3 extracting from ${history.size} msgs")
        return try {
            val prompt = buildLongTermPrompt(history)
            val request = LlmRequest(
                model       = providerConfig.backgroundModel,
                messages    = listOf(Message(role = "user", content = prompt)),
                maxTokens   = 600,
                temperature = 0.2
            )
            val response = llmService.chat(request).content ?: return null
            val facts = parseLongTermFacts(response)
            Log.d(TAG, "L3 extracted ${facts.size} facts: ${facts.map { it.keyName }}")
            facts
        } catch (e: Exception) {
            Log.e(TAG, "L3 extraction failed: ${e.localizedMessage}")
            null
        }
    }

    private suspend fun extractWorkingMemory(messages: List<Message>): String? {
        return try {
            val request = LlmRequest(
                model       = providerConfig.backgroundModel,
                messages    = listOf(Message(role = "user", content = buildWorkingPrompt(messages))),
                maxTokens   = 300,
                temperature = 0.2
            )
            llmService.chat(request).content
        } catch (e: Exception) {
            Log.e(TAG, "L2 extraction failed: ${e.localizedMessage}")
            null
        }
    }

    private fun buildWorkingPrompt(messages: List<Message>): String {
        val sb = StringBuilder()
        val existing = _workingMemory
        if (existing != null) {
            sb.append("Текущая рабочая память:\n$existing\n\nОбнови её на основе новых сообщений. ")
        }
        sb.append("Опиши кратко (до 150 слов):\n1. Текущая задача/цель пользователя\n2. Активные сущности (имена, темы, данные)\n3. Контекст для следующего ответа\n\nСообщения:\n")
        messages.forEach { msg ->
            val label = when (msg.role) { "user" -> "Пользователь"; "assistant" -> "Ассистент"; else -> return@forEach }
            sb.append("$label: ${msg.content?.take(300)}\n")
        }
        return sb.toString()
    }

    private fun buildLongTermPrompt(history: List<Message>): String {
        val sb = StringBuilder()
        sb.append("Проанализируй диалог и выдели факты для долговременного хранения (профиль пользователя, важные предпочтения, ключевые решения).\n")
        sb.append("Верни список в формате JSON-массива: [{\"category\":\"profile|knowledge|decision\",\"key\":\"название\",\"value\":\"содержание\"}]\n")
        sb.append("Если фактов нет — верни []\n\nДиалог:\n")
        history.takeLast(20).forEach { msg ->
            val label = when (msg.role) { "user" -> "Пользователь"; "assistant" -> "Ассистент"; else -> return@forEach }
            sb.append("$label: ${msg.content?.take(400)}\n")
        }
        return sb.toString()
    }

    companion object {
        /** Single Logcat tag for all 3-layer memory events. Filter: tag:MemoryLayers */
        const val TAG = "MemoryLayers"

        /** Minimum combined word count of last user + assistant messages to trigger extraction. */
        const val MIN_TURN_WORDS = 12
    }

    private data class LtmJsonEntry(val category: String?, val key: String?, val value: String?)

    private fun parseLongTermFacts(json: String): List<LongTermMemoryEntry> {
        val cleaned = json.trim().let {
            val start = it.indexOf('['); val end = it.lastIndexOf(']')
            if (start >= 0 && end > start) it.substring(start, end + 1) else "[]"
        }
        return try {
            val type = object : TypeToken<List<LtmJsonEntry>>() {}.type
            gson.fromJson<List<LtmJsonEntry>>(cleaned, type)
                .orEmpty()
                .mapNotNull { entry ->
                    val key   = entry.key?.takeIf   { it.isNotBlank() } ?: return@mapNotNull null
                    val value = entry.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val cat   = entry.category?.ifBlank { "knowledge" } ?: "knowledge"
                    val now   = System.currentTimeMillis()
                    LongTermMemoryEntry(category = cat, keyName = key, value = value, createdAt = now, updatedAt = now)
                }
        } catch (e: Exception) {
            Log.e(TAG, "L3 parse failed: ${e.localizedMessage}")
            emptyList()
        }
    }
}
