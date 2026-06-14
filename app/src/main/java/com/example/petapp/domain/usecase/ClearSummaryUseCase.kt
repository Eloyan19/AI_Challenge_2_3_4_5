package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

/** Deletes the persisted LLM summary, clearing the auxiliary data for the SUMMARY strategy. */
class ClearSummaryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.clearSummary()
}
