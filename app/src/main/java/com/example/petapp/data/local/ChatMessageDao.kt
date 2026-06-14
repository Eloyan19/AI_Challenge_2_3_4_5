package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room DAO for the `chat_messages` table.
 *
 * All queries are suspend functions and must be called from a coroutine.
 */
@Dao
interface ChatMessageDao {

    /** Returns all messages across all branches, ordered by timestamp ascending. */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    /** Returns messages belonging to [branchId], ordered by timestamp ascending. */
    @Query("SELECT * FROM chat_messages WHERE branch_id = :branchId ORDER BY timestamp ASC")
    suspend fun getByBranch(branchId: Long): List<ChatMessageEntity>

    /**
     * Returns messages in [branchId] whose DB id is ≤ [upToId].
     * Used during branch history reconstruction to apply a checkpoint cut-off.
     */
    @Query("SELECT * FROM chat_messages WHERE branch_id = :branchId AND id <= :upToId ORDER BY timestamp ASC")
    suspend fun getByBranchUpTo(branchId: Long, upToId: Long): List<ChatMessageEntity>

    /** Returns the highest id in [branchId], or null if the branch has no messages. */
    @Query("SELECT MAX(id) FROM chat_messages WHERE branch_id = :branchId")
    suspend fun getLastIdForBranch(branchId: Long): Long?

    /** Inserts all [messages]; existing rows with the same id are not replaced. */
    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    /** Deletes every row in the table. */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    /** Deletes all messages belonging to [branchId]. */
    @Query("DELETE FROM chat_messages WHERE branch_id = :branchId")
    suspend fun clearBranch(branchId: Long)

    /** Deletes messages from all branches except the main branch (id = 1). */
    @Query("DELETE FROM chat_messages WHERE branch_id != 1")
    suspend fun clearNonMainBranches()
}
