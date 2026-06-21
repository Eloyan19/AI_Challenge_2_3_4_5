package com.example.petapp.domain.usecase

import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.Message

class DetectComplexityUseCase(
    private val llmService: LlmService,
    private val providerConfig: LlmProviderConfig
) {
    suspend operator fun invoke(userInput: String): Boolean = runCatching {
        val request = LlmRequest(
            messages = listOf(
                Message(
                    role    = "system",
                    content = "You classify user requests. Reply with exactly one word: SIMPLE or COMPLEX.\n" +
                              "COMPLEX = requires planning, multi-step execution, creative writing, research, or lengthy generation.\n" +
                              "SIMPLE = factual Q&A, short translation, math, definition, quick lookup."
                ),
                Message(role = "user", content = userInput)
            ),
            model       = providerConfig.backgroundModel,
            maxTokens   = 10,
            temperature = 0.0
        )
        val response = llmService.chat(request)
        response.content?.contains("COMPLEX", ignoreCase = true) == true
    }.getOrDefault(false)
}
