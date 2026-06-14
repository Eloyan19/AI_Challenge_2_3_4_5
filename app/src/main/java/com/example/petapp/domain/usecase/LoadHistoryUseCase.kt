package com.example.petapp.domain.usecase

import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.repository.ChatRepository

/**
 * Loads all persisted messages from the database for session restore.
 *
 * Called during ViewModel initialization to re-populate the agent's history and the UI
 * after the app restarts. For the Branching strategy, history is reconstructed differently
 * via `reconstructBranchHistory` in [com.example.petapp.ui.MainViewModel].
 */
class LoadHistoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): List<ChatMessage> = repository.getAllMessages()
}
