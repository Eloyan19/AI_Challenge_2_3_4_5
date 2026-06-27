package com.example.petapp.data

import android.util.Log
import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.Tool
import com.example.petapp.domain.strategy.ContextStrategy
import com.example.petapp.domain.strategy.NoopStrategy
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core AI agent that manages a stateful conversation via a provider-agnostic [LlmService].
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
 * @param llmService Provider-agnostic LLM service (DeepSeek by default, swappable via DI).
 * @param providerConfig Model list, context limit, and per-model pricing for cost calculation.
 * @param toolRegistry Aggregates local and MCP tools; routes tool call execution.
 */
@Singleton
class SimpleAgent @Inject constructor(
    private val llmService: LlmService,
    private val providerConfig: LlmProviderConfig,
    private val toolRegistry: ToolRegistry
) {

    /**
     * Runtime configuration applied to every API request until [updateConfig] is called.
     *
     * @property model Model identifier (must be in [LlmProviderConfig.availableModels]).
     * @property maxTokens Hard cap on output tokens; null uses the model default.
     * @property temperature Sampling temperature; ignored when [thinkingEnabled] is true.
     * @property thinkingEnabled Activates extended reasoning mode.
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
         */
        data class Failure(val error: String, val isContextOverflow: Boolean = false) : AgentResult()
    }

    private var config: AgentConfig = AgentConfig(model = providerConfig.defaultModel)
    private val history = mutableListOf<Message>()

    /** Active context management strategy; replace between turns to change behaviour. */
    var strategy: ContextStrategy = NoopStrategy()

    /** Absolute behavioral constraints loaded from assets/guardrails.json; always the first system message. */
    var guardrailsInstruction: String? = null

    /** Instructions from the active user profile; injected as second system message in every request. */
    var systemProfileInstructions: String? = null

    /** Called with a UI-friendly status string whenever a tool invocation begins. */
    var onToolCall: ((String) -> Unit)? = null

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5
    }

    /** Replaces the current runtime configuration. Safe to call between turns. */
    fun updateConfig(newConfig: AgentConfig) { config = newConfig }

    /** Returns a snapshot of the current history. Safe to call under [agentMutex]. */
    fun historySnapshot(): List<Message> = history.toList()

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
     * Appends [messages] to history without an LLM call.
     * Used by the task orchestrator to persist a synthesized result directly into history
     * after the agent swarm completes, avoiding a redundant API round-trip.
     */
    fun appendMessages(messages: List<Message>) {
        history.addAll(messages)
    }

    /**
     * Runs one user turn: appends [userInput] to history, trims via [strategy], then
     * enters the tool-call loop until the model produces a final text response.
     *
     * On failure the full history is restored from a pre-turn snapshot so the conversation
     * state remains consistent with what is shown in the UI. A simple index-based rollback
     * would not work when [strategy] removes messages during [ContextStrategy.prepareContext]
     * (e.g. SlidingWindow), because history.size after trimming can be less than the original
     * turnStartIndex, causing the rollback loop to never execute.
     */
    suspend fun run(userInput: String): AgentResult {
        val historyBefore = history.toList()
        history.add(Message(role = "user", content = userInput))
        strategy.prepareContext(history)

        Log.d("SimpleAgent", "run() — ${history.size} msgs, strategy=${strategy.type}")

        return try {
            agentLoop(historyBefore.size)
        } catch (e: HttpException) {
            history.clear(); history.addAll(historyBefore)
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val isOverflow = body != null && (
                body.contains("context_length_exceeded", ignoreCase = true) ||
                body.contains("maximum context length", ignoreCase = true)
            )
            AgentResult.Failure("Ошибка API (${e.code()}): ${e.message()}", isOverflow)
        } catch (e: Exception) {
            history.clear(); history.addAll(historyBefore)
            AgentResult.Failure("Ошибка: ${e.localizedMessage}")
        }
    }

    /**
     * Inner tool-call loop: sends the current history to the LLM and repeats up to
     * [MAX_TOOL_ITERATIONS] times when the model requests tool calls.
     *
     * Tools are fetched once before the loop to avoid redundant [ToolRegistry.allTools] calls
     * (which include a network round-trip to the MCP server on cache miss) on every iteration.
     */
    private suspend fun agentLoop(turnStartIndex: Int): AgentResult {
        val startTime = System.currentTimeMillis()
        val tools = if (!config.thinkingEnabled) toolRegistry.allTools() else null

        repeat(MAX_TOOL_ITERATIONS) {
            val response = llmService.chat(buildLlmRequest(tools))

            if (response.finishReason == "tool_calls") {
                val toolCalls = response.toolCalls
                    ?: return AgentResult.Failure("Нет tool_calls в ответе")
                if (toolCalls.isEmpty()) return AgentResult.Failure("Пустой список tool_calls")

                history.add(Message(role = "assistant", content = response.content, toolCalls = toolCalls))
                for (toolCall in toolCalls) {
                    val toolName = toolCall.function.name
                    Log.d("SimpleAgent", "Tool: $toolName args=${toolCall.function.arguments}")
                    onToolCall?.invoke(toolDisplayName(toolName))
                    val result = toolRegistry.execute(toolCall)
                    history.add(Message(role = "tool", content = result, toolCallId = toolCall.id, name = toolName))
                }
            } else {
                val content = response.content ?: "Ответ пуст"
                history.add(Message(role = "assistant", content = content))

                val durationSec = (System.currentTimeMillis() - startTime) / 1000.0
                val tokenInfo = response.usage?.let { u ->
                    TokenInfo(u.promptTokens, u.completionTokens, u.totalTokens, u.cachedTokens)
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

    /** Builds the [LlmRequest] from current [config], [history], [strategy], and the pre-fetched [tools] list. */
    private fun buildLlmRequest(tools: List<Tool>?): LlmRequest {
        val strategyMessages = strategy.buildMessages(history)
        val messages = buildList {
            guardrailsInstruction?.let { add(Message(role = "system", content = it)) }
            systemProfileInstructions?.let { add(Message(role = "system", content = "=== ИНСТРУКЦИИ ПРОФИЛЯ ===\n$it")) }
            addAll(strategyMessages)
        }
        Log.d("SimpleAgent", "buildLlmRequest: ${tools?.size ?: 0} tools available: ${tools?.map { it.function.name }}")
        return LlmRequest(
            model           = config.model,
            messages        = messages,
            maxTokens       = config.maxTokens,
            temperature     = config.temperature,
            tools           = tools,
            toolChoice      = if (tools != null) "auto" else null,
            thinkingEnabled = config.thinkingEnabled,
            reasoningEffort = config.reasoningEffort
        )
    }

    private fun toolDisplayName(name: String) = when (name) {
        "get_weather"      -> "Запрашиваю погоду..."
        "convert_currency" -> "Конвертирую валюту..."
        "web_search"       -> "Ищу в интернете..."
        else               -> "Вызываю инструмент..."
    }

    /**
     * Estimates USD cost for a turn using per-model pricing from [providerConfig].
     * Cache-hit tokens are billed at the discounted cached rate.
     */
    private fun calculateCost(tokens: TokenInfo): Double {
        val pricing = providerConfig.pricingFor(config.model) ?: return 0.0
        val uncached = tokens.promptTokens - tokens.cachedTokens
        return (tokens.cachedTokens / 1_000_000.0) * pricing.costPerMInputCached +
               (uncached / 1_000_000.0) * pricing.costPerMInputUncached +
               (tokens.completionTokens / 1_000_000.0) * pricing.costPerMOutput
    }

    /** Clears the conversation history and resets the active strategy's auxiliary state. */
    fun reset() {
        history.clear()
        strategy.reset()
    }
}
