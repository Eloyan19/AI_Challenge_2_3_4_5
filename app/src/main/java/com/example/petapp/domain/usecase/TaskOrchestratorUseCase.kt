package com.example.petapp.domain.usecase

import com.example.petapp.domain.model.AgentRole
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.TaskState

class TaskOrchestratorUseCase(
    private val detectComplexity: DetectComplexityUseCase,
    private val runSwarm: RunAgentSwarmUseCase,
    @Suppress("UNUSED_PARAMETER") providerConfig: LlmProviderConfig
) {
    sealed class OrchestratorResult {
        object Simple : OrchestratorResult()
        data class PlanReady(val plan: String, val critique: String?) : OrchestratorResult()
        data class ExecutionDone(val finalAnswer: String) : OrchestratorResult()
        data class ValidationFailed(val reason: String) : OrchestratorResult()
        data class Failed(val error: String) : OrchestratorResult()
    }

    suspend fun detectAndPlan(
        userInput: String,
        compressedHistory: List<Message>,
        userProfileInstructions: String?,
        model: String,
        guardrailsInstruction: String? = null
    ): OrchestratorResult {
        if (!detectComplexity(userInput)) return OrchestratorResult.Simple

        val results = runSwarm(
            roles                   = listOf(AgentRole.PLANNER, AgentRole.CRITIC),
            userInput               = userInput,
            taskState               = TaskState.Planning(userInput),
            compressedHistory       = compressedHistory,
            userProfileInstructions = userProfileInstructions,
            model                   = model,
            guardrailsInstruction   = guardrailsInstruction
        )

        val planOutput = results[AgentRole.PLANNER]?.getOrNull()
            ?: return OrchestratorResult.Failed("Не удалось сгенерировать план")

        return OrchestratorResult.PlanReady(
            plan     = planOutput.content,
            critique = results[AgentRole.CRITIC]?.getOrNull()?.content
        )
    }

    suspend fun executeAndValidate(
        userInput: String,
        plan: String,
        compressedHistory: List<Message>,
        userProfileInstructions: String?,
        model: String,
        guardrailsInstruction: String? = null,
        onValidating: suspend (executionResult: String) -> Unit = {}
    ): OrchestratorResult {
        val execResults = runSwarm(
            roles                   = listOf(AgentRole.EXECUTOR),
            userInput               = userInput,
            taskState               = TaskState.Execution(userInput, plan),
            compressedHistory       = compressedHistory,
            userProfileInstructions = userProfileInstructions,
            model                   = model,
            guardrailsInstruction   = guardrailsInstruction
        )

        val executionResult = execResults[AgentRole.EXECUTOR]?.getOrNull()
            ?: return OrchestratorResult.Failed("Не удалось выполнить план")

        onValidating(executionResult.content)

        val validationState = TaskState.Validation(userInput, plan, executionResult.content)
        val validationResults = runSwarm(
            roles                   = listOf(AgentRole.VALIDATOR),
            userInput               = userInput,
            taskState               = validationState,
            compressedHistory       = compressedHistory,
            userProfileInstructions = userProfileInstructions,
            model                   = model,
            guardrailsInstruction   = guardrailsInstruction
        )

        val validatorOutput = validationResults[AgentRole.VALIDATOR]?.getOrNull()
            ?: return OrchestratorResult.Failed("Не удалось провести валидацию")

        if (!validatorOutput.content.startsWith("PASS", ignoreCase = true)) {
            val firstLine = validatorOutput.content.lines().firstOrNull().orEmpty()
            val rest = validatorOutput.content.removePrefix(firstLine).trimStart('\n', '\r')
            val reason = rest.ifBlank {
                firstLine.substringAfter(" ", missingDelimiterValue = "").trim()
            }.ifBlank { firstLine.trim() }
            return OrchestratorResult.ValidationFailed(reason)
        }

        val judgeResults = runSwarm(
            roles                   = listOf(AgentRole.JUDGE),
            userInput               = userInput,
            taskState               = validationState,
            compressedHistory       = compressedHistory,
            userProfileInstructions = userProfileInstructions,
            model                   = model,
            guardrailsInstruction   = guardrailsInstruction
        )

        val finalAnswer = judgeResults[AgentRole.JUDGE]?.getOrNull()?.content
            ?: executionResult.content

        return OrchestratorResult.ExecutionDone(finalAnswer)
    }

    suspend fun replan(
        userInput: String,
        previousPlan: String,
        rejectionReason: String,
        compressedHistory: List<Message>,
        userProfileInstructions: String?,
        model: String,
        guardrailsInstruction: String? = null
    ): OrchestratorResult {
        val results = runSwarm(
            roles                   = listOf(AgentRole.PLANNER, AgentRole.CRITIC),
            userInput               = userInput,
            taskState               = TaskState.Replanning(userInput, previousPlan, rejectionReason),
            compressedHistory       = compressedHistory,
            userProfileInstructions = userProfileInstructions,
            model                   = model,
            guardrailsInstruction   = guardrailsInstruction
        )

        val planOutput = results[AgentRole.PLANNER]?.getOrNull()
            ?: return OrchestratorResult.Failed("Не удалось перепланировать")

        return OrchestratorResult.PlanReady(
            plan     = planOutput.content,
            critique = results[AgentRole.CRITIC]?.getOrNull()?.content
        )
    }
}
