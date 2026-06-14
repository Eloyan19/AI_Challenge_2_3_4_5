package com.example.petapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
