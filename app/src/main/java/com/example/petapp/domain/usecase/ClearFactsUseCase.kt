package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

/** Deletes the persisted sticky facts, clearing the auxiliary data for the STICKY_FACTS strategy. */
class ClearFactsUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.clearFacts()
}
