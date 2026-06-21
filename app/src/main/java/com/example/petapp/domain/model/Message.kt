package com.example.petapp.domain.model

import com.google.gson.annotations.SerializedName

/**
 * A single conversation message. The canonical representation of dialogue entries —
 * used by all context strategies and persisted to the database as JSON.
 *
 * The structure follows the OpenAI Chat Completions format, which is a de-facto standard
 * supported by DeepSeek, Groq, Mistral, Together, Ollama, and most other providers.
 *
 * @property role One of "system", "user", "assistant", "tool".
 * @property content Text content; null for assistant messages that only contain tool calls.
 * @property toolCalls Tool calls requested by the assistant.
 * @property toolCallId Correlates a tool result back to the originating call.
 * @property name Tool name; present when role = "tool".
 */
data class Message(
    val role: String,
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)
