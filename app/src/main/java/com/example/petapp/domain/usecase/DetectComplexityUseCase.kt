package com.example.petapp.domain.usecase

import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.Message
import javax.inject.Inject

class DetectComplexityUseCase @Inject constructor(
    private val llmService: LlmService,
    private val providerConfig: LlmProviderConfig
) {
    suspend operator fun invoke(userInput: String): Boolean =
        heuristicCheck(userInput) ?: llmFallback(userInput)

    /**
     * Fast heuristic pre-filter that avoids an LLM call for unambiguous cases.
     *
     * Returns true/false when the signal is clear; null when the LLM should decide.
     *
     * Covers ~60-70 % of typical chat traffic:
     * - Very short or obviously factual messages → SIMPLE
     * - Long messages or multi-step / generation phrases → COMPLEX
     */
    private fun heuristicCheck(input: String): Boolean? {
        val lower = input.trim().lowercase()
        val wordCount = lower.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

        if (wordCount > 50) return true
        if (COMPLEX_PHRASES.any { lower.contains(it) }) return true

        if (wordCount <= 3) return false
        if (SIMPLE_PREFIXES.any { lower.startsWith(it) }) return false

        return null
    }

    private suspend fun llmFallback(input: String): Boolean = runCatching {
        val request = LlmRequest(
            messages = listOf(
                Message(
                    role    = "system",
                    content = "You classify user requests. Reply with exactly one word: SIMPLE or COMPLEX.\n" +
                              "COMPLEX = requires planning, multi-step execution, creative writing, research, or lengthy generation.\n" +
                              "SIMPLE = factual Q&A, short translation, math, definition, quick lookup."
                ),
                Message(role = "user", content = input)
            ),
            model       = providerConfig.backgroundModel,
            maxTokens   = 10,
            temperature = 0.0
        )
        llmService.chat(request).content?.contains("COMPLEX", ignoreCase = true) == true
    }.getOrDefault(false)

    companion object {
        // Unambiguous multi-step / generation phrases → always COMPLEX
        private val COMPLEX_PHRASES = listOf(
            // Step-by-step indicators (all synonyms)
            "пошагово", "по шагам", "поэтапно", "по пунктам", "последовательно",
            // Plan / list / table creation — all verb forms
            "составь план", "составим план", "составь список", "составим список",
            "составь таблицу", "составим таблицу",
            "помоги составить", "помоги написать", "помоги разработать",
            // Analysis
            "проанализируй", "проанализируйте",
            // Code / content generation
            "напиши код", "напиши программу", "напиши скрипт", "напиши функцию",
            "напиши рассказ", "напиши статью", "напиши эссе", "напиши сочинение",
            "напиши письмо", "напиши инструкцию",
            // Action verbs (imperative + collaborative)
            "реализуй", "разработай", "спланируй", "разработаем", "реализуем",
            "исследуй", "сравни несколько", "сравни и проанализируй",
            // "давай + verb" collaborative patterns
            "давай составим", "давай напишем", "давай разработаем", "давай создадим",
            "давай сделаем", "давай реализуем", "давай спланируем"
        )

        // Unambiguous factual / lookup patterns → always SIMPLE
        private val SIMPLE_PREFIXES = listOf(
            "что такое", "кто такой", "кто такая", "кто такие",
            "что значит", "что означает",
            "сколько", "когда ", "где ",
            "столица ", "как называется",
            "переведи", "переведите",
            "синоним", "антоним"
        )
    }
}
