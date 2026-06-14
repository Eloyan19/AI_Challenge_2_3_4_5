package com.example.petapp.domain.repository

import com.example.petapp.domain.model.Branch
import com.example.petapp.domain.model.ChatMessage

/**
 * Persistence contract for all chat data.
 *
 * Grouped into four sections matching the four active context strategies:
 * - **Linear history** — used by all strategies except Branching.
 * - **Summary** — auxiliary text for [com.example.petapp.domain.model.StrategyType.SUMMARY].
 * - **Sticky facts** — auxiliary text for [com.example.petapp.domain.model.StrategyType.STICKY_FACTS].
 * - **Branches** — tree of conversation forks for [com.example.petapp.domain.model.StrategyType.BRANCHING].
 */
interface ChatRepository {

    // ── Linear history ──────────────────────────────────────────────────────────

    /** Returns all persisted messages across all branches, ordered by timestamp ascending. */
    suspend fun getAllMessages(): List<ChatMessage>

    /** Persists [messages] to the database (insert-only; no upsert). */
    suspend fun saveMessages(messages: List<ChatMessage>)

    /** Deletes all messages, the summary, and the sticky facts (does not touch branches). */
    suspend fun clearAll()

    // ── Summary (SUMMARY strategy) ──────────────────────────────────────────────

    /** Returns the persisted summary text, or null if none has been saved yet. */
    suspend fun getSummary(): String?

    /** Overwrites the summary singleton row with [content]. */
    suspend fun saveSummary(content: String)

    /** Deletes the summary row. */
    suspend fun clearSummary()

    // ── Sticky facts (STICKY_FACTS strategy) ───────────────────────────────────

    /** Returns the persisted facts text, or null if none has been saved yet. */
    suspend fun getFacts(): String?

    /** Overwrites the sticky facts singleton row with [content]. */
    suspend fun saveFacts(content: String)

    /** Deletes the sticky facts row. */
    suspend fun clearFacts()

    // ── Branches (BRANCHING strategy) ──────────────────────────────────────────

    /** Returns all branches ordered by their primary key (creation order). */
    suspend fun getAllBranches(): List<Branch>

    /** Returns the branch with the given [id], or null if not found. */
    suspend fun getBranch(id: Long): Branch?

    /**
     * Creates a new branch forked from [parentBranchId].
     *
     * @param name User-visible label.
     * @param parentBranchId Id of the branch being forked.
     * @param checkpointMessageId Last message id from the parent that this branch inherits.
     *   Pass null to inherit the full parent history up to the fork point.
     * @return The auto-generated id of the newly created branch.
     */
    suspend fun createBranch(name: String, parentBranchId: Long, checkpointMessageId: Long?): Long

    /** Returns all messages belonging to [branchId], ordered by timestamp ascending. */
    suspend fun getMessagesForBranch(branchId: Long): List<ChatMessage>

    /**
     * Returns messages in [branchId] with database id ≤ [upToId].
     * Used during branch reconstruction to apply a checkpoint cut-off.
     */
    suspend fun getMessagesForBranchUpTo(branchId: Long, upToId: Long): List<ChatMessage>

    /** Returns the highest message id in [branchId], or null if the branch is empty. */
    suspend fun getLastMessageIdForBranch(branchId: Long): Long?

    /** Deletes all messages that belong to [branchId]. */
    suspend fun clearBranchMessages(branchId: Long)

    /** Deletes all non-main branches and their messages, leaving branch id = 1 intact. */
    suspend fun resetBranches()
}
