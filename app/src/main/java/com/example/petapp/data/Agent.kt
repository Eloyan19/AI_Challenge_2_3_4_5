package com.example.petapp.data

import android.util.Log
import com.example.petapp.domain.strategy.ContextStrategy
import com.example.petapp.domain.strategy.NoopStrategy
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core AI agent that manages a stateful conversation with the DeepSeek API.
 *
 * The agent maintains its own message [history] and delegates context management to a
 * pluggable [strategy]. A tool-call loop runs up to [MAX_TOOL_ITERATIONS] rounds per turn:
 * if the model requests tool calls the agent executes them via [ToolExecutor] and feeds the
 * results back before asking for the final text response.
 *
 * **Thread safety:** [history] is private and all callers that mutate it must hold
 * [com.example.petapp.ui.MainViewModel.agentMutex] before invoking [run], [loadHistory],
 * or [reset].
 *
 * Provided as a `@Singleton` — shared across the entire app lifetime via Dagger.
 *
 * @param apiService Retrofit service for the DeepSeek Chat Completions endpoint.
 * @param toolExecutor Executor for weather, currency, and web-search tool calls.
 */
@Singleton
class SimpleAgent @Inject constructor(
    private val apiService: DeepSeekApiService,
    private val toolExecutor: ToolExecutor
) {

    /**
     * Runtime configuration applied to every API request until [updateConfig] is called.
     *
     * @property model DeepSeek model identifier.
     * @property maxTokens Hard cap on output tokens; null uses the model default.
     * @property temperature Sampling temperature; must be null when [thinkingEnabled] is true.
     * @property thinkingEnabled Activates extended reasoning ("thinking") mode.
     *   Tools are automatically disabled when this is true.
     * @property reasoningEffort Budget hint for thinking mode (`"low"`, `"medium"`, `"high"`).
     */
    data class AgentConfig(
        val model: String = "deepseek-v4-flash",
        val maxTokens: Int? = null,
        val temperature: Double? = null,
        val thinkingEnabled: Boolean = false,
        val reasoningEffort: String? = null
    )

    /**
     * Token usage for a single completed turn.
     *
     * @property promptTokens Total input tokens (context + user message).
     * @property completionTokens Generated output tokens.
     * @property totalTokens Sum of [promptTokens] and [completionTokens].
     * @property cachedTokens Prompt tokens served from the KV-cache (reduce cost).
     */
    data class TokenInfo(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val cachedTokens: Int
    )

    /** Result of a single [run] call. */
    sealed class AgentResult {
        /**
         * The turn completed successfully.
         *
         * @property response Final assistant text shown to the user.
         * @property turnMessages All messages added during this turn (user, tool calls/results, assistant).
         * @property tokenInfo Usage stats from the final API call; null if not returned by the API.
         * @property cost Estimated USD cost based on [tokenInfo] and the active model's pricing.
         * @property durationSec Wall-clock seconds from [run] entry to final response.
         */
        data class Success(
            val response: String,
            val turnMessages: List<Message>,
            val tokenInfo: TokenInfo?,
            val cost: Double?,
            val durationSec: Double
        ) : AgentResult()

        /**
         * The turn failed (network error, API error, or tool iteration limit).
         *
         * @property error Human-readable error message.
         * @property isContextOverflow True when the API returned HTTP 400 with
         *   `context_length_exceeded` or `maximum context length` in the body.
         *   The ViewModel uses this to show a specific overflow warning to the user.
         */
        data class Failure(val error: String, val isContextOverflow: Boolean = false) : AgentResult()
    }

    private var config: AgentConfig = AgentConfig()
    private val history = mutableListOf<Message>()

    /** Active context management strategy; replace between turns to change behaviour. */
    var strategy: ContextStrategy = NoopStrategy()

    /** Called with a UI-friendly status string whenever a tool invocation begins. */
    var onToolCall: ((String) -> Unit)? = null

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5
    }

    /** Replaces the current runtime configuration. Safe to call between turns. */
    fun updateConfig(newConfig: AgentConfig) { config = newConfig }

    /**
     * Replaces the agent's in-memory history with [messages].
     * Called during session restore or when switching branches.
     */
    fun loadHistory(messages: List<Message>) {
        history.clear()
        history.addAll(messages)
        Log.d("SimpleAgent", "History loaded: ${history.size} messages")
    }

    /**
     * Runs one user turn: appends [userInput] to history, trims via [strategy], then
     * enters the tool-call loop until the model produces a final text response.
     *
     * On failure the messages added during this turn are rolled back from [history] so the
     * conversation state remains consistent with what is shown in the UI.
     *
     * @return [AgentResult.Success] with the response text and turn metadata, or
     *   [AgentResult.Failure] with an error message and overflow flag.
     */
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
        } catch (e: HttpException) {
            while (history.size > turnStartIndex) history.removeLastOrNull()
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val isOverflow = body != null && (
                body.contains("context_length_exceeded", ignoreCase = true) ||
                body.contains("maximum context length", ignoreCase = true)
            )
            AgentResult.Failure("Ошибка API (${e.code()}): ${e.message()}", isOverflow)
        } catch (e: Exception) {
            while (history.size > turnStartIndex) history.removeLastOrNull()
            AgentResult.Failure("Ошибка: ${e.localizedMessage}")
        }
    }

    /**
     * Inner tool-call loop: sends the current history to the API and repeats up to
     * [MAX_TOOL_ITERATIONS] times when the model requests tool calls.
     * Returns [AgentResult.Success] on the first non-tool-call response.
     */
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

    /** Builds the [ChatRequest] from current [config], [history], and [strategy]. */
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

    /**
     * Calculates the estimated USD cost of a turn based on [tokens] and the model's per-million
     * token pricing. Cache-hit tokens are billed at a discounted rate.
     */
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

    /** Clears the conversation history and resets the active strategy's auxiliary state. */
    fun reset() {
        history.clear()
        strategy.reset()
    }
}
