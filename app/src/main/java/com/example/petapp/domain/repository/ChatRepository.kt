package com.example.petapp.domain.repository

import com.example.petapp.domain.model.Branch
import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.model.LongTermMemoryEntry
import com.example.petapp.domain.model.TaskPlanData
import com.example.petapp.domain.model.UserProfile

/**
 * Persistence contract for all chat data.
 *
 * Grouped into five sections matching the active context strategies:
 * - **Linear history** — used by all strategies except Branching.
 * - **Summary** — auxiliary text for [com.example.petapp.domain.model.StrategyType.SUMMARY].
 * - **Sticky facts** — auxiliary text for [com.example.petapp.domain.model.StrategyType.STICKY_FACTS].
 * - **Branches** — tree of conversation forks for [com.example.petapp.domain.model.StrategyType.BRANCHING].
 * - **Memory layers** — working and long-term memory for [com.example.petapp.domain.model.StrategyType.MEMORY_LAYERS].
 */
interface ChatRepository {

    // ── Linear history ──────────────────────────────────────────────────────────

    /** Returns all persisted messages across all branches, ordered by timestamp ascending. */
    suspend fun getAllMessages(): List<ChatMessage>

    /** Persists [messages] to the database and returns the auto-generated ID of the last inserted row. */
    suspend fun saveMessages(messages: List<ChatMessage>): Long?

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

    // ── Working memory (MEMORY_LAYERS strategy) ────────────────────────────────

    /** Returns the current working memory text, or null if not set. */
    suspend fun getWorkingMemory(): String?

    /** Overwrites the working memory singleton row with [content]. */
    suspend fun saveWorkingMemory(content: String)

    /** Deletes the working memory row. */
    suspend fun clearWorkingMemory()

    // ── Long-term memory (MEMORY_LAYERS strategy) ──────────────────────────────

    /** Returns all long-term memory entries ordered by creation time ascending. */
    suspend fun getLongTermMemory(): List<LongTermMemoryEntry>

    /**
     * Inserts a new long-term memory entry.
     * @return The auto-generated id of the new entry.
     */
    suspend fun addLongTermMemory(category: String, keyName: String, value: String): Long

    /** Deletes the long-term memory entry with the given [id]. */
    suspend fun deleteLongTermMemory(id: Long)

    // ── User profiles ──────────────────────────────────────────────────────────

    /** Returns all saved user profiles ordered by creation time. */
    suspend fun getProfiles(): List<UserProfile>

    /** Returns the profile with the given [id], or null if not found. */
    suspend fun getProfile(id: Long): UserProfile?

    /**
     * Creates a new profile if [id] is null; updates the existing one otherwise.
     * @return The id of the created or updated profile.
     */
    suspend fun saveProfile(id: Long?, name: String, instructions: String): Long

    /** Deletes the profile with the given [id]. */
    suspend fun deleteProfile(id: Long)

    // ── Task plan (Task State Machine) ─────────────────────────────────────────

    /** Returns the pending task plan, or null if none is awaiting confirmation. */
    suspend fun getTaskPlan(): TaskPlanData?

    /** Persists the pending plan so it survives process death. */
    suspend fun saveTaskPlan(userInput: String, plan: String, critique: String?)

    /** Clears the pending plan (called on confirm, reject-fail, dismiss, or new session). */
    suspend fun clearTaskPlan()
}
