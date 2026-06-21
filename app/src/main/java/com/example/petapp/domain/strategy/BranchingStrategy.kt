package com.example.petapp.domain.strategy

import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.StrategyType

/**
 * No-op strategy used when [StrategyType.BRANCHING] is active.
 *
 * Branch management is driven entirely by [com.example.petapp.ui.MainViewModel]:
 * it decides which branch is active, reconstructs linear history from the branch tree,
 * and persists new messages to the correct branch.
 * From the agent's perspective, history is always linear — so this strategy is a no-op.
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
