package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

/** Persists an updated LLM summary so it survives process death and can be restored on next launch. */
class SaveSummaryUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(content: String) = repository.saveSummary(content)
}
