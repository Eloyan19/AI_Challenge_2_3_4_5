package com.example.petapp.domain.model

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
        val reason: String
    ) : TaskState()
    data class Done(val finalResult: String) : TaskState()
    data class Replanning(val userInput: String, val previousPlan: String, val reason: String) : TaskState()
    data class Error(val message: String) : TaskState()
}
