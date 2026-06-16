package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

class AddLongTermMemoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(category: String, keyName: String, value: String) =
        repository.addLongTermMemory(category, keyName, value)
}
