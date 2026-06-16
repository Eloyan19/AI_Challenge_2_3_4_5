package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

class SaveWorkingMemoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(content: String) = repository.saveWorkingMemory(content)
}
