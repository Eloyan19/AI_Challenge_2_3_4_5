package com.example.petapp.data

import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.LlmResponse
import com.example.petapp.domain.model.LlmUsage
import javax.inject.Inject

/**
 * DeepSeek implementation of [LlmService].
 *
 * Translates provider-agnostic [LlmRequest] into the DeepSeek/OpenAI wire format
 * ([ChatRequest]) and maps the response back to [LlmResponse].
 *
 * All DeepSeek-specific behaviour (thinking mode, reasoningEffort, tools incompatibility
 * with thinking) is encapsulated here. No other class in the project needs to know about it.
 *
 * To swap providers: add a new class implementing [LlmService] and update the @Binds
 * binding in [com.example.petapp.di.LlmModule].
 */
class DeepSeekLlmService @Inject constructor(
    private val apiService: DeepSeekApiService
) : LlmService {

    override suspend fun chat(request: LlmRequest): LlmResponse {
        val chatRequest = ChatRequest(
            model       = request.model,
            messages    = request.messages,
            maxTokens   = request.maxTokens,
            temperature = if (request.thinkingEnabled) null else request.temperature,
            thinking    = if (request.thinkingEnabled) Thinking("enabled") else null,
            reasoningEffort = if (request.thinkingEnabled && !request.reasoningEffort.isNullOrBlank())
                request.reasoningEffort else null,
            tools      = if (!request.thinkingEnabled) request.tools else null,
            toolChoice = if (!request.thinkingEnabled) request.toolChoice else null
        )
        val response = apiService.getChatCompletion(chatRequest)
        val choice = response.choices?.firstOrNull()
        return LlmResponse(
            content      = choice?.message?.content,
            toolCalls    = choice?.message?.toolCalls,
            finishReason = choice?.finishReason,
            usage        = response.usage?.let { u ->
                LlmUsage(
                    promptTokens     = u.promptTokens,
                    completionTokens = u.completionTokens,
                    totalTokens      = u.totalTokens,
                    cachedTokens     = u.promptTokensDetails?.cachedTokens ?: 0
                )
            }
        )
    }
}
