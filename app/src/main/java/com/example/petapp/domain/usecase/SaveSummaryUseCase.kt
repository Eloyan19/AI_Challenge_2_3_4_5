package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

/** Persists an updated LLM summary so it survives process death and can be restored on next launch. */
class SaveSummaryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(content: String) = repository.saveSummary(content)
}
