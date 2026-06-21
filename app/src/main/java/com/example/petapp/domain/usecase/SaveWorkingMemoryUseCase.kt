package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

class SaveWorkingMemoryUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(content: String) = repository.saveWorkingMemory(content)
}
