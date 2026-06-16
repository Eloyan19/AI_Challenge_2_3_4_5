package com.example.petapp.domain.model

/**
 * Identifies which context-management strategy the agent is using.
 *
 * @property displayName Human-readable label shown in the UI.
 */
enum class StrategyType(val displayName: String) {
    /** Full history is sent on every request — no trimming. */
    NONE("Без сжатия"),

    /** Only the last N messages are kept; older messages are discarded permanently. */
    SLIDING_WINDOW("Скользящее окно"),

    /** Older messages are replaced by an LLM-generated summary that grows incrementally. */
    SUMMARY("LLM-пересказ"),

    /**
     * After every turn the LLM extracts up to 10 key facts from the conversation.
     * Those facts are prepended as a system message on every subsequent request.
     */
    STICKY_FACTS("Sticky Facts"),

    /**
     * The user can fork the conversation at any checkpoint and explore independent
     * branches without context compression.
     */
    BRANCHING("Ветвление"),

    /**
     * 3-layer memory: short-term (live window), working (current task, cleared on reset),
     * long-term (profile/knowledge/decisions, persists across sessions).
     */
    MEMORY_LAYERS("Слои памяти")
}
