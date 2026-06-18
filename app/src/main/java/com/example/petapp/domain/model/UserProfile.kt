package com.example.petapp.domain.model

data class UserProfile(
    val id: Long,
    val name: String,
    val instructions: String,
    val createdAt: Long
)
