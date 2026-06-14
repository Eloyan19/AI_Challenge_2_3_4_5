package com.example.petapp.domain.model

data class ChatMessage(
    val id: Long = 0,
    val branchId: Long = 1L,
    val turnId: Long,
    val role: String,
    val messageJson: String,
    val displayText: String?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val cachedTokens: Int?,
    val cost: Double?,
    val durationSec: Double?,
    val timestamp: Long
)
