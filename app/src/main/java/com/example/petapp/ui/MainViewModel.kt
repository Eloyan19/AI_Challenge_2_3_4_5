package com.example.petapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petapp.data.ApiClient
import com.example.petapp.data.Message
import com.example.petapp.data.SimpleAgent
import com.example.petapp.data.local.ChatDatabase
import com.example.petapp.data.repository.ChatRepositoryImpl
import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.usecase.ClearHistoryUseCase
import com.example.petapp.domain.usecase.LoadHistoryUseCase
import com.example.petapp.domain.usecase.SaveTurnUseCase
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── Clean Architecture wiring (без DI-фреймворка) ──────────────────────
    private val repository = ChatRepositoryImpl(
        ChatDatabase.getInstance(application).chatMessageDao()
    )
    private val loadHistoryUseCase  = LoadHistoryUseCase(repository)
    private val saveTurnUseCase     = SaveTurnUseCase(repository)
    private val clearHistoryUseCase = ClearHistoryUseCase(repository)

    private val gson = Gson()
    private val agent = SimpleAgent(ApiClient.service)

    // ── UI state ────────────────────────────────────────────────────────────

    data class ChatTurn(
        val userMessage: String,
        val agentResponse: String,
        val tokenInfo: SimpleAgent.TokenInfo?,
        val cost: Double?,
        val durationSec: Double?  // null у восстановленных из БД ходов
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

    // ── Инициализация: восстанавливаем историю из БД ───────────────────────

    init {
        viewModelScope.launch { restoreHistory() }
    }

    private suspend fun restoreHistory() {
        val saved = loadHistoryUseCase()
        if (saved.isEmpty()) return

        // Восстанавливаем внутреннюю историю агента (для передачи в API)
        val agentMessages = saved.map { gson.fromJson(it.messageJson, Message::class.java) }
        agent.loadHistory(agentMessages)

        // Восстанавливаем историю для UI
        _chatHistory.value = saved.toChatTurns()
    }

    // ── Отправка сообщения ──────────────────────────────────────────────────

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
        agent.onToolCall = { status -> _uiState.value = UiState.Loading(toolStatus = status) }

        viewModelScope.launch {
            _uiState.value = UiState.Loading()

            when (val result = agent.run(userInput)) {
                is SimpleAgent.AgentResult.Success -> {
                    // Сохраняем все сообщения хода в БД
                    saveTurnUseCase(result.turnMessages.toDomainMessages(result))

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

    // ── Новая сессия (кнопка «Новая сессия») ────────────────────────────────

    fun newSession() {
        viewModelScope.launch {
            clearHistoryUseCase()   // очищаем БД
            agent.reset()           // очищаем память агента
            _chatHistory.value = emptyList()
            _uiState.value = UiState.Idle
        }
    }

    fun dismissError() { _uiState.value = UiState.Idle }

    // ── Маппинг Message → ChatMessage (domain) ──────────────────────────────

    private fun List<Message>.toDomainMessages(
        result: SimpleAgent.AgentResult.Success
    ): List<ChatMessage> {
        val turnId = System.currentTimeMillis()
        return mapIndexed { index, message ->
            val isLastAssistant = index == size - 1 && message.role == "assistant"
            ChatMessage(
                turnId = turnId,
                role = message.role,
                messageJson = gson.toJson(message),
                displayText = message.content,
                promptTokens    = if (isLastAssistant) result.tokenInfo?.promptTokens    else null,
                completionTokens= if (isLastAssistant) result.tokenInfo?.completionTokens else null,
                totalTokens     = if (isLastAssistant) result.tokenInfo?.totalTokens      else null,
                cachedTokens    = if (isLastAssistant) result.tokenInfo?.cachedTokens     else null,
                cost            = if (isLastAssistant) result.cost        else null,
                durationSec     = if (isLastAssistant) result.durationSec else null,
                timestamp = System.currentTimeMillis() + index  // гарантируем порядок
            )
        }
    }

    // ── Маппинг ChatMessage → ChatTurn (для восстановления UI) ─────────────

    private fun List<ChatMessage>.toChatTurns(): List<ChatTurn> =
        groupBy { it.turnId }
            .values
            .sortedBy { group -> group.minOf { it.timestamp } }
            .mapNotNull { group ->
                val userMsg      = group.find { it.role == "user" }      ?: return@mapNotNull null
                val assistantMsg = group.findLast { it.role == "assistant" } ?: return@mapNotNull null

                ChatTurn(
                    userMessage  = userMsg.displayText      ?: return@mapNotNull null,
                    agentResponse = assistantMsg.displayText ?: return@mapNotNull null,
                    tokenInfo = assistantMsg.totalTokens?.let {
                        SimpleAgent.TokenInfo(
                            promptTokens     = assistantMsg.promptTokens     ?: 0,
                            completionTokens = assistantMsg.completionTokens ?: 0,
                            totalTokens      = it,
                            cachedTokens     = assistantMsg.cachedTokens     ?: 0
                        )
                    },
                    cost       = assistantMsg.cost,
                    durationSec = assistantMsg.durationSec
                )
            }
}
