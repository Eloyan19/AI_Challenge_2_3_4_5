package com.example.petapp.domain.model

data class SwarmAgentOutput(
    val role: AgentRole,
    val content: String,
    val durationMs: Long
)
