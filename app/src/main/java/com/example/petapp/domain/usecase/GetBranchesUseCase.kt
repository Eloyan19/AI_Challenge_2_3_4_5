package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

/** Loads all branches from the database; used to populate the branch selector in the UI. */
class GetBranchesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.getAllBranches()
}
