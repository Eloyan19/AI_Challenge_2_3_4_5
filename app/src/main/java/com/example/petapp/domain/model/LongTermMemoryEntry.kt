package com.example.petapp.domain.model

data class LongTermMemoryEntry(
    val id: Long = 0,
    val category: String,
    val keyName: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long
)
