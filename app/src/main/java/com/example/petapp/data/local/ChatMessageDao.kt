package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE branch_id = :branchId ORDER BY timestamp ASC")
    suspend fun getByBranch(branchId: Long): List<ChatMessageEntity>

    /** Messages in [branchId] whose DB id is ≤ [upToId] — used for branch reconstruction. */
    @Query("SELECT * FROM chat_messages WHERE branch_id = :branchId AND id <= :upToId ORDER BY timestamp ASC")
    suspend fun getByBranchUpTo(branchId: Long, upToId: Long): List<ChatMessageEntity>

    @Query("SELECT MAX(id) FROM chat_messages WHERE branch_id = :branchId")
    suspend fun getLastIdForBranch(branchId: Long): Long?

    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    @Query("DELETE FROM chat_messages WHERE branch_id = :branchId")
    suspend fun clearBranch(branchId: Long)

    @Query("DELETE FROM chat_messages WHERE branch_id != 1")
    suspend fun clearNonMainBranches()
}
