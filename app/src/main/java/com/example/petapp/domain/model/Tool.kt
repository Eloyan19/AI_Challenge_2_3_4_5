package com.example.petapp.domain.model

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * A function tool made available to the LLM.
 * Follows the OpenAI function-calling format supported by most providers.
 */
data class Tool(
    val type: String = "function",
    val function: ToolFunction
)

/** Metadata and JSON-Schema parameters for a single tool function. */
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/** A tool invocation requested by the model in an assistant message. */
data class ToolCall(
    val id: String,
    val type: String,
    val function: ToolCallFunction
)

/** Name and JSON-encoded arguments of a specific tool call. */
data class ToolCallFunction(
    val name: String,
    val arguments: String
)
