package com.example.petapp.domain.usecase

import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.repository.ChatRepository

/**
 * Persists all messages produced in a single agent turn.
 *
 * Stamps each message with [branchId] before saving so messages are correctly
 * scoped to the active branch regardless of which branch id was set when they
 * were originally created.
 *
 * @param branchId Target branch; defaults to the main branch (id = 1).
 */
class SaveTurnUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(messages: List<ChatMessage>, branchId: Long = 1L) =
        repository.saveMessages(messages.map { it.copy(branchId = branchId) })
}
