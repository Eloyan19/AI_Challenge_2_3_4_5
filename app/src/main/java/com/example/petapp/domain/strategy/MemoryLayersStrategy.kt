package com.example.petapp.domain.strategy

import android.util.Log
import com.example.petapp.data.ChatRequest
import com.example.petapp.data.DeepSeekApiService
import com.example.petapp.data.Message
import com.example.petapp.domain.model.LongTermMemoryEntry
import com.example.petapp.domain.model.StrategyType

/**
 * 3-layer memory strategy:
 * - Layer 1 (Short-term): last [shortTermWindow] messages in the live window (in-memory)
 * - Layer 2 (Working): auto-extracted task context after each turn (persisted via [onAuxDataUpdated])
 * - Layer 3 (Long-term): persistent facts across sessions (injected from DB, read-only here)
 */
class MemoryLayersStrategy(
    private val apiService: DeepSeekApiService,
    var shortTermWindow: Int = 8
) : ContextStrategy {

    override val type = StrategyType.MEMORY_LAYERS

    // Layer 2: Working memory
    private var _workingMemory: String? = null
    var workingMemory: String?
        get() = _workingMemory
        set(v) { _workingMemory = v }

    // Layer 3: Long-term memory (injected externally, read-only in strategy)
    var longTermMemory: String? = null

    override val auxData: String? get() = _workingMemory
    override var onAuxDataUpdated: ((String?) -> Unit)? = null

    // Layer 1: Short-term — trim to window
    override suspend fun prepareContext(history: MutableList<Message>) {
        while (history.size > shortTermWindow) history.removeAt(0)
    }

    // Build: [long-term system] + [working system] + [short-term messages]
    override fun buildMessages(history: List<Message>): List<Message> {
        val prefix = mutableListOf<Message>()
        longTermMemory?.let {
            prefix.add(Message(role = "system", content = "=== ДОЛГОВРЕМЕННАЯ ПАМЯТЬ (профиль и знания пользователя) ===\n$it"))
        }
        _workingMemory?.let {
            prefix.add(Message(role = "system", content = "=== РАБОЧАЯ ПАМЯТЬ (текущая сессия) ===\n$it"))
        }
        return prefix + history
    }

    // After each turn: update working memory via LLM
    override suspend fun afterTurn(history: List<Message>) {
        val updated = extractWorkingMemory(history.takeLast(shortTermWindow * 2))
        if (updated != null) {
            _workingMemory = updated
            onAuxDataUpdated?.invoke(updated)
            Log.d("MemoryLayersStrategy", "Working memory updated (${updated.length} chars)")
        }
    }

    // Reset clears working memory only (long-term survives)
    override fun reset() {
        _workingMemory = null
        onAuxDataUpdated?.invoke(null)
    }

    // Called externally to extract long-term facts
    suspend fun extractLongTermFacts(history: List<Message>): List<LongTermMemoryEntry>? {
        return try {
            val prompt = buildLongTermPrompt(history)
            val request = ChatRequest(
                model = "deepseek-v4-flash",
                messages = listOf(Message(role = "user", content = prompt)),
                maxTokens = 600,
                temperature = 0.2
            )
            val response = apiService.getChatCompletion(request)
                .choices?.firstOrNull()?.message?.content ?: return null
            parseLongTermFacts(response)
        } catch (e: Exception) {
            Log.e("MemoryLayersStrategy", "Long-term extraction failed: ${e.localizedMessage}")
            null
        }
    }

    private suspend fun extractWorkingMemory(messages: List<Message>): String? {
        return try {
            val request = ChatRequest(
                model = "deepseek-v4-flash",
                messages = listOf(Message(role = "user", content = buildWorkingPrompt(messages))),
                maxTokens = 300,
                temperature = 0.2
            )
            apiService.getChatCompletion(request).choices?.firstOrNull()?.message?.content
        } catch (e: Exception) {
            Log.e("MemoryLayersStrategy", "Working memory extraction failed: ${e.localizedMessage}")
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

    private fun parseLongTermFacts(json: String): List<LongTermMemoryEntry> {
        return try {
            val cleaned = json.trim().let {
                val start = it.indexOf('[')
                val end = it.lastIndexOf(']')
                if (start >= 0 && end > start) it.substring(start, end + 1) else "[]"
            }
            val entries = mutableListOf<LongTermMemoryEntry>()
            val objectPattern = Regex("""\{[^}]+\}""")
            objectPattern.findAll(cleaned).forEach { match ->
                val obj = match.value
                val category = Regex(""""category"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: return@forEach
                val key = Regex(""""key"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: return@forEach
                val value = Regex(""""value"\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1) ?: return@forEach
                if (key.isNotBlank() && value.isNotBlank()) {
                    entries.add(LongTermMemoryEntry(category = category, keyName = key, value = value, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                }
            }
            entries
        } catch (e: Exception) {
            Log.e("MemoryLayersStrategy", "Failed to parse long-term facts: ${e.localizedMessage}")
            emptyList()
        }
    }
}
