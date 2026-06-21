package com.example.petapp

import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.LlmResponse
import com.example.petapp.domain.usecase.DetectComplexityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetectComplexityUseCaseTest {

    private lateinit var llmService: LlmService
    private lateinit var useCase: DetectComplexityUseCase

    private val providerConfig = LlmProviderConfig(
        providerName     = "Test",
        availableModels  = listOf("flash"),
        defaultModel     = "flash",
        backgroundModel  = "flash",
        contextLimit     = 128_000,
        supportsThinking = false,
        supportsTools    = false,
        modelPricing     = emptyList()
    )

    @Before
    fun setUp() {
        llmService = mockk()
        useCase = DetectComplexityUseCase(llmService, providerConfig)
    }

    // ── Heuristic: clearly SIMPLE (no LLM call) ───────────────────────────

    @Test
    fun `single word returns false without LLM call`() = runTest {
        val result = useCase("привет")
        assertFalse(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `two-word query returns false without LLM call`() = runTest {
        val result = useCase("что такое")
        assertFalse(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `three-word factual question returns false without LLM call`() = runTest {
        val result = useCase("столица франции это")
        assertFalse(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `simple prefix что такое returns false without LLM call`() = runTest {
        val result = useCase("что такое kotlin coroutines")
        assertFalse(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `simple prefix сколько returns false without LLM call`() = runTest {
        val result = useCase("сколько планет в солнечной системе")
        assertFalse(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `переведи prefix returns false without LLM call`() = runTest {
        val result = useCase("переведи hello world на русский")
        assertFalse(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `что значит prefix returns false without LLM call`() = runTest {
        val result = useCase("что значит deadlock в программировании")
        assertFalse(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    // ── Heuristic: clearly COMPLEX (no LLM call) ─────────────────────────

    @Test
    fun `пошагово keyword returns true without LLM call`() = runTest {
        val result = useCase("объясни пошагово как работает garbage collector")
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `по шагам keyword returns true without LLM call`() = runTest {
        val result = useCase("расскажи по шагам как настроить CI/CD")
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `напиши код keyword returns true without LLM call`() = runTest {
        val result = useCase("напиши код для сортировки массива на kotlin")
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `напиши функцию keyword returns true without LLM call`() = runTest {
        val result = useCase("напиши функцию для парсинга JSON")
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `напиши рассказ keyword returns true without LLM call`() = runTest {
        val result = useCase("напиши рассказ про робота который научился чувствовать")
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `составь план keyword returns true without LLM call`() = runTest {
        val result = useCase("составь план изучения kotlin за три месяца")
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `проанализируй keyword returns true without LLM call`() = runTest {
        val result = useCase("проанализируй плюсы и минусы микросервисной архитектуры")
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `реализуй keyword returns true without LLM call`() = runTest {
        val result = useCase("реализуй паттерн observer на kotlin")
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `message over 50 words returns true without LLM call`() = runTest {
        val longInput = "слово ".repeat(51).trim()
        val result = useCase(longInput)
        assertTrue(result)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    // ── LLM fallback: ambiguous input ─────────────────────────────────────

    @Test
    fun `ambiguous input calls LLM and returns COMPLEX when LLM says so`() = runTest {
        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = "COMPLEX", toolCalls = null, finishReason = "stop", usage = null
        )

        val result = useCase("расскажи мне про kotlin")
        assertTrue(result)
        coVerify(exactly = 1) { llmService.chat(any()) }
    }

    @Test
    fun `ambiguous input calls LLM and returns SIMPLE when LLM says so`() = runTest {
        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = "SIMPLE", toolCalls = null, finishReason = "stop", usage = null
        )

        val result = useCase("расскажи мне про kotlin")
        assertFalse(result)
        coVerify(exactly = 1) { llmService.chat(any()) }
    }

    @Test
    fun `LLM failure defaults to false`() = runTest {
        coEvery { llmService.chat(any()) } throws RuntimeException("network error")

        val result = useCase("расскажи мне про kotlin")
        assertFalse(result)
    }

    @Test
    fun `LLM returns null content defaults to false`() = runTest {
        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = null, toolCalls = null, finishReason = "stop", usage = null
        )

        val result = useCase("расскажи мне про kotlin")
        assertFalse(result)
        coVerify(exactly = 1) { llmService.chat(any()) }
    }
}
