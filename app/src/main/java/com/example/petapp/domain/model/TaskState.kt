package com.example.petapp.domain.model

/**
 * Represents the state of the agentic task lifecycle.
 *
 * **Intentional dual role:** each subclass serves both as the UI state machine driver
 * (determining which composable renders in [com.example.petapp.ui.screens.TaskStateCard])
 * AND as the orchestration context carrier — payload fields (`plan`, `executionResult`, `reason`)
 * are injected into LLM prompts via [com.example.petapp.domain.prompt.DefaultPromptBuilder.stateContext].
 * This is deliberate: the data that drives the UI display (e.g. showing the plan text) is exactly
 * the same data that the next LLM call needs — no separate PromptContext class is needed.
 */
sealed class TaskState {
    object Idle : TaskState()
    data class Analyzing(val userInput: String) : TaskState()
    data class Planning(val userInput: String) : TaskState()
    data class AwaitingInput(
        val userInput: String,
        val plan: String,
        val critique: String? = null
    ) : TaskState()
    data class Execution(val userInput: String, val plan: String) : TaskState()
    data class Validation(val userInput: String, val plan: String, val executionResult: String) : TaskState()
    data class ValidationFailed(
        val userInput: String,
        val plan: String,
        val executionResult: String,
        val reason: String,
        val critique: String? = null
    ) : TaskState()
    data class Done(val finalResult: String) : TaskState()
    data class Replanning(val userInput: String, val previousPlan: String, val reason: String) : TaskState()
    data class Error(val message: String) : TaskState()
}
