package com.example.petapp.domain.usecase

import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.repository.ChatRepository

class LoadHistoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): List<ChatMessage> = repository.getAllMessages()
}
