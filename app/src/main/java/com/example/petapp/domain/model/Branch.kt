package com.example.petapp.domain.model

data class Branch(
    val id: Long,
    val name: String,
    val parentBranchId: Long?,
    val checkpointMessageId: Long?,
    val createdAt: Long
)
