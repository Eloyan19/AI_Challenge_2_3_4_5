package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

/** Retrieves the persisted sticky facts for restoring [com.example.petapp.domain.strategy.StickyFactsStrategy] on session resume. */
class GetFactsUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.getFacts()
}
