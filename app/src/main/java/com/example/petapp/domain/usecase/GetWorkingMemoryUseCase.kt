package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

class GetWorkingMemoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): String? = repository.getWorkingMemory()
}
