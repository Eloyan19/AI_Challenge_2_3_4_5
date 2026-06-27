package com.example.petapp.data

import com.example.petapp.domain.model.Tool
import com.example.petapp.domain.model.ToolCall
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    private val mcpClient: McpClient,
    private val toolExecutor: ToolExecutor,
    private val gson: Gson
) {
    suspend fun allTools(): List<Tool> =
        ToolDefinitions.localTools + mcpClient.listTools()

    suspend fun execute(toolCall: ToolCall): String =
        if (mcpClient.ownsToolName(toolCall.function.name)) {
            val args = gson.fromJson(toolCall.function.arguments, JsonObject::class.java)
            mcpClient.callTool(toolCall.function.name, args)
        } else {
            toolExecutor.execute(toolCall)
        }
}
