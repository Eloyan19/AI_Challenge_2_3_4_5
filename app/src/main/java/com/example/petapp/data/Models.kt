package com.example.petapp.data

import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.Tool
import com.google.gson.annotations.SerializedName

/**
 * Wire-format types for the DeepSeek/OpenAI Chat Completions API.
 *
 * Domain types ([Message], [Tool], [ToolCall], etc.) live in [com.example.petapp.domain.model]
 * and are reused here directly — the OpenAI message format is both the wire format and
 * the domain representation for this app.
 *
 * [ChatRequest] is assembled by [DeepSeekLlmService] from a [com.example.petapp.domain.model.LlmRequest].
 * [ChatResponse] is parsed by Gson and mapped back to [com.example.petapp.domain.model.LlmResponse].
 */

/**
 * Request body sent to the Chat Completions endpoint.
 *
 * @property thinking DeepSeek-specific: enables extended reasoning mode.
 * @property reasoningEffort DeepSeek-specific: budget hint for thinking ("low", "medium", "high").
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

/** DeepSeek-specific thinking mode configuration. */
data class Thinking(val type: String)

/** Top-level response from the Chat Completions endpoint. */
data class ChatResponse(
    val choices: List<Choice>?,
    val usage: Usage? = null
)

/** One completion candidate. */
data class Choice(
    val message: Message?,
    @SerializedName("finish_reason") val finishReason: String? = null
)

/** Token usage statistics for a single API call. */
data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0,
    @SerializedName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null
)

/** Cache breakdown within prompt tokens. */
data class PromptTokensDetails(
    @SerializedName("cached_tokens") val cachedTokens: Int = 0
)
