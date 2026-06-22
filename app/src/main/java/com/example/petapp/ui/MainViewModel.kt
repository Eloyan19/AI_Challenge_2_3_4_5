package com.example.petapp.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petapp.data.GuardrailsLoader
import com.example.petapp.data.SimpleAgent
import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.Branch
import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.model.LongTermMemoryEntry
import com.example.petapp.domain.model.ShortTermType
import com.example.petapp.domain.model.StrategyType
import com.example.petapp.domain.model.UserProfile
import com.example.petapp.domain.repository.ChatRepository
import com.example.petapp.domain.strategy.BranchingStrategy
import com.example.petapp.domain.strategy.ContextStrategy
import com.example.petapp.domain.strategy.MemoryLayersStrategy
import com.example.petapp.domain.strategy.NoopStrategy
import com.example.petapp.domain.strategy.SlidingWindowStrategy
import com.example.petapp.domain.strategy.StickyFactsStrategy
import com.example.petapp.domain.strategy.SummaryStrategy
import com.example.petapp.domain.model.TaskState
import com.example.petapp.domain.model.TaskStateMachine
import com.example.petapp.domain.usecase.ChatUseCases
import com.example.petapp.domain.usecase.TaskOrchestratorUseCase
import com.example.petapp.domain.usecase.TaskUseCases
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Primary ViewModel that drives the chat UI and orchestrates the AI agent, context strategies,
 * persistence, and branch management.
 *
 * **Threading:** [SimpleAgent.history] is not thread-safe. All operations that read or write it
 * ([sendMessage], [switchBranch], [applyStrategyConfig]) acquire [agentMutex] before executing,
 * serializing concurrent coroutines that would otherwise race on agent state.
 *
 * **Session persistence:** On construction the ViewModel restores the previously active strategy
 * (including any saved summary/facts) and reloads the message history from the database, so the
 * user continues exactly where they left off after process death.
 *
 * **Inference settings** ([selectedModel], [thinkingEnabled], [reasoningEffort], [maxTokensText],
 * [temperatureText]) are kept in [StateFlow]s rather than Compose `remember` state so they survive
 * configuration changes (screen rotation).
 *
 * @param prefs Shared preferences for persisting strategy settings between sessions.
 * @param repository Chat persistence layer.
 * @param agent The AI agent; shared singleton, accessed only under [agentMutex].
 * @param llmService Provider-agnostic LLM service passed into context strategies.
 * @param providerConfig Model list, context limit, and pricing for the active LLM provider.
 * @param gson Singleton Gson for serializing/deserializing [Message] objects.
 */
class MainViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val repository: ChatRepository,
    private val agent: SimpleAgent,
    private val llmService: LlmService,
    private val providerConfig: LlmProviderConfig,
    private val gson: Gson,
    private val guardrailsLoader: GuardrailsLoader,
    private val chat: ChatUseCases,
    private val tasks: TaskUseCases,
) : ViewModel() {

    companion object {
        /** Default number of messages kept in the live window for sliding-window strategies. */
        const val DEFAULT_KEEP_LAST = 10

        /** Maximum number of turns materialized into the UI list. Older turns stay in the DB only.
         *  This caps LazyColumn memory under NONE/BRANCHING strategies where agent history is uncapped. */
        const val MAX_DISPLAY_TURNS = 100

        /** Name of the [SharedPreferences] file used for strategy settings. */
        const val PREFS_NAME    = "context_strategy_prefs"

        /** SharedPreferences key for the active [StrategyType] name. */
        const val KEY_STRATEGY  = "strategy_type"

        /** SharedPreferences key for the keep-last-N window size. */
        const val KEY_KEEP_LAST = "keep_last_n"

        /** SharedPreferences key for the active user profile id (-1 = no active profile). */
        const val KEY_ACTIVE_PROFILE = "active_profile_id"

        /** SharedPreferences key for the Layer 1 strategy within MEMORY_LAYERS. */
        const val KEY_SHORT_TERM_TYPE = "memory_layers_short_term_type"

        /** Default Layer 1 strategy — mirrors the original hardcoded sliding-window behaviour. */
        val DEFAULT_SHORT_TERM_TYPE = ShortTermType.SLIDING_WINDOW

        /** Database id of the permanent root branch that is always present. */
        const val MAIN_BRANCH_ID = 1L

        /** Maximum number of replanning attempts per task before the orchestrator gives up. */
        const val MAX_REPLAN_ATTEMPTS = 3
    }

    /** Serializes all agent operations to prevent concurrent mutation of agent history. */
    val agentMutex  = Mutex()

    /** Tracks how many replan attempts have been made for the current task. Reset on new task or dismiss. */
    private var replanCount = 0

    /**
     * Monotonically-increasing counter used to generate collision-free [ChatMessage.turnId] values.
     * Seeded at the current epoch millis so it never collides with previously persisted ids
     * (which were also millis-based) after process restart.
     */
    private val turnIdSource = AtomicLong(System.currentTimeMillis())

    // ── UI models ──────────────────────────────────────────────────────────────

    /**
     * A single user↔agent exchange shown as one row in the chat list.
     *
     * @property userMessage The user's input text.
     * @property agentResponse The final assistant response text.
     * @property tokenInfo Usage stats from the last API call in this turn.
     * @property cost Estimated USD cost of this turn.
     * @property durationSec Wall-clock seconds for the API call.
     * @property lastMessageId The database id of the last [ChatMessage] in this turn;
     *   used as a branch checkpoint when the user forks the conversation here.
     */
    data class ChatTurn(
        val userMessage: String,
        val agentResponse: String,
        val tokenInfo: SimpleAgent.TokenInfo?,
        val cost: Double?,
        val durationSec: Double?,
        val lastMessageId: Long? = null
    )

    /**
     * Aggregate session statistics displayed in the context usage bar.
     *
     * @property turnCount Total number of completed turns in the current view.
     * @property totalCompletionTokens Sum of output tokens across all turns.
     * @property totalCost Cumulative estimated USD cost of the session.
     * @property contextTokens Total tokens reported by the last API call (prompt + completion).
     * @property contextLimit Model-specific token limit used to compute [contextFraction].
     */
    data class SessionStats(
        val turnCount: Int = 0,
        val totalCompletionTokens: Int = 0,
        val totalCost: Double = 0.0,
        val contextTokens: Int = 0,
        val contextLimit: Int = 128_000
    ) {
        /** Fraction of the context window consumed; used to drive the progress bar width. */
        val contextFraction: Float
            get() = if (contextLimit > 0) contextTokens.toFloat() / contextLimit else 0f
    }

    /** Discriminated union representing the state of the chat input/output area. */
    sealed class UiState {
        /** No request in flight; the user can type and send. */
        object Idle : UiState()

        /**
         * A request is in flight.
         *
         * @property toolStatus Optional status string shown while a tool call is executing
         *   (e.g. "Запрашиваю погоду..."). Null while waiting for the initial API response.
         */
        data class Loading(val toolStatus: String? = null) : UiState()

        /**
         * The last request failed.
         *
         * @property message Human-readable error description.
         * @property isContextOverflow True when the failure was caused by the model's context limit
         *   being exceeded; the UI shows a specific overflow warning in this case.
         */
        data class Error(val message: String, val isContextOverflow: Boolean = false) : UiState()
    }

    // ── Core state ─────────────────────────────────────────────────────────────

    private val _uiState     = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _chatHistory = MutableStateFlow<ImmutableList<ChatTurn>>(persistentListOf())
    val chatHistory: StateFlow<ImmutableList<ChatTurn>> = _chatHistory.asStateFlow()

    // ── Inference settings state (survives config changes) ─────────────────────
    // _selectedModel must be declared before sessionStats because sessionStats
    // captures it in the combine() initializer.

    private val _selectedModel   = MutableStateFlow(providerConfig.defaultModel)
    val selectedModel: StateFlow<String>  = _selectedModel.asStateFlow()

    private val _thinkingEnabled = MutableStateFlow(false)
    val thinkingEnabled: StateFlow<Boolean> = _thinkingEnabled.asStateFlow()

    private val _reasoningEffort = MutableStateFlow("medium")
    val reasoningEffort: StateFlow<String> = _reasoningEffort.asStateFlow()

    private val _maxTokensText   = MutableStateFlow("")
    val maxTokensText: StateFlow<String>  = _maxTokensText.asStateFlow()

    private val _temperatureText = MutableStateFlow("")
    val temperatureText: StateFlow<String> = _temperatureText.asStateFlow()

    fun setModel(model: String)              { _selectedModel.value   = model }
    fun setThinkingEnabled(enabled: Boolean) { _thinkingEnabled.value = enabled }
    fun setReasoningEffort(effort: String)   { _reasoningEffort.value = effort }
    fun setMaxTokensText(text: String)       { _maxTokensText.value   = text }
    fun setTemperatureText(text: String)     { _temperatureText.value = text }

    /**
     * Aggregate session statistics derived from [chatHistory] and [selectedModel].
     * Recomputed reactively when either flow changes so the context bar reflects the
     * correct limit for the currently selected model.
     */
    val sessionStats: StateFlow<SessionStats> = combine(chatHistory, _selectedModel) { turns, _ ->
        val limit = providerConfig.contextLimit
        if (turns.isEmpty()) return@combine SessionStats(contextLimit = limit)
        SessionStats(
            turnCount             = turns.size,
            totalCompletionTokens = turns.sumOf { it.tokenInfo?.completionTokens ?: 0 },
            totalCost             = turns.sumOf { it.cost ?: 0.0 },
            contextTokens         = turns.last().tokenInfo?.totalTokens ?: 0,
            contextLimit          = limit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionStats())

    // ── Strategy state ─────────────────────────────────────────────────────────

    private val _currentStrategyType = MutableStateFlow(
        runCatching {
            StrategyType.valueOf(prefs.getString(KEY_STRATEGY, StrategyType.NONE.name)!!)
        }.getOrDefault(StrategyType.NONE)
    )

    /** The strategy that is currently active in the agent (not the pending UI selection). */
    val currentStrategyType: StateFlow<StrategyType> = _currentStrategyType.asStateFlow()

    private val _keepLastN = MutableStateFlow(prefs.getInt(KEY_KEEP_LAST, DEFAULT_KEEP_LAST))
    val keepLastN: StateFlow<Int> = _keepLastN.asStateFlow()

    private val _shortTermType = MutableStateFlow(
        runCatching {
            ShortTermType.valueOf(prefs.getString(KEY_SHORT_TERM_TYPE, DEFAULT_SHORT_TERM_TYPE.name)!!)
        }.getOrDefault(DEFAULT_SHORT_TERM_TYPE)
    )
    val shortTermType: StateFlow<ShortTermType> = _shortTermType.asStateFlow()

    /** Summary text (for SUMMARY strategy) or facts list (for STICKY_FACTS) or working memory (for MEMORY_LAYERS); null otherwise. */
    private val _auxData = MutableStateFlow<String?>(null)
    val auxData: StateFlow<String?> = _auxData.asStateFlow()

    // ── Memory layers state ────────────────────────────────────────────────────

    private val _longTermMemories = MutableStateFlow<List<LongTermMemoryEntry>>(emptyList())
    val longTermMemories: StateFlow<List<LongTermMemoryEntry>> = _longTermMemories.asStateFlow()

    private val _shortTermCount = MutableStateFlow(0)
    val shortTermCount: StateFlow<Int> = _shortTermCount.asStateFlow()

    // ── Profile state ──────────────────────────────────────────────────────────

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    // ── Branch state ───────────────────────────────────────────────────────────

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches: StateFlow<List<Branch>> = _branches.asStateFlow()

    private val _activeBranchId = MutableStateFlow(MAIN_BRANCH_ID)
    val activeBranchId: StateFlow<Long> = _activeBranchId.asStateFlow()

    // ── Task state machine ─────────────────────────────────────────────────────

    private val _taskState = MutableStateFlow<TaskState>(TaskState.Idle)
    val taskState: StateFlow<TaskState> = _taskState.asStateFlow()

    private fun setTaskState(newState: TaskState) {
        when (val t = TaskStateMachine.validate(_taskState.value, newState)) {
            is TaskStateMachine.Transition.Allowed -> _taskState.value = newState
            is TaskStateMachine.Transition.Forbidden -> {
                android.util.Log.w("TaskStateMachine", t.reason)
                _uiState.value = UiState.Error(t.reason)
            }
        }
    }

    // ── Init ───────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            _uiState.value = UiState.Loading()
            agent.guardrailsInstruction = withContext(Dispatchers.IO) { guardrailsLoader.load() }

            // Restore active profile first so it's injected into the agent before history loads
            val savedProfileId = prefs.getLong(KEY_ACTIVE_PROFILE, -1L)
            if (savedProfileId != -1L) {
                val profile = repository.getProfile(savedProfileId)
                if (profile != null) {
                    _activeProfile.value = profile
                    agent.systemProfileInstructions = profile.instructions
                } else {
                    prefs.edit().remove(KEY_ACTIVE_PROFILE).apply()
                }
            }
            _profiles.value = chat.getProfiles()

            applyStrategy(_currentStrategyType.value, _keepLastN.value, restoreAux = true)
            restoreHistory(_activeBranchId.value)
            if (_currentStrategyType.value == StrategyType.BRANCHING) {
                _branches.value = chat.getBranches()
            }
            if (_currentStrategyType.value == StrategyType.MEMORY_LAYERS) {
                _longTermMemories.value = chat.getLongTermMemory()
            }
            // Restore task state — if app was closed while plan was awaiting confirmation,
            // bring the user back to the approval screen
            repository.getTaskPlan()?.let { planData ->
                _taskState.value = TaskState.AwaitingInput(  // direct set: restore on cold start bypasses validation
                    userInput = planData.userInput,
                    plan      = planData.plan,
                    critique  = planData.critique
                )
            }
            _uiState.value = UiState.Idle
        }
    }

    // ── Strategy management ────────────────────────────────────────────────────

    /**
     * Persists the new strategy settings and applies them to the agent.
     *
     * Waits for [agentMutex] before swapping the active strategy, so an in-progress
     * [sendMessage] coroutine completes with the old strategy before the new one takes effect.
     *
     * @param type The new strategy to activate.
     * @param keepLastN Window size for strategies that trim the history.
     */
    fun applyStrategyConfig(type: StrategyType, keepLastN: Int, shortTermType: ShortTermType = _shortTermType.value) {
        prefs.edit()
            .putString(KEY_STRATEGY, type.name)
            .putInt(KEY_KEEP_LAST, keepLastN)
            .putString(KEY_SHORT_TERM_TYPE, shortTermType.name)
            .apply()
        _currentStrategyType.value = type
        _keepLastN.value           = keepLastN
        _shortTermType.value       = shortTermType

        viewModelScope.launch {
            agentMutex.withLock {
                // Detach the old strategy's callback before clearing so a racing afterTurn
                // coroutine cannot write stale aux data back after the clear.
                agent.strategy.onAuxDataUpdated = null
                // Clear all aux data — each strategy starts fresh, old aux is irrelevant
                chat.clearSummary()
                chat.clearFacts()
                chat.clearWorkingMemory()
                _auxData.value = null
                applyStrategy(type, keepLastN, restoreAux = false)
                // Reload history from DB under the new strategy's rules so the agent's
                // in-memory context is consistent with what the new strategy expects.
                agent.loadHistory(emptyList())
                _shortTermCount.value = 0
                restoreHistory(_activeBranchId.value)
            }
            if (type == StrategyType.BRANCHING) {
                _branches.value = chat.getBranches()
            }
            if (type == StrategyType.MEMORY_LAYERS) {
                _longTermMemories.value = chat.getLongTermMemory()
            }
        }
    }

    /**
     * Instantiates the correct [ContextStrategy] and assigns it to the agent.
     *
     * For SUMMARY and STICKY_FACTS strategies, wires up [ContextStrategy.onAuxDataUpdated]
     * so auxiliary data is persisted to the database whenever the strategy updates it.
     * When [restoreAux] is true, previously saved data is loaded from the database
     * and injected into the new strategy instance (used on session restore).
     */
    private suspend fun applyStrategy(type: StrategyType, n: Int, restoreAux: Boolean) {
        val newStrategy: ContextStrategy = when (type) {
            StrategyType.NONE           -> NoopStrategy()
            StrategyType.SLIDING_WINDOW -> SlidingWindowStrategy(n)
            StrategyType.SUMMARY        -> SummaryStrategy(llmService, providerConfig, n).also { s ->
                if (restoreAux) {
                    val saved = chat.getSummary()
                    s.summary = saved
                    _auxData.value = saved
                }
                s.onAuxDataUpdated = { text ->
                    _auxData.value = text
                    viewModelScope.launch {
                        if (text != null) chat.saveSummary(text) else chat.clearSummary()
                    }
                }
            }
            StrategyType.STICKY_FACTS   -> StickyFactsStrategy(llmService, providerConfig, n).also { s ->
                if (restoreAux) {
                    val saved = chat.getFacts()
                    s.facts = saved
                    _auxData.value = saved
                }
                s.onAuxDataUpdated = { text ->
                    _auxData.value = text
                    viewModelScope.launch {
                        if (text != null) chat.saveFacts(text) else chat.clearFacts()
                    }
                }
            }
            StrategyType.BRANCHING      -> BranchingStrategy()
            StrategyType.MEMORY_LAYERS  -> {
                val inner: ContextStrategy = when (_shortTermType.value) {
                    ShortTermType.NONE           -> NoopStrategy()
                    ShortTermType.SLIDING_WINDOW -> SlidingWindowStrategy(n)
                    ShortTermType.STICKY_FACTS   -> StickyFactsStrategy(llmService, providerConfig, n).also { sf ->
                        if (restoreAux) { sf.facts = chat.getFacts() }
                        sf.onAuxDataUpdated = { text ->
                            // Inner-strategy facts persist to saveFacts(); _auxData is reserved for Layer 2
                            viewModelScope.launch {
                                if (text != null) chat.saveFacts(text) else chat.clearFacts()
                            }
                        }
                    }
                }
                MemoryLayersStrategy(llmService, providerConfig, inner, n, gson = gson).also { s ->
                    val ltm = chat.getLongTermMemory()
                    _longTermMemories.value = ltm
                    s.setLongTermMemory(formatLtmForPrompt(ltm))
                    if (restoreAux) {
                        val saved = chat.getWorkingMemory()
                        s.restoreWorkingMemory(saved)
                        _auxData.value = saved
                    }
                    s.onAuxDataUpdated = { text ->
                        _auxData.value = text
                        viewModelScope.launch {
                            if (text != null) chat.saveWorkingMemory(text) else chat.clearWorkingMemory()
                        }
                    }
                }
            }
        }
        agent.strategy = newStrategy
    }

    // ── Send message ───────────────────────────────────────────────────────────

    /**
     * Sends [userInput] to the agent or the task orchestrator, depending on request complexity.
     *
     * Simple requests go directly to [SimpleAgent.run] (unchanged flow).
     * Complex requests trigger the Task State Machine: PLANNER + CRITIC run in parallel and
     * produce a plan that is shown to the user in [taskState] = [TaskState.AwaitingInput].
     * The mutex is released while the user reads the plan so the UI stays responsive.
     */
    fun sendMessage(userInput: String) {
        // Guard: block new messages during active task lifecycle
        when (val s = _taskState.value) {
            is TaskState.Idle -> Unit
            is TaskState.Done, is TaskState.Error -> _taskState.value = TaskState.Idle  // auto-dismiss
            is TaskState.AwaitingInput -> {
                _uiState.value = UiState.Error("Сначала одобри или отклони план ↑")
                return
            }
            else -> {
                _uiState.value = UiState.Error("Дождись завершения этапа «${s::class.simpleName}»")
                return
            }
        }

        replanCount = 0
        updateAgentConfig()

        viewModelScope.launch {
            agentMutex.withLock {
                agent.onToolCall = { status -> _uiState.value = UiState.Loading(toolStatus = status) }
                _uiState.value = UiState.Loading()
                setTaskState(TaskState.Analyzing(userInput))
                val compressedHistory = orchestratorHistory()
                when (val result = tasks.orchestrator.detectAndPlan(
                    userInput               = userInput,
                    compressedHistory       = compressedHistory,
                    userProfileInstructions = agent.systemProfileInstructions,
                    model                   = _selectedModel.value,
                    guardrailsInstruction   = agent.guardrailsInstruction,
                    onPlanning              = { setTaskState(TaskState.Planning(userInput)) }
                )) {
                    is TaskOrchestratorUseCase.OrchestratorResult.Simple -> {
                        setTaskState(TaskState.Idle)
                        runDirectAnswer(userInput)
                    }
                    is TaskOrchestratorUseCase.OrchestratorResult.PlanReady -> {
                        setTaskState(TaskState.AwaitingInput(userInput, result.plan, result.critique))
                        repository.saveTaskPlan(userInput, result.plan, result.critique)
                        _uiState.value   = UiState.Idle
                    }
                    is TaskOrchestratorUseCase.OrchestratorResult.Failed -> {
                        setTaskState(TaskState.Idle)
                        _uiState.value = UiState.Error(result.error)
                    }
                    else -> {
                        android.util.Log.e("MainViewModel", "Unexpected detectAndPlan result: $result")
                        setTaskState(TaskState.Idle)
                        _uiState.value = UiState.Idle
                    }
                }
            }
        }
    }

    /** Approves the current plan and moves to EXECUTION → VALIDATION → DONE. */
    fun confirmPlan() {
        val state = _taskState.value as? TaskState.AwaitingInput ?: return
        updateAgentConfig()
        viewModelScope.launch {
            agentMutex.withLock {
                _uiState.value = UiState.Loading()
                setTaskState(TaskState.Execution(state.userInput, state.plan))

                // Inject the approved plan into agent.history as a system message so context
                // strategies (Summary, StickyFacts, MemoryLayers) can see and compress it.
                val planContextMsg = Message(
                    role    = "system",
                    content = "=== ЗАДАЧА В РАБОТЕ ===\nЗапрос: ${state.userInput}\n\nУтверждённый план:\n${state.plan}"
                )
                agent.appendMessages(listOf(planContextMsg))
                chat.saveTurn(
                    listOf(ChatMessage(
                        turnId           = turnIdSource.incrementAndGet(),
                        role             = "system",
                        messageJson      = gson.toJson(planContextMsg),
                        displayText      = null,
                        promptTokens     = null, completionTokens = null,
                        totalTokens      = null, cachedTokens     = null,
                        cost             = null, durationSec      = null,
                        timestamp        = System.currentTimeMillis()
                    )),
                    _activeBranchId.value
                )

                val compressedHistory = orchestratorHistory()
                when (val result = tasks.orchestrator.executeAndValidate(
                    userInput               = state.userInput,
                    plan                    = state.plan,
                    compressedHistory       = compressedHistory,
                    userProfileInstructions = agent.systemProfileInstructions,
                    model                   = _selectedModel.value,
                    guardrailsInstruction   = agent.guardrailsInstruction,
                    onValidating            = { execResult ->
                        setTaskState(TaskState.Validation(state.userInput, state.plan, execResult))
                    }
                )) {
                    is TaskOrchestratorUseCase.OrchestratorResult.ExecutionDone -> {
                        repository.clearTaskPlan()
                        persistOrchestratorResult(state.userInput, result.finalAnswer)
                        setTaskState(TaskState.Done(result.finalAnswer))
                        // Done persists until user dismisses via dismissTaskState()
                        _uiState.value = UiState.Idle
                        // Trigger memory update off the critical path — same pattern as runDirectAnswer.
                        // Without this, working memory and sticky facts are never updated for
                        // complex orchestrated tasks (only direct answers triggered afterTurn before).
                        val snapshot = agent.historySnapshot().toMutableList()
                        val currentStrategy = agent.strategy
                        viewModelScope.launch { currentStrategy.afterTurn(snapshot) }
                    }
                    is TaskOrchestratorUseCase.OrchestratorResult.ValidationFailed -> {
                        repository.clearTaskPlan()
                        setTaskState(TaskState.ValidationFailed(
                            userInput       = state.userInput,
                            plan            = state.plan,
                            executionResult = result.executionResult,
                            reason          = result.reason
                        ))
                        _uiState.value = UiState.Idle
                    }
                    is TaskOrchestratorUseCase.OrchestratorResult.Failed -> {
                        repository.clearTaskPlan()
                        setTaskState(TaskState.Error(result.error))
                        _uiState.value = UiState.Idle
                    }
                    else -> {
                        android.util.Log.e("MainViewModel", "Unexpected executeAndValidate result: $result")
                        repository.clearTaskPlan()
                        setTaskState(TaskState.Error("Неожиданный результат оркестрации"))
                        _uiState.value = UiState.Idle
                    }
                }
            }
        }
    }

    /** Retries execution with the same plan after a validation failure. */
    fun retryFromValidationFailed() {
        val s = _taskState.value as? TaskState.ValidationFailed ?: return
        setTaskState(TaskState.AwaitingInput(s.userInput, s.plan))
    }

    /** Triggers replanning directly from a validation failure, skipping the AwaitingInput step. */
    fun replanFromValidationFailed(reason: String = "") {
        if (_taskState.value !is TaskState.ValidationFailed) return
        if (replanCount >= MAX_REPLAN_ATTEMPTS) {
            setTaskState(TaskState.Error("Превышен лимит перепланирований ($MAX_REPLAN_ATTEMPTS). Уточните задачу и начните заново."))
            return
        }
        replanCount++
        updateAgentConfig()
        viewModelScope.launch {
            agentMutex.withLock {
                val s = _taskState.value as? TaskState.ValidationFailed ?: return@withLock
                _uiState.value = UiState.Loading()
                val replanReason = reason.ifBlank { "Валидация не пройдена: ${s.reason}" }
                setTaskState(TaskState.Replanning(s.userInput, s.plan, replanReason))
                val compressedHistory = orchestratorHistory()
                when (val result = tasks.orchestrator.replan(
                    userInput               = s.userInput,
                    previousPlan            = s.plan,
                    rejectionReason         = replanReason,
                    compressedHistory       = compressedHistory,
                    userProfileInstructions = agent.systemProfileInstructions,
                    model                   = _selectedModel.value,
                    guardrailsInstruction   = agent.guardrailsInstruction
                )) {
                    is TaskOrchestratorUseCase.OrchestratorResult.PlanReady -> {
                        setTaskState(TaskState.AwaitingInput(s.userInput, result.plan, result.critique))
                        repository.saveTaskPlan(s.userInput, result.plan, result.critique)
                        _uiState.value = UiState.Idle
                    }
                    is TaskOrchestratorUseCase.OrchestratorResult.Failed -> {
                        repository.clearTaskPlan()
                        setTaskState(TaskState.Error(result.error))
                        _uiState.value = UiState.Idle
                    }
                    else -> {
                        android.util.Log.e("MainViewModel", "Unexpected replan result: $result")
                        repository.clearTaskPlan()
                        setTaskState(TaskState.Error("Неожиданный результат перепланирования"))
                        _uiState.value = UiState.Idle
                    }
                }
            }
        }
    }

    /** Rejects the current plan with an optional [reason] and triggers replanning. */
    fun rejectPlan(reason: String = "") {
        val state = _taskState.value as? TaskState.AwaitingInput ?: return
        if (replanCount >= MAX_REPLAN_ATTEMPTS) {
            setTaskState(TaskState.Error("Превышен лимит перепланирований ($MAX_REPLAN_ATTEMPTS). Уточните задачу и начните заново."))
            return
        }
        replanCount++
        updateAgentConfig()
        viewModelScope.launch {
            agentMutex.withLock {
                _uiState.value = UiState.Loading()
                setTaskState(TaskState.Replanning(state.userInput, state.plan, reason))
                val compressedHistory = orchestratorHistory()
                when (val result = tasks.orchestrator.replan(
                    userInput               = state.userInput,
                    previousPlan            = state.plan,
                    rejectionReason         = reason,
                    compressedHistory       = compressedHistory,
                    userProfileInstructions = agent.systemProfileInstructions,
                    model                   = _selectedModel.value,
                    guardrailsInstruction   = agent.guardrailsInstruction
                )) {
                    is TaskOrchestratorUseCase.OrchestratorResult.PlanReady -> {
                        setTaskState(TaskState.AwaitingInput(state.userInput, result.plan, result.critique))
                        repository.saveTaskPlan(state.userInput, result.plan, result.critique)
                        _uiState.value = UiState.Idle
                    }
                    is TaskOrchestratorUseCase.OrchestratorResult.Failed -> {
                        repository.clearTaskPlan()
                        setTaskState(TaskState.Error(result.error))
                        _uiState.value = UiState.Idle
                    }
                    else -> {
                        android.util.Log.e("MainViewModel", "Unexpected replan result: $result")
                        repository.clearTaskPlan()
                        setTaskState(TaskState.Error("Неожиданный результат перепланирования"))
                        _uiState.value = UiState.Idle
                    }
                }
            }
        }
    }

    /** Resets [taskState] to [TaskState.Idle] without any further action. */
    fun dismissTaskState() {
        replanCount = 0
        _taskState.value = TaskState.Idle  // direct set: dismiss is always allowed
        viewModelScope.launch { repository.clearTaskPlan() }
    }

    /**
     * Builds a context-compressed history snapshot for orchestrator swarm agents.
     *
     * Equivalent to what [SimpleAgent.run] does internally before each LLM call, but operating
     * on a copy of the history so the live [agent] history is not mutated. This ensures that
     * the window trimming and any strategy-level compression (summary generation, etc.) are
     * applied to the history sent to PLANNER/CRITIC/EXECUTOR/VALIDATOR/JUDGE — just as they
     * would be in the direct-answer path.
     *
     * Must be called inside [agentMutex].
     */
    private suspend fun orchestratorHistory(): List<Message> {
        val snapshot = agent.historySnapshot().toMutableList()
        agent.strategy.prepareContext(snapshot)
        val result = agent.strategy.buildMessages(snapshot)
        android.util.Log.d("OrchestratorCtx",
            "strategy=${agent.strategy.type} | " +
            "raw=${agent.historySnapshot().size} msgs → trimmed=${snapshot.size} | " +
            "sent to swarm=${result.size} msgs | " +
            "prefixes=${result.takeWhile { it.role == "system" }.map { it.content?.take(40)?.replace("\n"," ") }}"
        )
        return result
    }

    private fun updateAgentConfig() {
        val thinking = _thinkingEnabled.value
        agent.updateConfig(
            SimpleAgent.AgentConfig(
                model           = _selectedModel.value,
                maxTokens       = _maxTokensText.value.toIntOrNull(),
                temperature     = if (thinking) null else _temperatureText.value.toDoubleOrNull(),
                thinkingEnabled = thinking,
                reasoningEffort = _reasoningEffort.value.takeIf { thinking }
            )
        )
    }

    private suspend fun runDirectAnswer(userInput: String) {
        when (val result = agent.run(userInput)) {
            is SimpleAgent.AgentResult.Success -> {
                val branchId = _activeBranchId.value
                val lastId = chat.saveTurn(result.turnMessages.toDomainMessages(result), branchId)
                _chatHistory.value = (_chatHistory.value + ChatTurn(
                    userMessage   = userInput,
                    agentResponse = result.response,
                    tokenInfo     = result.tokenInfo,
                    cost          = result.cost,
                    durationSec   = result.durationSec,
                    lastMessageId = lastId
                )).toImmutableList()
                _shortTermCount.value = agent.historySnapshot().size
                _uiState.value = UiState.Idle
                // afterTurn (fact/memory extraction) runs off the critical path so the user
                // can type their next message while extraction happens in the background.
                val historySnapshot = agent.historySnapshot().toMutableList()
                val currentStrategy = agent.strategy
                viewModelScope.launch { currentStrategy.afterTurn(historySnapshot) }
            }
            is SimpleAgent.AgentResult.Failure -> {
                _uiState.value = UiState.Error(result.error, isContextOverflow = result.isContextOverflow)
            }
        }
    }

    private suspend fun persistOrchestratorResult(userInput: String, finalAnswer: String) {
        val userMsg      = Message(role = "user",      content = userInput)
        val assistantMsg = Message(role = "assistant", content = finalAnswer)
        agent.appendMessages(listOf(userMsg, assistantMsg))
        val branchId = _activeBranchId.value
        val lastId = chat.saveTurn(listOf(userMsg, assistantMsg).toDomainMessagesSimple(), branchId)
        _chatHistory.value = (_chatHistory.value + ChatTurn(
            userMessage   = userInput,
            agentResponse = finalAnswer,
            tokenInfo     = null,
            cost          = null,
            durationSec   = null,
            lastMessageId = lastId
        )).toImmutableList()
        _shortTermCount.value = agent.historySnapshot().size
    }

    // ── Branch management ──────────────────────────────────────────────────────

    /**
     * Creates a new branch forked from the current branch and immediately switches to it.
     *
     * Guards against creating a branch while a request is loading — if [UiState.Loading]
     * is active the call is silently ignored to avoid race conditions with the agent.
     *
     * @param name User-visible label for the new branch.
     * @param checkpointMessageId The [ChatTurn.lastMessageId] at the fork point.
     *   Messages in the parent branch after this id will not be visible in the new branch.
     */
    fun createBranch(name: String, checkpointMessageId: Long?) {
        if (_uiState.value is UiState.Loading) return
        viewModelScope.launch {
            val parentId = _activeBranchId.value
            val newBranchId = chat.createBranch(name, parentId, checkpointMessageId)
            _branches.value = chat.getBranches()
            switchBranch(newBranchId)
        }
    }

    /**
     * Switches the active branch to [branchId].
     *
     * Acquires [agentMutex] before touching agent state. Reconstructs the full linear history
     * from the branch tree, reloads it into the agent, and replaces [chatHistory] for the UI.
     */
    fun switchBranch(branchId: Long) {
        viewModelScope.launch {
            agentMutex.withLock {
                _activeBranchId.value = branchId
                val fullHistory = reconstructBranchHistory(branchId)
                agent.loadHistory(fullHistory.map { gson.fromJson(it.messageJson, Message::class.java) })
                _chatHistory.value = fullHistory.toChatTurns()
            }
        }
    }

    /**
     * Walks the branch ancestry tree from [startBranchId] up to the root, then folds the
     * segments back down to produce a flat, ordered list of messages.
     *
     * Uses an iterative approach (not recursive) to avoid [StackOverflowError] on deep trees.
     *
     * At each segment boundary the checkpoint cut-off is applied: only messages from the parent
     * branch with id ≤ [Branch.checkpointMessageId] are included.
     */
    private suspend fun reconstructBranchHistory(startBranchId: Long): List<ChatMessage> {
        data class Segment(val messages: List<ChatMessage>, val checkpointMessageId: Long?)

        val chain = mutableListOf<Segment>()
        val visited = mutableSetOf<Long>()
        var currentId: Long? = startBranchId
        while (currentId != null) {
            if (!visited.add(currentId)) break  // cycle guard: stop if we've seen this id before
            val branch = repository.getBranch(currentId) ?: break
            chain.add(Segment(repository.getMessagesForBranch(currentId), branch.checkpointMessageId))
            currentId = branch.parentBranchId
        }

        chain.reverse() // root first, leaf last

        var result = emptyList<ChatMessage>()
        for (segment in chain) {
            val base = if (segment.checkpointMessageId != null) {
                result.filter { it.id <= segment.checkpointMessageId }
            } else {
                result
            }
            result = base + segment.messages
        }
        return result
    }

    // ── New session ────────────────────────────────────────────────────────────

    /**
     * Clears all persisted chat data and resets the agent and UI to the initial empty state.
     *
     * Does not modify the active strategy settings — those are preserved in [prefs].
     * Called only after the user confirms the reset dialog.
     * Note: long-term memory is NOT cleared on newSession() — it persists across sessions.
     */
    fun newSession() {
        viewModelScope.launch {
            chat.clearHistory()
            chat.clearSummary()
            chat.clearFacts()
            chat.clearWorkingMemory()
            repository.resetBranches()
            repository.clearTaskPlan()

            agentMutex.withLock { agent.reset() }
            _chatHistory.value    = persistentListOf()
            _auxData.value        = null
            _activeBranchId.value = MAIN_BRANCH_ID
            _taskState.value      = TaskState.Idle
            _uiState.value        = UiState.Idle
            _shortTermCount.value = 0

            // resetBranches() preserves branch id=1 (only deletes id != 1).
            // Reload so the BranchBar shows the root branch immediately after reset.
            _branches.value = if (_currentStrategyType.value == StrategyType.BRANCHING) {
                chat.getBranches()
            } else {
                emptyList()
            }
        }
    }

    /** Transitions [uiState] from [UiState.Error] back to [UiState.Idle]. */
    fun dismissError() { _uiState.value = UiState.Idle }

    // ── Restore history ────────────────────────────────────────────────────────

    /**
     * Loads persisted messages and restores the agent's history and UI on session resume.
     *
     * For Branching, history is reconstructed from the branch tree.
     * For trimming strategies (SlidingWindow, Summary, StickyFacts, MemoryLayers), only the last
     * [keepLastN] messages are loaded into the agent — older messages were already compressed or
     * discarded by the strategy before they were written to the DB.
     * For NONE strategy the full flat history is loaded.
     */
    private suspend fun restoreHistory(branchId: Long) {
        val all = if (_currentStrategyType.value == StrategyType.BRANCHING) {
            reconstructBranchHistory(branchId)
        } else {
            chat.loadHistory()
        }

        val saved = when (_currentStrategyType.value) {
            StrategyType.SLIDING_WINDOW,
            StrategyType.SUMMARY,
            StrategyType.STICKY_FACTS,
            StrategyType.MEMORY_LAYERS -> all.takeLast(_keepLastN.value)
            else                       -> all
        }

        if (saved.isEmpty()) return
        agent.loadHistory(saved.map { gson.fromJson(it.messageJson, Message::class.java) })
        _chatHistory.value = saved.toChatTurns()
        _shortTermCount.value = saved.size
    }

    // ── Memory layers management ───────────────────────────────────────────────

    /** Adds a new entry to long-term memory and refreshes the strategy's in-memory copy. */
    fun addLongTermMemory(category: String, keyName: String, value: String) {
        viewModelScope.launch {
            chat.addLongTermMemory(category, keyName, value)
            refreshLongTermMemories()
        }
    }

    /** Deletes a long-term memory entry by id and refreshes the strategy's in-memory copy. */
    fun deleteLongTermMemory(id: Long) {
        viewModelScope.launch {
            chat.deleteLongTermMemory(id)
            refreshLongTermMemories()
        }
    }

    /**
     * Calls the LLM to extract long-term facts from the current conversation history,
     * persists them, and refreshes the strategy's in-memory long-term memory text.
     * Only works when MEMORY_LAYERS strategy is active.
     */
    fun extractLongTermFromHistory() {
        viewModelScope.launch {
            agentMutex.withLock {
                val strategy = agent.strategy as? MemoryLayersStrategy ?: return@withLock
                val history = agent.historySnapshot()
                if (history.isNotEmpty()) {
                    strategy.extractLongTermFacts(history)?.forEach { entry ->
                        chat.addLongTermMemory(entry.category, entry.keyName, entry.value)
                    }
                    refreshLongTermMemories()
                }
            }
        }
    }

    private suspend fun refreshLongTermMemories() {
        val ltm = chat.getLongTermMemory()
        _longTermMemories.value = ltm
        (agent.strategy as? MemoryLayersStrategy)?.setLongTermMemory(formatLtmForPrompt(ltm))
    }

    private fun formatLtmForPrompt(entries: List<LongTermMemoryEntry>): String? {
        if (entries.isEmpty()) return null
        return entries.groupBy { it.category }
            .entries.joinToString("\n\n") { (cat, items) ->
                "== $cat ==\n" + items.joinToString("\n") { "${it.keyName}: ${it.value}" }
            }
    }

    // ── Profile management ─────────────────────────────────────────────────────

    /**
     * Switches the active profile. Pass null to clear (no active profile).
     * Resets working memory on switch (new profile = new context), keeps history and long-term memory.
     */
    fun switchProfile(profileId: Long?) {
        viewModelScope.launch {
            val profile = profileId?.let { repository.getProfile(it) }
            prefs.edit().apply {
                if (profile != null) putLong(KEY_ACTIVE_PROFILE, profile.id)
                else remove(KEY_ACTIVE_PROFILE)
            }.apply()

            agentMutex.withLock {
                agent.systemProfileInstructions = profile?.instructions
                chat.clearWorkingMemory()
                (agent.strategy as? MemoryLayersStrategy)?.restoreWorkingMemory(null)
                _auxData.value = null
            }
            _activeProfile.value = profile
        }
    }

    /** Creates a new profile or updates an existing one. Pass null [id] to create. */
    fun saveProfile(id: Long?, name: String, instructions: String) {
        viewModelScope.launch {
            val savedId = chat.saveProfile(id, name, instructions)
            _profiles.value = chat.getProfiles()
            // If editing the active profile — update instructions in agent immediately
            if (_activeProfile.value?.id == savedId) {
                val updated = repository.getProfile(savedId)
                _activeProfile.value = updated
                agentMutex.withLock { agent.systemProfileInstructions = updated?.instructions }
            }
        }
    }

    /** Deletes a profile. If it was active — clears the active profile. */
    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            chat.deleteProfile(id)
            _profiles.value = chat.getProfiles()
            if (_activeProfile.value?.id == id) {
                prefs.edit().remove(KEY_ACTIVE_PROFILE).apply()
                _activeProfile.value = null
                agentMutex.withLock { agent.systemProfileInstructions = null }
            }
        }
    }

    // ── Mapping helpers ────────────────────────────────────────────────────────

    /**
     * Converts the raw [Message] list from a successful agent turn into [ChatMessage] domain objects.
     *
     * All messages in the turn share a single [ChatMessage.turnId] from [turnIdSource] so they can
     * be grouped back into a [ChatTurn] on restore. Token stats and cost are set only on the last
     * assistant message; timestamps are offset by index to preserve intra-turn ordering in the DB.
     */
    private fun List<Message>.toDomainMessages(
        result: SimpleAgent.AgentResult.Success
    ): List<ChatMessage> {
        val turnId = turnIdSource.incrementAndGet()
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

    /** Converts a raw [Message] list to [ChatMessage] objects without token stats (orchestrator path). */
    private fun List<Message>.toDomainMessagesSimple(): List<ChatMessage> {
        val turnId = turnIdSource.incrementAndGet()
        return mapIndexed { index, message ->
            ChatMessage(
                turnId           = turnId,
                role             = message.role,
                messageJson      = gson.toJson(message),
                displayText      = message.content,
                promptTokens     = null,
                completionTokens = null,
                totalTokens      = null,
                cachedTokens     = null,
                cost             = null,
                durationSec      = null,
                timestamp        = System.currentTimeMillis() + index
            )
        }
    }

    /**
     * Groups a flat list of [ChatMessage]s by [ChatMessage.turnId] and converts each group
     * into a [ChatTurn] for display.
     *
     * Groups without both a user and an assistant message are silently skipped (they represent
     * incomplete turns or pure tool messages that have no visible bubble).
     */
    private fun List<ChatMessage>.toChatTurns(): ImmutableList<ChatTurn> =
        groupBy { it.turnId }
            .values
            .sortedBy { group -> group.minOf { it.timestamp } }
            .takeLast(MAX_DISPLAY_TURNS)
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
            }.toImmutableList()
}
