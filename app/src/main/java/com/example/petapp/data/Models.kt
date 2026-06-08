package com.example.petapp.data

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val temperature: Double? = null,
    val thinking: Thinking? = null,
    @SerializedName("reasoning_effort")
    val reasoningEffort: String? = null
)

data class Thinking(
    val type: String
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>?,
    val usage: Usage? = null
)

data class Choice(
    val message: Message?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,
    @SerializedName("total_tokens")
    val totalTokens: Int = 0,
    @SerializedName("prompt_tokens_details")
    val promptTokensDetails: PromptTokensDetails? = null
)

data class PromptTokensDetails(
    @SerializedName("cached_tokens")
    val cachedTokens: Int = 0
)