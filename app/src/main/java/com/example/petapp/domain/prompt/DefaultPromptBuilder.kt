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
            add(Message(role = "system", content = role.systemPrompt))
            userProfileInstructions?.let {
                add(Message(role = "system", content = "=== ИНСТРУКЦИИ ПРОФИЛЯ ===\n$it"))
            }
            stateContext(taskState)?.let {
                add(Message(role = "system", content = it))
            }
        }
        return LlmRequest(
            messages        = systemMessages + compressedHistory + listOf(Message(role = "user", content = userInput)),
            model           = model,
            maxTokens       = 1024,
            temperature     = 0.7,
            tools           = null,
            toolChoice      = null,
            thinkingEnabled = false,
            reasoningEffort = null
        )
    }

    private fun stateContext(state: TaskState): String? = when (state) {
        is TaskState.Planning   ->
            "Составь подробный пронумерованный план для выполнения задачи пользователя. Выводи только план."
        is TaskState.Execution  ->
            "=== УТВЕРЖДЁННЫЙ ПЛАН ===\n${state.plan}\n\nВыполни каждый шаг плана последовательно. Выводи только результат."
        is TaskState.Validation ->
            "=== ПЛАН ===\n${state.plan}\n\n=== РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ ===\n${state.executionResult}\n\nПроверь соответствие результата поставленной задаче."
        is TaskState.Replanning ->
            "=== ПРЕДЫДУЩИЙ ПЛАН ОТКЛОНЁН ===\nПричина: ${state.reason.ifBlank { "пользователь отклонил план" }}\n\nСоставь улучшенный план. Выводи только план."
        else                    -> null
    }
}
