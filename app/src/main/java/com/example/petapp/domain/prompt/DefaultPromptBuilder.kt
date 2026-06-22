package com.example.petapp.domain.prompt

import com.example.petapp.domain.model.AgentRole
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.TaskState
import javax.inject.Inject

class DefaultPromptBuilder @Inject constructor() : PromptBuilder {

    override fun build(
        role: AgentRole,
        userInput: String,
        taskState: TaskState,
        compressedHistory: List<Message>,
        userProfileInstructions: String?,
        model: String,
        guardrailsInstruction: String?
    ): LlmRequest {
        val systemMessages = buildList {
            guardrailsInstruction?.let { add(Message(role = "system", content = it)) }
            userProfileInstructions?.let {
                add(Message(role = "system", content = "=== ИНСТРУКЦИИ ПРОФИЛЯ ===\n$it"))
            }
            add(Message(role = "system", content = role.systemPrompt))
            stateContext(taskState)?.let {
                add(Message(role = "system", content = it))
            }
        }
        return LlmRequest(
            messages        = systemMessages + compressedHistory + listOf(Message(role = "user", content = userInput)),
            model           = model,
            maxTokens       = maxTokensFor(role),
            temperature     = 0.7,
            tools           = null,
            toolChoice      = null,
            thinkingEnabled = false,
            reasoningEffort = null
        )
    }

    private fun maxTokensFor(role: AgentRole): Int = when (role) {
        AgentRole.EXECUTOR  -> 4096  // may need to write long code or detailed content
        AgentRole.JUDGE     -> 2048  // final synthesis shown to the user
        AgentRole.PLANNER   -> 1024
        AgentRole.CRITIC    -> 512
        AgentRole.VALIDATOR -> 256   // only PASS/FAIL + one sentence
    }

    private fun stateContext(state: TaskState): String? = when (state) {
        is TaskState.Planning   ->
            "Составь подробный пронумерованный план для выполнения задачи пользователя. Выводи только план."
        is TaskState.Execution  ->
            "=== УТВЕРЖДЁННЫЙ ПЛАН ===\n${state.plan}\n\nВыполни каждый шаг плана последовательно. Выводи только результат."
        is TaskState.Validation ->
            // Provides plan + execution result as data context for both VALIDATOR and JUDGE.
            // Each agent's role prompt contains the specific instruction (check vs. synthesise).
            "=== ПЛАН ===\n${state.plan}\n\n=== РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ ===\n${state.executionResult}"
        is TaskState.Replanning ->
            "=== ПРЕДЫДУЩИЙ ПЛАН ОТКЛОНЁН ===\nПричина: ${state.reason.ifBlank { "пользователь отклонил план" }}\n\nСоставь улучшенный план. Выводи только план."
        else                    -> null
    }
}
