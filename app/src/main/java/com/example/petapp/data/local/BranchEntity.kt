package com.example.petapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentBranchId: Long?,
    /** Last message ID from parent branch that this branch inherits (inclusive). */
    val checkpointMessageId: Long?,
    val createdAt: Long
)
