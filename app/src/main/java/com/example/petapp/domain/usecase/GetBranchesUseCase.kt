package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

/** Loads all branches from the database; used to populate the branch selector in the UI. */
class GetBranchesUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.getAllBranches()
}
