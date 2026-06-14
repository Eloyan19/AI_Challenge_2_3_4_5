package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StickyFactsDao {
    @Query("SELECT * FROM sticky_facts WHERE id = 1")
    suspend fun get(): StickyFactsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: StickyFactsEntity)

    @Query("DELETE FROM sticky_facts")
    suspend fun clear()
}
