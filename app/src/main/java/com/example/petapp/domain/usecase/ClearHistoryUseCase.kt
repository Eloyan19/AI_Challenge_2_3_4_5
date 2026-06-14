package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

class ClearHistoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.clearAll()
}
