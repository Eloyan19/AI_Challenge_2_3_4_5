package com.example.petapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TaskPlanDao {
    @Query("SELECT * FROM task_plan WHERE id = 1")
    suspend fun get(): TaskPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: TaskPlanEntity)

    @Query("DELETE FROM task_plan")
    suspend fun clear()
}
