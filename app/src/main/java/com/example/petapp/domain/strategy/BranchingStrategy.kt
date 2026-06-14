package com.example.petapp.domain.strategy

import com.example.petapp.data.Message
import com.example.petapp.domain.model.StrategyType

/**
 * Branch management is driven entirely by the ViewModel (which branch is active,
 * how to reconstruct history from the branch tree, where to persist new messages).
 * From the Agent's perspective, history is always linear — so this strategy is a no-op.
 */
class BranchingStrategy : ContextStrategy {
    override val type = StrategyType.BRANCHING
    override val auxData: String? = null
    override var onAuxDataUpdated: ((String?) -> Unit)? = null
    override suspend fun prepareContext(history: MutableList<Message>) = Unit
    override fun buildMessages(history: List<Message>) = history
    override suspend fun afterTurn(history: List<Message>) = Unit
    override fun reset() = Unit
}
