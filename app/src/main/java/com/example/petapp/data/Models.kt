package com.example.petapp.data

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Request body sent to the DeepSeek Chat Completions API.
 *
 * @property model Model identifier (e.g. `"deepseek-v4-flash"`, `"deepseek-v4-pro"`).
 * @property messages Conversation history including system, user, assistant, and tool messages.
 * @property maxTokens Hard cap on generated tokens; null means the model's default.
 * @property stop Optional stop sequences that terminate generation.
 * @property temperature Sampling temperature; null when thinking mode is active (not supported together).
 * @property thinking Enables extended reasoning ("thinking") mode when [type] = `"enabled"`.
 * @property reasoningEffort Reasoning budget hint (`"low"`, `"medium"`, `"high"`) for thinking mode.
 * @property tools Available tool definitions; omitted when thinking mode is active.
 * @property toolChoice How the model selects tools (`"auto"`, `"none"`, or a specific function).
 */
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val temperature: Double? = null,
    val thinking: Thinking? = null,
    @SerializedName("reasoning_effort") val reasoningEffort: String? = null,
    val tools: List<Tool>? = null,
    @SerializedName("tool_choice") val toolChoice: String? = null
)

/**
 * Thinking mode configuration for extended reasoning.
 *
 * @property type Must be `"enabled"` to activate thinking mode.
 */
data class Thinking(val type: String)

/**
 * A single conversation message exchanged with the model.
 *
 * @property role One of `"system"`, `"user"`, `"assistant"`, `"tool"`.
 * @property content Text content; null for assistant messages that only contain tool calls.
 * @property toolCalls Tool calls requested by the assistant; present only when [role] = `"assistant"`.
 * @property toolCallId Id that correlates a tool result back to the originating call; present when [role] = `"tool"`.
 * @property name Tool name; present when [role] = `"tool"`.
 */
data class Message(
    val role: String,
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

/**
 * A function tool made available to the model.
 *
 * @property type Always `"function"` per the DeepSeek API specification.
 * @property function Metadata and JSON-Schema parameters for the tool.
 */
data class Tool(
    val type: String = "function",
    val function: ToolFunction
)

/**
 * Metadata for a single tool function.
 *
 * @property name Snake-case identifier used in tool call responses (e.g. `"get_weather"`).
 * @property description Human-readable description that guides model selection.
 * @property parameters JSON-Schema object describing the expected arguments.
 */
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/**
 * A tool invocation requested by the model in an assistant message.
 *
 * @property id Unique call id; must be echoed back in the tool result message.
 * @property type Always `"function"`.
 * @property function The function name and JSON-encoded arguments string.
 */
data class ToolCall(
    val id: String,
    val type: String,
    val function: ToolCallFunction
)

/**
 * Name and arguments of a specific tool call.
 *
 * @property name The tool function name.
 * @property arguments JSON string containing the argument values; parsed by [com.example.petapp.data.ToolExecutor].
 */
data class ToolCallFunction(
    val name: String,
    val arguments: String
)

/**
 * Top-level response from the DeepSeek Chat Completions API.
 *
 * @property choices List of generated completions (typically one for temperature > 0).
 * @property usage Token consumption breakdown; null if the API omits it.
 */
data class ChatResponse(
    val choices: List<Choice>?,
    val usage: Usage? = null
)

/**
 * One completion candidate returned by the API.
 *
 * @property message The generated message, including any tool calls.
 * @property finishReason Why generation stopped: `"stop"`, `"tool_calls"`, `"length"`, etc.
 */
data class Choice(
    val message: Message?,
    @SerializedName("finish_reason") val finishReason: String? = null
)

/**
 * Token usage statistics for a single API call.
 *
 * @property promptTokens Total tokens in the input (context + user message).
 * @property completionTokens Tokens generated in the output.
 * @property totalTokens Sum of [promptTokens] and [completionTokens].
 * @property promptTokensDetails Breakdown of prompt tokens by cache status.
 */
data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0,
    @SerializedName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null
)

/**
 * Breakdown of prompt tokens by whether they were served from the KV-cache.
 *
 * @property cachedTokens Tokens that were cache hits; they cost less than uncached tokens.
 */
data class PromptTokensDetails(
    @SerializedName("cached_tokens") val cachedTokens: Int = 0
)
