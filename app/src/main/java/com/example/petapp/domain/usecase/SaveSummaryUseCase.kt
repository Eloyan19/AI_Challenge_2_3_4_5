package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

class SaveSummaryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(content: String) = repository.saveSummary(content)
}
