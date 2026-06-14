package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

class CreateBranchUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        name: String,
        parentBranchId: Long,
        checkpointMessageId: Long?
    ): Long = repository.createBranch(name, parentBranchId, checkpointMessageId)
}
