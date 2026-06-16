package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LongTermMemoryDao {
    @Query("SELECT * FROM long_term_memory ORDER BY created_at ASC")
    suspend fun getAll(): List<LongTermMemoryEntity>

    @Insert
    suspend fun insert(e: LongTermMemoryEntity): Long

    @Query("DELETE FROM long_term_memory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM long_term_memory")
    suspend fun clearAll()
}
