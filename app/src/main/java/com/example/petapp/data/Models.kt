package com.example.petapp.data

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val temperature: Double? = null
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: Message?
)