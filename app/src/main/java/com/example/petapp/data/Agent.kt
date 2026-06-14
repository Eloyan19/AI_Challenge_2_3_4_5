package com.example.petapp.data

import android.util.Log

class SimpleAgent(
    private val apiService: DeepSeekApiService,
    initialConfig: AgentConfig = AgentConfig()
) {
    data class AgentConfig(
        val model: String = "deepseek-v4-flash",
        val maxTokens: Int? = null,
        val temperature: Double? = null,
        val thinkingEnabled: Boolean = false,
        val reasoningEffort: String? = null
    )

    data class TokenInfo(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val cachedTokens: Int
    )

    sealed class AgentResult {
        data class Success(
            val response: String,
            val tokenInfo: TokenInfo?,
            val cost: Double?,
            val durationSec: Double
        ) : AgentResult()
        data class Failure(val error: String) : AgentResult()
    }

    private var config: AgentConfig = initialConfig
    private val history = mutableListOf<Message>()

    fun updateConfig(newConfig: AgentConfig) {
        config = newConfig
    }

    suspend fun run(userInput: String): AgentResult {
        history.add(Message("user", userInput))
        Log.d("SimpleAgent", "run() — ${history.size} messages in context")

        return try {
            step()
        } catch (e: Exception) {
            history.removeLastOrNull()
            AgentResult.Failure("Ошибка: ${e.localizedMessage}")
        }
    }

    private suspend fun step(): AgentResult {
        val startTime = System.currentTimeMillis()
        val response = apiService.getChatCompletion(buildRequest())
        val durationSec = (System.currentTimeMillis() - startTime) / 1000.0

        val content = response.choices?.firstOrNull()?.message?.content ?: "Ответ пуст"
        history.add(Message("assistant", content))

        val tokenInfo = response.usage?.let { u ->
            TokenInfo(
                promptTokens = u.promptTokens,
                completionTokens = u.completionTokens,
                totalTokens = u.totalTokens,
                cachedTokens = u.promptTokensDetails?.cachedTokens ?: 0
            )
        }

        return AgentResult.Success(
            response = content,
            tokenInfo = tokenInfo,
            cost = tokenInfo?.let { calculateCost(it) },
            durationSec = durationSec
        )
    }

    private fun buildRequest() = ChatRequest(
        model = config.model,
        messages = history.toList(),
        maxTokens = config.maxTokens,
        temperature = if (config.thinkingEnabled) null else config.temperature,
        thinking = if (config.thinkingEnabled) Thinking("enabled") else null,
        reasoningEffort = if (config.thinkingEnabled && !config.reasoningEffort.isNullOrBlank())
            config.reasoningEffort else null
    )

    private fun calculateCost(tokens: TokenInfo): Double {
        val (cacheHit, cacheMiss, output) = when (config.model) {
            "deepseek-v4-flash" -> Triple(0.0028, 0.14, 0.28)
            "deepseek-v4-pro"   -> Triple(0.003625, 0.435, 0.87)
            else                -> return 0.0
        }
        val uncached = tokens.promptTokens - tokens.cachedTokens
        return (tokens.cachedTokens / 1_000_000.0) * cacheHit +
               (uncached / 1_000_000.0) * cacheMiss +
               (tokens.completionTokens / 1_000_000.0) * output
    }

    fun reset() = history.clear()
}
