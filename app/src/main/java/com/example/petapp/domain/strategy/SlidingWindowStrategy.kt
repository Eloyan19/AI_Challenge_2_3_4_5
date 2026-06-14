package com.example.petapp.domain.strategy

import com.example.petapp.data.Message
import com.example.petapp.domain.model.StrategyType

/**
 * Keeps only the most recent [keepLastN] messages in the agent's history.
 *
 * Old messages are removed from the front of the list on every [prepareContext] call.
 * This is a destructive operation — dropped messages cannot be recovered.
 * No auxiliary data is maintained, so [auxData] is always null.
 *
 * @param keepLastN Maximum number of messages to retain. Updated when the user
 *   saves new settings in [com.example.petapp.ui.screens.ContextSettingsScreen].
 */
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
