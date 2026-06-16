package com.example.petapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "working_memory")
data class WorkingMemoryEntity(
    @PrimaryKey val id: Int = 1,
    val content: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
