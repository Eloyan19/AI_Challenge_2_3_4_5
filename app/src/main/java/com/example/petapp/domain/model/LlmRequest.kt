package com.example.petapp.domain.model

/**
 * Provider-agnostic LLM chat request.
 *
 * Fields map to the OpenAI Chat Completions format understood by most providers.
 * Provider-specific capabilities (e.g. thinking mode) are passed as optional flags
 * and ignored by implementations that don't support them.
 *
 * @property thinkingEnabled Activates extended reasoning if the provider supports it.
 * @property reasoningEffort Budget hint for thinking mode ("low", "medium", "high").
 */
data class LlmRequest(
    val messages: List<Message>,
    val model: String,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val tools: List<Tool>? = null,
    val toolChoice: String? = null,
    val thinkingEnabled: Boolean = false,
    val reasoningEffort: String? = null
)
