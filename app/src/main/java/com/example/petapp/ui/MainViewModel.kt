package com.example.petapp.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petapp.data.ApiClient
import com.example.petapp.data.Message
import com.example.petapp.data.SimpleAgent
import com.example.petapp.data.local.ChatDatabase
import com.example.petapp.data.repository.ChatRepositoryImpl
import com.example.petapp.domain.model.Branch
import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.model.StrategyType
import com.example.petapp.domain.strategy.BranchingStrategy
import com.example.petapp.domain.strategy.ContextStrategy
import com.example.petapp.domain.strategy.NoopStrategy
import com.example.petapp.domain.strategy.SlidingWindowStrategy
import com.example.petapp.domain.strategy.StickyFactsStrategy
import com.example.petapp.domain.strategy.SummaryStrategy
import com.example.petapp.domain.usecase.ClearFactsUseCase
import com.example.petapp.domain.usecase.ClearHistoryUseCase
import com.example.petapp.domain.usecase.ClearSummaryUseCase
import com.example.petapp.domain.usecase.CreateBranchUseCase
import com.example.petapp.domain.usecase.GetBranchesUseCase
import com.example.petapp.domain.usecase.GetFactsUseCase
import com.example.petapp.domain.usecase.GetSummaryUseCase
import com.example.petapp.domain.usecase.LoadHistoryUseCase
import com.example.petapp.domain.usecase.SaveFactsUseCase
import com.example.petapp.domain.usecase.SaveSummaryUseCase
import com.example.petapp.domain.usecase.SaveTurnUseCase
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val CONTEXT_LIMIT     = 128_000
        const val DEFAULT_KEEP_LAST = 10
        private const val PREFS_NAME    = "context_strategy_prefs"
        private const val KEY_STRATEGY  = "strategy_type"
        private const val KEY_KEEP_LAST = "keep_last_n"
        const val MAIN_BRANCH_ID = 1L
    }

    // ── Infrastructure ─────────────────────────────────────────────────────────
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val db = ChatDatabase.getInstance(application)
    private val repository = ChatRepositoryImpl(
        db.chatMessageDao(), db.summaryDao(), db.stickyFactsDao(), db.branchDao()
    )
    private val gson  = Gson()
    private val agent = SimpleAgent(ApiClient.service)

    // ── Use cases ──────────────────────────────────────────────────────────────
    private val loadHistoryUseCase  = LoadHistoryUseCase(repository)
    private val saveTurnUseCase     = SaveTurnUseCase(repository)
    private val clearHistoryUseCase = ClearHistoryUseCase(repository)
    private val getSummaryUseCase   = GetSummaryUseCase(repository)
    private val saveSummaryUseCase  = SaveSummaryUseCase(repository)
    private val clearSummaryUseCase = ClearSummaryUseCase(repository)
    private val getFactsUseCase     = GetFactsUseCase(repository)
    private val saveFactsUseCase    = SaveFactsUseCase(repository)
    private val clearFactsUseCase   = ClearFactsUseCase(repository)
    private val getBranchesUseCase  = GetBranchesUseCase(repository)
    private val createBranchUseCase = CreateBranchUseCase(repository)

    // ── UI models ──────────────────────────────────────────────────────────────

    data class ChatTurn(
        val userMessage: String,
        val agentResponse: String,
        val tokenInfo: SimpleAgent.TokenInfo?,
        val cost: Double?,
        val durationSec: Double?,
        val lastMessageId: Long? = null
    )

    data class SessionStats(
        val turnCount: Int = 0,
        val totalCompletionTokens: Int = 0,
        val totalCost: Double = 0.0,
        val contextTokens: Int = 0,
        val contextLimit: Int = CONTEXT_LIMIT
    ) {
        val contextFraction: Float
            get() = if (contextLimit > 0) contextTokens.toFloat() / contextLimit else 0f
    }

    sealed class UiState {
        object Idle : UiState()
        data class Loading(val toolStatus: String? = null) : UiState()
        data class Error(val message: String, val isContextOverflow: Boolean = false) : UiState()
    }

    // ── Core state ─────────────────────────────────────────────────────────────

    private val _uiState     = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatTurn>>(emptyList())
    val chatHistory: StateFlow<List<ChatTurn>> = _chatHistory.asStateFlow()

    val sessionStats: StateFlow<SessionStats> = chatHistory
        .map { turns ->
            if (turns.isEmpty()) return@map SessionStats()
            SessionStats(
                turnCount             = turns.size,
                totalCompletionTokens = turns.sumOf { it.tokenInfo?.completionTokens ?: 0 },
                totalCost             = turns.sumOf { it.cost ?: 0.0 },
                contextTokens         = turns.last().tokenInfo?.totalTokens ?: 0
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionStats())

    // ── Strategy state ─────────────────────────────────────────────────────────

    private val _currentStrategyType = MutableStateFlow(
        StrategyType.valueOf(prefs.getString(KEY_STRATEGY, StrategyType.NONE.name)!!)
    )
    val currentStrategyType: StateFlow<StrategyType> = _currentStrategyType.asStateFlow()

    private val _keepLastN = MutableStateFlow(prefs.getInt(KEY_KEEP_LAST, DEFAULT_KEEP_LAST))
    val keepLastN: StateFlow<Int> = _keepLastN.asStateFlow()

    private val _auxData = MutableStateFlow<String?>(null)
    val auxData: StateFlow<String?> = _auxData.asStateFlow()

    // ── Branch state ───────────────────────────────────────────────────────────

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches: StateFlow<List<Branch>> = _branches.asStateFlow()

    private val _activeBranchId = MutableStateFlow(MAIN_BRANCH_ID)
    val activeBranchId: StateFlow<Long> = _activeBranchId.asStateFlow()

    // ── Init ───────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            applyStrategy(_currentStrategyType.value, _keepLastN.value, restoreAux = true)
            restoreHistory(_activeBranchId.value)
            if (_currentStrategyType.value == StrategyType.BRANCHING) {
                _branches.value = getBranchesUseCase()
            }
        }
    }

    // ── Strategy management ────────────────────────────────────────────────────

    fun applyStrategyConfig(type: StrategyType, keepLastN: Int) {
        prefs.edit()
            .putString(KEY_STRATEGY, type.name)
            .putInt(KEY_KEEP_LAST, keepLastN)
            .apply()
        _currentStrategyType.value = type
        _keepLastN.value           = keepLastN

        // Clear aux data that belongs to the previous strategy
        viewModelScope.launch {
            when (type) {
                StrategyType.SUMMARY      -> { clearFactsUseCase(); _auxData.value = (agent.strategy as? SummaryStrategy)?.summary }
                StrategyType.STICKY_FACTS -> { clearSummaryUseCase(); _auxData.value = (agent.strategy as? StickyFactsStrategy)?.facts }
                else                      -> { clearSummaryUseCase(); clearFactsUseCase(); _auxData.value = null }
            }
            applyStrategy(type, keepLastN, restoreAux = false)
        }
    }

    private suspend fun applyStrategy(type: StrategyType, n: Int, restoreAux: Boolean) {
        val newStrategy: ContextStrategy = when (type) {
            StrategyType.NONE           -> NoopStrategy()
            StrategyType.SLIDING_WINDOW -> SlidingWindowStrategy(n)
            StrategyType.SUMMARY        -> SummaryStrategy(ApiClient.service, n).also { s ->
                if (restoreAux) {
                    val saved = getSummaryUseCase()
                    s.summary = saved
                    _auxData.value = saved
                }
                s.onAuxDataUpdated = { text ->
                    _auxData.value = text
                    viewModelScope.launch {
                        if (text != null) saveSummaryUseCase(text) else clearSummaryUseCase()
                    }
                }
            }
            StrategyType.STICKY_FACTS   -> StickyFactsStrategy(ApiClient.service, n).also { s ->
                if (restoreAux) {
                    val saved = getFactsUseCase()
                    s.facts = saved
                    _auxData.value = saved
                }
                s.onAuxDataUpdated = { text ->
                    _auxData.value = text
                    viewModelScope.launch {
                        if (text != null) saveFactsUseCase(text) else clearFactsUseCase()
                    }
                }
            }
            StrategyType.BRANCHING      -> BranchingStrategy()
        }
        agent.strategy = newStrategy
    }

    // ── Send message ───────────────────────────────────────────────────────────

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
                model           = model,
                maxTokens       = maxTokens,
                temperature     = temperature,
                thinkingEnabled = thinkingEnabled,
                reasoningEffort = reasoningEffort
            )
        )
        agent.onToolCall = { status -> _uiState.value = UiState.Loading(toolStatus = status) }

        viewModelScope.launch {
            _uiState.value = UiState.Loading()

            when (val result = agent.run(userInput)) {
                is SimpleAgent.AgentResult.Success -> {
                    val branchId = _activeBranchId.value
                    saveTurnUseCase(result.turnMessages.toDomainMessages(result), branchId)
                    val lastId = repository.getLastMessageIdForBranch(branchId)
                    _chatHistory.value += ChatTurn(
                        userMessage   = userInput,
                        agentResponse = result.response,
                        tokenInfo     = result.tokenInfo,
                        cost          = result.cost,
                        durationSec   = result.durationSec,
                        lastMessageId = lastId
                    )
                    _uiState.value = UiState.Idle
                }
                is SimpleAgent.AgentResult.Failure -> {
                    val isOverflow = sessionStats.value.contextFraction >= 0.90f
                    _uiState.value = UiState.Error(result.error, isContextOverflow = isOverflow)
                }
            }
        }
    }

    // ── Branch management ──────────────────────────────────────────────────────

    fun createBranch(name: String, checkpointMessageId: Long?) {
        viewModelScope.launch {
            val parentId = _activeBranchId.value
            val newBranchId = createBranchUseCase(name, parentId, checkpointMessageId)
            _branches.value = getBranchesUseCase()
            switchBranch(newBranchId)
        }
    }

    fun switchBranch(branchId: Long) {
        viewModelScope.launch {
            _activeBranchId.value = branchId
            val fullHistory = reconstructBranchHistory(branchId)
            agent.loadHistory(fullHistory.map { gson.fromJson(it.messageJson, Message::class.java) })
            _chatHistory.value = fullHistory.toChatTurns()
        }
    }

    /**
     * Walks up the branch tree and reconstructs the full message list:
     * parent messages up to the checkpoint + this branch's own messages.
     */
    /**
     * Recursively reconstructs the full message sequence for a branch:
     * parent's history (filtered to the checkpoint) + this branch's own messages.
     * Works at any depth.
     */
    private suspend fun reconstructBranchHistory(branchId: Long): List<ChatMessage> {
        val branch = repository.getBranch(branchId) ?: return emptyList()
        val ownMessages = repository.getMessagesForBranch(branchId)

        if (branch.parentBranchId == null) return ownMessages

        val parentHistory = reconstructBranchHistory(branch.parentBranchId)
        val checkpointId  = branch.checkpointMessageId
        val parentUpTo    = if (checkpointId != null) {
            parentHistory.filter { it.id <= checkpointId }
        } else {
            parentHistory
        }

        return parentUpTo + ownMessages
    }

    // ── New session ────────────────────────────────────────────────────────────

    fun newSession() {
        viewModelScope.launch {
            clearHistoryUseCase()
            clearSummaryUseCase()
            clearFactsUseCase()
            repository.resetBranches()

            agent.reset()
            _chatHistory.value    = emptyList()
            _auxData.value        = null
            _activeBranchId.value = MAIN_BRANCH_ID
            _branches.value       = emptyList()
            _uiState.value        = UiState.Idle
        }
    }

    fun dismissError() { _uiState.value = UiState.Idle }

    // ── Restore history ────────────────────────────────────────────────────────

    private suspend fun restoreHistory(branchId: Long) {
        val saved = if (_currentStrategyType.value == StrategyType.BRANCHING) {
            reconstructBranchHistory(branchId)
        } else {
            loadHistoryUseCase()
        }
        if (saved.isEmpty()) return
        agent.loadHistory(saved.map { gson.fromJson(it.messageJson, Message::class.java) })
        _chatHistory.value = saved.toChatTurns()
    }

    // ── Mapping helpers ────────────────────────────────────────────────────────

    private fun List<Message>.toDomainMessages(
        result: SimpleAgent.AgentResult.Success
    ): List<ChatMessage> {
        val turnId = System.currentTimeMillis()
        return mapIndexed { index, message ->
            val isLastAssistant = index == size - 1 && message.role == "assistant"
            ChatMessage(
                turnId           = turnId,
                role             = message.role,
                messageJson      = gson.toJson(message),
                displayText      = message.content,
                promptTokens     = if (isLastAssistant) result.tokenInfo?.promptTokens     else null,
                completionTokens = if (isLastAssistant) result.tokenInfo?.completionTokens else null,
                totalTokens      = if (isLastAssistant) result.tokenInfo?.totalTokens      else null,
                cachedTokens     = if (isLastAssistant) result.tokenInfo?.cachedTokens     else null,
                cost             = if (isLastAssistant) result.cost        else null,
                durationSec      = if (isLastAssistant) result.durationSec else null,
                timestamp        = System.currentTimeMillis() + index
            )
        }
    }

    private fun List<ChatMessage>.toChatTurns(): List<ChatTurn> =
        groupBy { it.turnId }
            .values
            .sortedBy { group -> group.minOf { it.timestamp } }
            .mapNotNull { group ->
                val userMsg      = group.find     { it.role == "user"      } ?: return@mapNotNull null
                val assistantMsg = group.findLast { it.role == "assistant" } ?: return@mapNotNull null
                ChatTurn(
                    userMessage   = userMsg.displayText      ?: return@mapNotNull null,
                    agentResponse = assistantMsg.displayText ?: return@mapNotNull null,
                    tokenInfo     = assistantMsg.totalTokens?.let {
                        SimpleAgent.TokenInfo(
                            promptTokens     = assistantMsg.promptTokens     ?: 0,
                            completionTokens = assistantMsg.completionTokens ?: 0,
                            totalTokens      = it,
                            cachedTokens     = assistantMsg.cachedTokens     ?: 0
                        )
                    },
                    cost          = assistantMsg.cost,
                    durationSec   = assistantMsg.durationSec,
                    lastMessageId = assistantMsg.id.takeIf { it > 0 }
                )
            }
}
