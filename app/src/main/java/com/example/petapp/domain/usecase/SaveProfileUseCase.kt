package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

class SaveProfileUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(id: Long?, name: String, instructions: String): Long =
        repository.saveProfile(id, name, instructions)
}
