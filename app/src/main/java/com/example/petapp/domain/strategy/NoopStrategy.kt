package com.example.petapp.domain.strategy

import com.example.petapp.data.Message
import com.example.petapp.domain.model.StrategyType

class NoopStrategy : ContextStrategy {
    override val type = StrategyType.NONE
    override suspend fun prepareContext(history: MutableList<Message>) = Unit
    override fun buildMessages(history: List<Message>) = history
    override suspend fun afterTurn(history: List<Message>) = Unit
    override val auxData: String? = null
    override var onAuxDataUpdated: ((String?) -> Unit)? = null
    override fun reset() = Unit
}
