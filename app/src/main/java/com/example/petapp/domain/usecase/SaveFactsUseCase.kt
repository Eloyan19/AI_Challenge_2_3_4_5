package com.example.petapp.domain.usecase

import com.example.petapp.domain.repository.ChatRepository

/** Persists updated sticky facts so they survive process death and can be restored on next launch. */
class SaveFactsUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(content: String) = repository.saveFacts(content)
}
