package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

/** Retrieves the persisted sticky facts for restoring [com.example.petapp.domain.strategy.StickyFactsStrategy] on session resume. */
class GetFactsUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.getFacts()
}
