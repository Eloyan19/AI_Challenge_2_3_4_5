package com.example.petapp.domain.repository

import com.example.petapp.domain.model.ChatMessage

interface ChatRepository {
    suspend fun getAllMessages(): List<ChatMessage>
    suspend fun saveMessages(messages: List<ChatMessage>)
    suspend fun clearAll()
}
