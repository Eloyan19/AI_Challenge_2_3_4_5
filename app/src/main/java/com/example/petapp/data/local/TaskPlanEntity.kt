package com.example.petapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_plan")
data class TaskPlanEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "user_input") val userInput: String,
    val plan: String,
    val critique: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
