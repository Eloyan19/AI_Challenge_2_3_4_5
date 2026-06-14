package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

/** Retrieves the persisted LLM summary for restoring [com.example.petapp.domain.strategy.SummaryStrategy] on session resume. */
class GetSummaryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): String? = repository.getSummary()
}
