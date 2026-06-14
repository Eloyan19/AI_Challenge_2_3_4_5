package com.example.petapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sticky_facts")
data class StickyFactsEntity(
    @PrimaryKey val id: Int = 1,
    val content: String,
    val updatedAt: Long
)
