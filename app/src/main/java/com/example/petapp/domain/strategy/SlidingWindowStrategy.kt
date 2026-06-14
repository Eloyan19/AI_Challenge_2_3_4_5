package com.example.petapp.domain.strategy

import com.example.petapp.data.Message
import com.example.petapp.domain.model.StrategyType

class SlidingWindowStrategy(var keepLastN: Int = 10) : ContextStrategy {
    override val type = StrategyType.SLIDING_WINDOW
    override val auxData: String? = null
    override var onAuxDataUpdated: ((String?) -> Unit)? = null

    override suspend fun prepareContext(history: MutableList<Message>) {
        while (history.size > keepLastN) history.removeAt(0)
    }

    override fun buildMessages(history: List<Message>) = history

    override suspend fun afterTurn(history: List<Message>) = Unit

    override fun reset() = Unit
}
