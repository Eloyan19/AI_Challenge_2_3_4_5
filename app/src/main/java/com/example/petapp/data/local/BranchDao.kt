package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY created_at ASC")
    suspend fun getAll(): List<BranchEntity>

    @Query("SELECT * FROM branches WHERE id = :id")
    suspend fun getById(id: Long): BranchEntity?

    @Insert
    suspend fun insert(branch: BranchEntity): Long

    @Query("DELETE FROM branches WHERE id != 1")
    suspend fun deleteAllExceptMain()
}
