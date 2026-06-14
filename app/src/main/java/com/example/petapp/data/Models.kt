package com.example.petapp.data

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

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

data class Thinking(val type: String)

data class Message(
    val role: String,
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

data class Tool(
    val type: String = "function",
    val function: ToolFunction
)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

data class ToolCall(
    val id: String,
    val type: String,
    val function: ToolCallFunction
)

data class ToolCallFunction(
    val name: String,
    val arguments: String
)

data class ChatResponse(
    val choices: List<Choice>?,
    val usage: Usage? = null
)

data class Choice(
    val message: Message?,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0,
    @SerializedName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null
)

data class PromptTokensDetails(
    @SerializedName("cached_tokens") val cachedTokens: Int = 0
)
