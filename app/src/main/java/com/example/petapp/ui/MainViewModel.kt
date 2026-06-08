package com.example.petapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petapp.data.ApiClient
import com.example.petapp.data.ChatRequest
import com.example.petapp.data.Message
import com.example.petapp.data.Thinking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val apiService = ApiClient.service

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(
            val content: String,
            val tokens: TokenInfo?,
            val cost: Double?,
            val responseTimeSeconds: Double
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    data class TokenInfo(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val cachedTokens: Int
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Цены за 1M токенов
    companion object {
        // deepseek-v4-flash
        private const val FLASH_CACHE_HIT = 0.0028
        private const val FLASH_CACHE_MISS = 0.14
        private const val FLASH_OUTPUT = 0.28

        // deepseek-v4-pro
        private const val PRO_CACHE_HIT = 0.003625
        private const val PRO_CACHE_MISS = 0.435
        private const val PRO_OUTPUT = 0.87
    }

    fun sendRequest(
        model: String,
        prompt: String,
        maxTokens: Int?,
        stop: List<String>?,
        temperature: Double?,
        thinkingEnabled: Boolean,
        reasoningEffort: String?
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val thinking = if (thinkingEnabled) Thinking("enabled") else null
                val finalTemperature = if (thinkingEnabled) null else temperature
                val finalReasoningEffort = if (thinkingEnabled && !reasoningEffort.isNullOrBlank()) {
                    reasoningEffort
                } else null

                val request = ChatRequest(
                    model = model,
                    messages = listOf(Message("user", prompt)),
                    maxTokens = maxTokens,
                    stop = stop,
                    temperature = finalTemperature,
                    thinking = thinking,
                    reasoningEffort = finalReasoningEffort
                )

                val startTime = System.currentTimeMillis()
                val response = apiService.getChatCompletion(request)
                val endTime = System.currentTimeMillis()
                val durationSec = (endTime - startTime) / 1000.0

                val content = response.choices
                    ?.firstOrNull()
                    ?.message
                    ?.content
                    ?: "Ответ пуст"
                Log.v("Response", content)

                // Разбор usage и расчёт стоимости
                val usage = response.usage
                val tokenInfo = if (usage != null) {
                    TokenInfo(
                        promptTokens = usage.promptTokens,
                        completionTokens = usage.completionTokens,
                        totalTokens = usage.totalTokens,
                        cachedTokens = usage.promptTokensDetails?.cachedTokens ?: 0
                    )
                } else null

                val cost = if (tokenInfo != null) {
                    calculateCost(model, tokenInfo)
                } else null

                _uiState.value = UiState.Success(
                    content = content,
                    tokens = tokenInfo,
                    cost = cost,
                    responseTimeSeconds = durationSec
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Ошибка: ${e.localizedMessage}")
            }
        }
    }

    private fun calculateCost(model: String, tokens: TokenInfo): Double {
        val (cacheHitPrice, cacheMissPrice, outputPrice) = when (model) {
            "deepseek-v4-flash" -> Triple(FLASH_CACHE_HIT, FLASH_CACHE_MISS, FLASH_OUTPUT)
            "deepseek-v4-pro" -> Triple(PRO_CACHE_HIT, PRO_CACHE_MISS, PRO_OUTPUT)
            else -> return 0.0
        }
        val cached = tokens.cachedTokens
        val uncached = tokens.promptTokens - cached
        return (cached / 1_000_000.0) * cacheHitPrice +
                (uncached / 1_000_000.0) * cacheMissPrice +
                (tokens.completionTokens / 1_000_000.0) * outputPrice
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }
}