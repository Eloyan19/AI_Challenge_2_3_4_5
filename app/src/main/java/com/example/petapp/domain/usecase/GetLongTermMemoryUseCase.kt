package com.example.petapp.domain.usecase

import com.example.petapp.domain.model.LongTermMemoryEntry
import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

class GetLongTermMemoryUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(): List<LongTermMemoryEntry> = repository.getLongTermMemory()
}
