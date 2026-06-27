package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class LongTermMemoryDao {
    @Query("SELECT * FROM long_term_memory ORDER BY created_at ASC")
    abstract suspend fun getAll(): List<LongTermMemoryEntity>

    @Insert
    abstract suspend fun insert(e: LongTermMemoryEntity): Long

    @Query("SELECT id FROM long_term_memory WHERE category = :category AND key_name = :keyName LIMIT 1")
    abstract suspend fun findIdByCategoryAndKey(category: String, keyName: String): Long?

    @Query("UPDATE long_term_memory SET value = :value, updated_at = :updatedAt WHERE category = :category AND key_name = :keyName")
    abstract suspend fun updateByCategoryAndKey(category: String, keyName: String, value: String, updatedAt: Long)

    @Transaction
    open suspend fun upsert(e: LongTermMemoryEntity): Long {
        val existingId = findIdByCategoryAndKey(e.category, e.keyName)
        return if (existingId != null) {
            updateByCategoryAndKey(e.category, e.keyName, e.value, e.updatedAt)
            existingId
        } else {
            insert(e)
        }
    }

    @Query("DELETE FROM long_term_memory WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM long_term_memory")
    abstract suspend fun clearAll()
}
