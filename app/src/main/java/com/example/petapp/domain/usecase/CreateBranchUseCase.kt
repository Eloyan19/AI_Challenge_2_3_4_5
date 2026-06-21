package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * Creates a new conversation branch forked from an existing parent branch.
 *
 * @return The auto-generated database id of the new branch.
 */
class CreateBranchUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(
        name: String,
        parentBranchId: Long,
        checkpointMessageId: Long?
    ): Long = repository.createBranch(name, parentBranchId, checkpointMessageId)
}
