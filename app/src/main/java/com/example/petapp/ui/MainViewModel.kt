package com.example.petapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petapp.data.ApiClient
import com.example.petapp.data.SimpleAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    data class ChatTurn(
        val userMessage: String,
        val agentResponse: String,
        val tokenInfo: SimpleAgent.TokenInfo?,
        val cost: Double?,
        val durationSec: Double
    )

    sealed class UiState {
        object Idle : UiState()
        data class Loading(val toolStatus: String? = null) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatTurn>>(emptyList())
    val chatHistory: StateFlow<List<ChatTurn>> = _chatHistory.asStateFlow()

    private val agent = SimpleAgent(ApiClient.service)

    fun sendMessage(
        userInput: String,
        model: String,
        maxTokens: Int?,
        temperature: Double?,
        thinkingEnabled: Boolean,
        reasoningEffort: String?
    ) {
        agent.updateConfig(
            SimpleAgent.AgentConfig(
                model = model,
                maxTokens = maxTokens,
                temperature = temperature,
                thinkingEnabled = thinkingEnabled,
                reasoningEffort = reasoningEffort
            )
        )
        agent.onToolCall = { status ->
            _uiState.value = UiState.Loading(toolStatus = status)
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading()
            when (val result = agent.run(userInput)) {
                is SimpleAgent.AgentResult.Success -> {
                    _chatHistory.value += ChatTurn(
                        userMessage = userInput,
                        agentResponse = result.response,
                        tokenInfo = result.tokenInfo,
                        cost = result.cost,
                        durationSec = result.durationSec
                    )
                    _uiState.value = UiState.Idle
                }
                is SimpleAgent.AgentResult.Failure -> {
                    _uiState.value = UiState.Error(result.error)
                }
            }
        }
    }

    fun newSession() {
        agent.reset()
        _chatHistory.value = emptyList()
        _uiState.value = UiState.Idle
    }

    fun dismissError() {
        _uiState.value = UiState.Idle
    }
}
