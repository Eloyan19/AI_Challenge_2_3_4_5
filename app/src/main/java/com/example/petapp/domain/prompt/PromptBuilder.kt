package com.example.petapp.domain.prompt

import com.example.petapp.domain.model.AgentRole
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.TaskState

interface PromptBuilder {
    fun build(
        role: AgentRole,
        userInput: String,
        taskState: TaskState,
        compressedHistory: List<Message>,
        userProfileInstructions: String?,
        model: String
    ): LlmRequest
}
