package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

class DeleteLongTermMemoryUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteLongTermMemory(id)
}
