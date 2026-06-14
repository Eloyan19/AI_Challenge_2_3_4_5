package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

/**
 * Deletes all messages, summary, and sticky facts from the database.
 *
 * Called when the user confirms a "New session" action. Does not remove branch records —
 * branches are reset separately via [com.example.petapp.domain.repository.ChatRepository.resetBranches].
 */
class ClearHistoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.clearAll()
}
