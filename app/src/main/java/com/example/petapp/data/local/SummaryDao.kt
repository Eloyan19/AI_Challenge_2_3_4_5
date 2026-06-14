package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SummaryDao {

    @Query("SELECT * FROM conversation_summary WHERE id = 1")
    suspend fun get(): SummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: SummaryEntity)

    @Query("DELETE FROM conversation_summary")
    suspend fun clear()
}
