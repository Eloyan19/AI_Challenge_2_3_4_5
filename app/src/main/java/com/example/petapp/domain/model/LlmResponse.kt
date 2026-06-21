package com.example.petapp.domain.model

/**
 * Provider-agnostic LLM chat response.
 *
 * @property content Response text; null when the model only requested tool calls.
 * @property toolCalls Tool invocations requested by the model; present when finishReason = "tool_calls".
 * @property finishReason Why generation stopped: "stop", "tool_calls", "length", etc.
 * @property usage Token consumption stats; null if the provider did not return them.
 */
data class LlmResponse(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val finishReason: String?,
    val usage: LlmUsage?
)

/**
 * Token usage for a single LLM call.
 *
 * @property cachedTokens Prompt tokens served from the provider's KV-cache (cost discount).
 */
data class LlmUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cachedTokens: Int
)
