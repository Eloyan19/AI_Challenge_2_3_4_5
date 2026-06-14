package com.example.petapp.domain.usecase

import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.repository.ChatRepository

class SaveTurnUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(messages: List<ChatMessage>) = repository.saveMessages(messages)
}
