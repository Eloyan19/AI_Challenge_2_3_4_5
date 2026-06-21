package com.example.petapp.domain

import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.LlmResponse

/**
 * Provider-agnostic interface for LLM chat completions.
 *
 * Implementations live in the data layer ([com.example.petapp.data.DeepSeekLlmService])
 * and translate [LlmRequest] into the provider's wire format, then map the response
 * back to [LlmResponse].
 *
 * To swap providers: add a new implementation in data/, update the @Binds in LlmModule.
 */
interface LlmService {
    suspend fun chat(request: LlmRequest): LlmResponse
}
