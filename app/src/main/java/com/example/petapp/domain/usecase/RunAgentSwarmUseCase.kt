package com.example.petapp.domain.usecase

import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.AgentRole
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.model.SwarmAgentOutput
import com.example.petapp.domain.model.TaskState
import com.example.petapp.domain.prompt.PromptBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class RunAgentSwarmUseCase @Inject constructor(
    private val llmService: LlmService,
    private val promptBuilder: PromptBuilder,
    private val providerConfig: LlmProviderConfig
) {
    suspend operator fun invoke(
        roles: List<AgentRole>,
        userInput: String,
        taskState: TaskState,
        compressedHistory: List<Message>,
        userProfileInstructions: String?,
        model: String,
        guardrailsInstruction: String? = null
    ): Map<AgentRole, Result<SwarmAgentOutput>> = coroutineScope {
        roles.map { role ->
            async {
                role to runCatching {
                    val request = promptBuilder.build(
                        role                    = role,
                        userInput               = userInput,
                        taskState               = taskState,
                        compressedHistory       = compressedHistory,
                        userProfileInstructions = userProfileInstructions,
                        model                   = model,
                        guardrailsInstruction   = guardrailsInstruction
                    )
                    val t0 = System.currentTimeMillis()
                    val response = llmService.chat(request)
                    SwarmAgentOutput(
                        role       = role,
                        content    = response.content ?: "",
                        durationMs = System.currentTimeMillis() - t0
                    )
                }
            }
        }.map { it.await() }.toMap()
    }
}
