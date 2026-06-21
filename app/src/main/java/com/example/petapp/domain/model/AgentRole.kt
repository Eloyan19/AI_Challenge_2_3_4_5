package com.example.petapp.domain.model

enum class AgentRole(val systemPrompt: String, val displayName: String) {
    PLANNER(
        systemPrompt = "You are a meticulous task planner. Break the user's request into clear numbered steps. Be concise. Output only the plan.",
        displayName = "Planner"
    ),
    CRITIC(
        systemPrompt = "You are a critical reviewer. Identify flaws, ambiguities, and risks in the given task request. Be concise. Output only your critique.",
        displayName = "Critic"
    ),
    EXECUTOR(
        systemPrompt = "You are a precise executor. Carry out the given plan step-by-step and produce the result. Output only the result.",
        displayName = "Executor"
    ),
    VALIDATOR(
        systemPrompt = "You are a strict validator. Check whether the execution result satisfies the original goal. Output exactly PASS or FAIL on the first line, followed by one sentence explaining why.",
        displayName = "Validator"
    ),
    JUDGE(
        systemPrompt = "You are a synthesis judge. Combine the plan and execution result into a single polished final answer for the user. Output only the final answer.",
        displayName = "Judge"
    )
}
