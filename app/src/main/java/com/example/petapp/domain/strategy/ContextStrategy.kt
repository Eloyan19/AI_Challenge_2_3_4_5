package com.example.petapp.domain.strategy

import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.StrategyType

interface ContextStrategy {
    val type: StrategyType

    /** Trims [history] in-place if the strategy requires it (called before every API request). */
    suspend fun prepareContext(history: MutableList<Message>)

    /** Wraps [history] into the final list for the API call (may prepend system messages). */
    fun buildMessages(history: List<Message>): List<Message>

    /**
     * Called after a successful agent turn with the updated full history.
     * Used for async post-processing: extracting facts, updating summary, etc.
     */
    suspend fun afterTurn(history: List<Message>)

    /** Auxiliary persisted text (summary content, facts content). Null if not applicable. */
    val auxData: String?

    /** Invoked when [auxData] changes so the ViewModel can persist it to the DB. */
    var onAuxDataUpdated: ((String?) -> Unit)?

    /** Clears all in-memory strategy state (called on new session). */
    fun reset()
}
