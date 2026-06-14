package com.example.petapp.data

import android.util.Log
import com.example.petapp.BuildConfig
import com.example.petapp.domain.strategy.ContextStrategy
import com.example.petapp.domain.strategy.NoopStrategy

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
            val turnMessages: List<Message>,
            val tokenInfo: TokenInfo?,
            val cost: Double?,
            val durationSec: Double
        ) : AgentResult()
        data class Failure(val error: String) : AgentResult()
    }

    private var config: AgentConfig = initialConfig
    val history = mutableListOf<Message>()
    private val toolExecutor = ToolExecutor(BuildConfig.YANDEX_SEARCH_USER, BuildConfig.YANDEX_SEARCH_KEY)

    var strategy: ContextStrategy = NoopStrategy()
    var onToolCall: ((String) -> Unit)? = null

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5
    }

    fun updateConfig(newConfig: AgentConfig) { config = newConfig }

    fun loadHistory(messages: List<Message>) {
        history.clear()
        history.addAll(messages)
        Log.d("SimpleAgent", "History loaded: ${history.size} messages")
    }

    suspend fun run(userInput: String): AgentResult {
        history.add(Message(role = "user", content = userInput))
        strategy.prepareContext(history)

        // After possible in-place trimming, the user message is always last
        val turnStartIndex = history.lastIndex
        Log.d("SimpleAgent", "run() — ${history.size} msgs, strategy=${strategy.type}")

        return try {
            val result = agentLoop(turnStartIndex)
            if (result is AgentResult.Success) {
                strategy.afterTurn(history)
            }
            result
        } catch (e: Exception) {
            while (history.size > turnStartIndex) history.removeLastOrNull()
            AgentResult.Failure("Ошибка: ${e.localizedMessage}")
        }
    }

    private suspend fun agentLoop(turnStartIndex: Int): AgentResult {
        val startTime = System.currentTimeMillis()

        repeat(MAX_TOOL_ITERATIONS) {
            val response = apiService.getChatCompletion(buildRequest())
            val choice = response.choices?.firstOrNull()

            if (choice?.finishReason == "tool_calls") {
                val message   = choice.message  ?: return AgentResult.Failure("Пустой ответ с tool_calls")
                val toolCalls = message.toolCalls ?: return AgentResult.Failure("Нет tool_calls в сообщении")

                history.add(message)
                for (toolCall in toolCalls) {
                    val toolName = toolCall.function.name
                    Log.d("SimpleAgent", "Tool: $toolName args=${toolCall.function.arguments}")
                    onToolCall?.invoke(toolDisplayName(toolName))
                    val result = toolExecutor.execute(toolCall)
                    history.add(Message(role = "tool", content = result, toolCallId = toolCall.id, name = toolName))
                }
            } else {
                val content = choice?.message?.content ?: "Ответ пуст"
                history.add(Message(role = "assistant", content = content))

                val durationSec = (System.currentTimeMillis() - startTime) / 1000.0
                val tokenInfo   = response.usage?.let { u ->
                    TokenInfo(u.promptTokens, u.completionTokens, u.totalTokens,
                        u.promptTokensDetails?.cachedTokens ?: 0)
                }

                return AgentResult.Success(
                    response     = content,
                    turnMessages = history.subList(turnStartIndex, history.size).toList(),
                    tokenInfo    = tokenInfo,
                    cost         = tokenInfo?.let { calculateCost(it) },
                    durationSec  = durationSec
                )
            }
        }

        return AgentResult.Failure("Превышен лимит итераций ($MAX_TOOL_ITERATIONS)")
    }

    private fun buildRequest() = ChatRequest(
        model    = config.model,
        messages = strategy.buildMessages(history),
        maxTokens   = config.maxTokens,
        temperature = if (config.thinkingEnabled) null else config.temperature,
        thinking    = if (config.thinkingEnabled) Thinking("enabled") else null,
        reasoningEffort = if (config.thinkingEnabled && !config.reasoningEffort.isNullOrBlank())
            config.reasoningEffort else null,
        tools      = if (!config.thinkingEnabled) ToolDefinitions.allTools else null,
        toolChoice = if (!config.thinkingEnabled) "auto" else null
    )

    private fun toolDisplayName(name: String) = when (name) {
        "get_weather"      -> "Запрашиваю погоду..."
        "convert_currency" -> "Конвертирую валюту..."
        "web_search"       -> "Ищу в интернете..."
        else               -> "Вызываю инструмент..."
    }

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

    fun reset() {
        history.clear()
        strategy.reset()
    }
}
