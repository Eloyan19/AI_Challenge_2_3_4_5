package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkingMemoryDao {
    @Query("SELECT * FROM working_memory WHERE id = 1")
    suspend fun get(): WorkingMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(e: WorkingMemoryEntity)

    @Query("DELETE FROM working_memory")
    suspend fun clear()
}
