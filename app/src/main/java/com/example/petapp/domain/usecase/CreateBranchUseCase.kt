package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

/**
 * Creates a new conversation branch forked from an existing parent branch.
 *
 * @return The auto-generated database id of the new branch.
 */
class CreateBranchUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        name: String,
        parentBranchId: Long,
        checkpointMessageId: Long?
    ): Long = repository.createBranch(name, parentBranchId, checkpointMessageId)
}
