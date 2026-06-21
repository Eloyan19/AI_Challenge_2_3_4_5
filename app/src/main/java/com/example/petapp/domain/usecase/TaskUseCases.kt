package com.example.petapp.domain.usecase

import javax.inject.Inject

/**
 * Aggregates orchestrator use cases into a single injectable holder.
 *
 * Dagger resolves the full dependency chain automatically:
 * TaskOrchestratorUseCase → DetectComplexityUseCase + RunAgentSwarmUseCase → LlmService + PromptBuilder + LlmProviderConfig
 */
class TaskUseCases @Inject constructor(
    val orchestrator: TaskOrchestratorUseCase,
)
