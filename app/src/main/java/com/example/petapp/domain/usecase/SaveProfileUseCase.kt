package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

class SaveProfileUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(id: Long?, name: String, instructions: String): Long =
        repository.saveProfile(id, name, instructions)
}
