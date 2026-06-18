package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profiles ORDER BY created_at ASC")
    suspend fun getAll(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: Long): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UserProfileEntity): Long

    @Update
    suspend fun update(entity: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
