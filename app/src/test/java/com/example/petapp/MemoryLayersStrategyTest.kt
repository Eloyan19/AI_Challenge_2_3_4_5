package com.example.petapp

import android.util.Log
import com.example.petapp.domain.LlmService
import com.example.petapp.domain.model.LlmProviderConfig
import com.example.petapp.domain.model.LlmRequest
import com.example.petapp.domain.model.LlmResponse
import com.example.petapp.domain.model.LlmUsage
import com.example.petapp.domain.model.Message
import com.example.petapp.domain.strategy.MemoryLayersStrategy
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class MemoryLayersStrategyTest {

    private lateinit var llmService: LlmService
    private lateinit var providerConfig: LlmProviderConfig
    private lateinit var strategy: MemoryLayersStrategy
    private val gson = Gson()

    @Before
    fun setUp() {
        llmService = mockk()
        providerConfig = mockk()

        // Mock providerConfig properties
        every { providerConfig.backgroundModel } returns "deepseek-flash"

        strategy = MemoryLayersStrategy(llmService, providerConfig)

        // Mock static Log to avoid "not mocked" errors in unit tests
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    // ─────────────────────────────────────────────────────────────────────
    // Target A: parseLongTermFacts() tests
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun testExtractLongTermFacts_validJsonArray() = runTest {
        // Given: LLM returns valid JSON array
        val jsonResponse = """
            [
              {"category":"profile","key":"name","value":"John"},
              {"category":"knowledge","key":"pref","value":"Kotlin"}
            ]
        """.trimIndent()

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = jsonResponse,
            toolCalls = null,
            finishReason = "stop",
            usage = null
        )

        // When: calling extractLongTermFacts
        val history = listOf(
            Message(role = "user", content = "Hello"),
            Message(role = "assistant", content = "Hi there")
        )
        val entries = strategy.extractLongTermFacts(history)

        // Then: entries are parsed correctly
        assertNotNull(entries)
        assertEquals(2, entries!!.size)
        assertEquals("profile", entries[0].category)
        assertEquals("name", entries[0].keyName)
        assertEquals("John", entries[0].value)
        assertEquals("knowledge", entries[1].category)
        assertEquals("pref", entries[1].keyName)
        assertEquals("Kotlin", entries[1].value)
    }

    @Test
    fun testExtractLongTermFacts_jsonWrappedInMarkdownFences() = runTest {
        // Given: LLM returns JSON wrapped in markdown code fences
        val jsonResponse = """
            ```json
            [
              {"category":"profile","key":"age","value":"25"}
            ]
            ```
        """.trimIndent()

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = jsonResponse,
            toolCalls = null,
            finishReason = "stop",
            usage = null
        )

        // When: calling extractLongTermFacts
        val history = listOf(Message(role = "user", content = "Test"))
        val entries = strategy.extractLongTermFacts(history)

        // Then: markdown fences are stripped and JSON is parsed
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("age", entries[0].keyName)
        assertEquals("25", entries[0].value)
    }

    @Test
    fun testExtractLongTermFacts_emptyArray() = runTest {
        // Given: LLM returns empty array
        val jsonResponse = "[]"

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = jsonResponse,
            toolCalls = null,
            finishReason = "stop",
            usage = null
        )

        // When: calling extractLongTermFacts
        val history = listOf(Message(role = "user", content = "No facts"))
        val entries = strategy.extractLongTermFacts(history)

        // Then: returns empty list
        assertNotNull(entries)
        assertTrue(entries!!.isEmpty())
    }

    @Test
    fun testExtractLongTermFacts_invalidJson() = runTest {
        // Given: LLM returns invalid JSON
        val invalidJson = "not valid json"

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = invalidJson,
            toolCalls = null,
            finishReason = "stop",
            usage = null
        )

        // When: calling extractLongTermFacts
        val history = listOf(Message(role = "user", content = "Test"))
        val entries = strategy.extractLongTermFacts(history)

        // Then: returns empty list without throwing
        assertNotNull(entries)
        assertTrue(entries!!.isEmpty())
    }

    @Test
    fun testExtractLongTermFacts_missingKey() = runTest {
        // Given: JSON entry missing "key" field
        val jsonResponse = """
            [
              {"category":"profile","value":"John"},
              {"category":"knowledge","key":"pref","value":"Kotlin"}
            ]
        """.trimIndent()

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = jsonResponse,
            toolCalls = null,
            finishReason = "stop",
            usage = null
        )

        // When: calling extractLongTermFacts
        val history = listOf(Message(role = "user", content = "Test"))
        val entries = strategy.extractLongTermFacts(history)

        // Then: first entry is skipped, second is included
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("pref", entries[0].keyName)
    }

    @Test
    fun testExtractLongTermFacts_missingValue() = runTest {
        // Given: JSON entry missing "value" field
        val jsonResponse = """
            [
              {"category":"profile","key":"name"},
              {"category":"knowledge","key":"pref","value":"Kotlin"}
            ]
        """.trimIndent()

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = jsonResponse,
            toolCalls = null,
            finishReason = "stop",
            usage = null
        )

        // When: calling extractLongTermFacts
        val history = listOf(Message(role = "user", content = "Test"))
        val entries = strategy.extractLongTermFacts(history)

        // Then: first entry is skipped, second is included
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("pref", entries[0].keyName)
    }

    @Test
    fun testExtractLongTermFacts_blankCategory_defaultsToKnowledge() = runTest {
        // Given: JSON entry with blank category
        val jsonResponse = """
            [
              {"category":"","key":"fact","value":"content"}
            ]
        """.trimIndent()

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = jsonResponse,
            toolCalls = null,
            finishReason = "stop",
            usage = null
        )

        // When: calling extractLongTermFacts
        val history = listOf(Message(role = "user", content = "Test"))
        val entries = strategy.extractLongTermFacts(history)

        // Then: category defaults to "knowledge"
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("knowledge", entries[0].category)
    }

    @Test
    fun testExtractLongTermFacts_llmServiceException() = runTest {
        // Given: LLM service throws exception
        coEvery { llmService.chat(any()) } throws RuntimeException("Network error")

        // When: calling extractLongTermFacts
        val history = listOf(Message(role = "user", content = "Test"))
        val entries = strategy.extractLongTermFacts(history)

        // Then: returns null (caught and logged)
        assertNull(entries)
    }

    @Test
    fun testExtractLongTermFacts_blankKeyOrValue_ignored() = runTest {
        // Given: JSON entry with blank key or value
        val jsonResponse = """
            [
              {"category":"profile","key":"  ","value":"John"},
              {"category":"knowledge","key":"pref","value":"  "},
              {"category":"decision","key":"choice","value":"yes"}
            ]
        """.trimIndent()

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = jsonResponse,
            toolCalls = null,
            finishReason = "stop",
            usage = null
        )

        // When: calling extractLongTermFacts
        val history = listOf(Message(role = "user", content = "Test"))
        val entries = strategy.extractLongTermFacts(history)

        // Then: blank key/value entries are skipped
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("choice", entries[0].keyName)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Target C: formatLtmForPrompt indirectly via buildMessages()
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun testBuildMessages_withLongTermMemory() {
        // Given: long-term memory is set
        val ltmText = "== profile ==\nname: John\nage: 30"
        strategy.setLongTermMemory(ltmText)

        // When: calling buildMessages with empty history
        val result = strategy.buildMessages(emptyList())

        // Then: first message is system message with long-term memory
        assertFalse(result.isEmpty())
        assertEquals("system", result[0].role)
        assertTrue(result[0].content!!.contains("ДОЛГОВРЕМЕННАЯ ПАМЯТЬ"))
        assertTrue(result[0].content!!.contains(ltmText))
    }

    @Test
    fun testBuildMessages_withoutLongTermMemory() {
        // Given: long-term memory is not set
        strategy.setLongTermMemory(null)

        // When: calling buildMessages with empty history
        val result = strategy.buildMessages(emptyList())

        // Then: returns empty list (no system message added)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testBuildMessages_withBothLongTermAndWorkingMemory() {
        // Given: both long-term and working memory are set
        val ltmText = "== profile ==\nname: Alice"
        val workingText = "Current task: learning Kotlin"
        strategy.setLongTermMemory(ltmText)
        strategy.restoreWorkingMemory(workingText)

        // When: calling buildMessages
        val result = strategy.buildMessages(emptyList())

        // Then: first message is long-term, second is working memory
        assertEquals(2, result.size)
        assertEquals("system", result[0].role)
        assertTrue(result[0].content!!.contains("ДОЛГОВРЕМЕННАЯ ПАМЯТЬ"))
        assertEquals("system", result[1].role)
        assertTrue(result[1].content!!.contains("РАБОЧАЯ ПАМЯТЬ"))
    }

    @Test
    fun testBuildMessages_preservesHistory() {
        // Given: history messages
        val userMsg = Message(role = "user", content = "Hello")
        val assistantMsg = Message(role = "assistant", content = "Hi")
        strategy.setLongTermMemory("profile")

        // When: calling buildMessages with history
        val result = strategy.buildMessages(listOf(userMsg, assistantMsg))

        // Then: system message is prepended, history is preserved
        assertEquals(3, result.size)
        assertEquals(userMsg, result[1])
        assertEquals(assistantMsg, result[2])
    }

    @Test
    fun testBuildMessages_afterClearingLongTermMemory() {
        // Given: long-term memory was set then cleared
        strategy.setLongTermMemory("initial profile")
        strategy.setLongTermMemory(null)

        // When: calling buildMessages
        val result = strategy.buildMessages(emptyList())

        // Then: returns empty (long-term memory is gone)
        assertTrue(result.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────
    // Target D: afterTurn — isTurnSignificant guard
    // ─────────────────────────────────────────────────────────────────────

    private fun significantHistory(): List<Message> {
        val userWords   = "слово ".repeat(6).trim()   // 6 words
        val assistWords = "слово ".repeat(7).trim()   // 7 words → total 13 >= MIN_TURN_WORDS
        return listOf(
            Message(role = "user",      content = userWords),
            Message(role = "assistant", content = assistWords)
        )
    }

    @Test
    fun `afterTurn skips LLM when history has fewer than 2 content messages`() = runTest {
        val history = listOf(Message(role = "user", content = "привет"))
        strategy.afterTurn(history)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `afterTurn skips LLM when last assistant message has no content`() = runTest {
        // Tool-call-only turn: assistant message with null content
        val history = listOf(
            Message(role = "user",      content = "покажи погоду в москве"),
            Message(role = "assistant", content = null),
            Message(role = "tool",      content = """{"temp":20}""")
        )
        strategy.afterTurn(history)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `afterTurn skips LLM when combined word count is below threshold`() = runTest {
        // 1 + 3 = 4 words < MIN_TURN_WORDS
        val history = listOf(
            Message(role = "user",      content = "привет"),
            Message(role = "assistant", content = "Привет! Чем помочь?")
        )
        strategy.afterTurn(history)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `afterTurn calls LLM when turn is significant`() = runTest {
        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = "Task: learning coroutines", toolCalls = null, finishReason = "stop", usage = null
        )
        strategy.afterTurn(significantHistory())
        coVerify(exactly = 1) { llmService.chat(any()) }
    }

    @Test
    fun `afterTurn updates working memory when LLM returns content`() = runTest {
        val extracted = "Task: learning coroutines. Entity: Kotlin."
        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = extracted, toolCalls = null, finishReason = "stop", usage = null
        )
        strategy.afterTurn(significantHistory())
        assertEquals(extracted, strategy.workingMemory)
    }

    @Test
    fun `afterTurn does not update working memory when turn is skipped`() = runTest {
        val initial = "existing working memory"
        strategy.restoreWorkingMemory(initial)

        val history = listOf(
            Message(role = "user",      content = "ок"),
            Message(role = "assistant", content = "Понял")
        )
        strategy.afterTurn(history)

        assertEquals(initial, strategy.workingMemory)
        coVerify(exactly = 0) { llmService.chat(any()) }
    }

    @Test
    fun `afterTurn fires onAuxDataUpdated only when LLM extraction succeeds`() = runTest {
        var callbackInvoked = false
        strategy.onAuxDataUpdated = { callbackInvoked = true }

        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = "Task: test.", toolCalls = null, finishReason = "stop", usage = null
        )
        strategy.afterTurn(significantHistory())
        assertTrue(callbackInvoked)
    }

    @Test
    fun `afterTurn does not fire onAuxDataUpdated when turn is skipped`() = runTest {
        var callbackInvoked = false
        strategy.onAuxDataUpdated = { callbackInvoked = true }

        val history = listOf(
            Message(role = "user",      content = "привет"),
            Message(role = "assistant", content = "Привет!")
        )
        strategy.afterTurn(history)
        assertFalse(callbackInvoked)
    }

    @Test
    fun `custom minTurnWords threshold is respected`() = runTest {
        val lowThresholdStrategy = MemoryLayersStrategy(
            llmService     = llmService,
            providerConfig = providerConfig,
            minTurnWords   = 3
        )
        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = "Task: greet.", toolCalls = null, finishReason = "stop", usage = null
        )

        // 1 + 3 = 4 words — below default threshold (12) but above custom threshold (3)
        val history = listOf(
            Message(role = "user",      content = "привет"),
            Message(role = "assistant", content = "Привет! Как дела?")
        )
        lowThresholdStrategy.afterTurn(history)
        coVerify(exactly = 1) { llmService.chat(any()) }
    }

    @Test
    fun `minTurnWords can be changed at runtime`() = runTest {
        coEvery { llmService.chat(any()) } returns LlmResponse(
            content = "Task: greet.", toolCalls = null, finishReason = "stop", usage = null
        )
        val history = listOf(
            Message(role = "user",      content = "привет"),
            Message(role = "assistant", content = "Привет! Как дела?")
        )

        // Default threshold (12) — skips this turn
        strategy.afterTurn(history)
        coVerify(exactly = 0) { llmService.chat(any()) }

        // Lower the threshold — same turn now passes
        strategy.minTurnWords = 3
        strategy.afterTurn(history)
        coVerify(exactly = 1) { llmService.chat(any()) }
    }
}
