package com.example.petapp.domain.strategy

import com.example.petapp.data.Message
import com.example.petapp.domain.model.StrategyType

/**
 * Pass-through strategy that sends the full conversation history on every request.
 *
 * Used when [StrategyType.NONE] is selected. No trimming, no compression, no auxiliary data.
 * Context growth is unbounded — the user is responsible for keeping conversations short
 * enough to fit within the model's context window.
 */
class NoopStrategy : ContextStrategy {
    override val type = StrategyType.NONE
    override suspend fun prepareContext(history: MutableList<Message>) = Unit
    override fun buildMessages(history: List<Message>) = history
    override suspend fun afterTurn(history: List<Message>) = Unit
    override val auxData: String? = null
    override var onAuxDataUpdated: ((String?) -> Unit)? = null
    override fun reset() = Unit
}
