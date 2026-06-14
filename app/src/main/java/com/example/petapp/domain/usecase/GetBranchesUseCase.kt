package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

class GetBranchesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.getAllBranches()
}
