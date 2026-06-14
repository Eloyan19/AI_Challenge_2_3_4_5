package com.example.petapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_summary")
data class SummaryEntity(
    @PrimaryKey val id: Int = 1,
    val content: String,
    val timestamp: Long
)
