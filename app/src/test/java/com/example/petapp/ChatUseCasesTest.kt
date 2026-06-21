package com.example.petapp

import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.model.LongTermMemoryEntry
import com.example.petapp.domain.repository.ChatRepository
import com.example.petapp.domain.usecase.AddLongTermMemoryUseCase
import com.example.petapp.domain.usecase.ClearHistoryUseCase
import com.example.petapp.domain.usecase.DeleteLongTermMemoryUseCase
import com.example.petapp.domain.usecase.GetLongTermMemoryUseCase
import com.example.petapp.domain.usecase.LoadHistoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ChatUseCasesTest {

    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        repository = mockk()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Target B: Use case delegation tests
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun testAddLongTermMemoryUseCase_delegatesToRepository() = runTest {
        // Given: repository returns an ID
        val expectedId = 42L
        coEvery {
            repository.addLongTermMemory("profile", "name", "John")
        } returns expectedId

        val useCase = AddLongTermMemoryUseCase(repository)

        // When: invoking the use case
        val result = useCase("profile", "name", "John")

        // Then: repository method is called with correct parameters and result is returned
        assertEquals(expectedId, result)
        coVerify { repository.addLongTermMemory("profile", "name", "John") }
    }

    @Test
    fun testAddLongTermMemoryUseCase_differentCategory() = runTest {
        // Given: repository is mocked
        coEvery {
            repository.addLongTermMemory("knowledge", "lang", "Kotlin")
        } returns 99L

        val useCase = AddLongTermMemoryUseCase(repository)

        // When: invoking with different category
        val result = useCase("knowledge", "lang", "Kotlin")

        // Then: correct parameters are passed
        assertEquals(99L, result)
        coVerify { repository.addLongTermMemory("knowledge", "lang", "Kotlin") }
    }

    @Test
    fun testClearHistoryUseCase_delegatesToRepository() = runTest {
        // Given: repository clearAll is mocked
        coEvery { repository.clearAll() } returns Unit

        val useCase = ClearHistoryUseCase(repository)

        // When: invoking the use case
        useCase()

        // Then: repository.clearAll() is called once
        coVerify(exactly = 1) { repository.clearAll() }
    }

    @Test
    fun testLoadHistoryUseCase_returnsRepositoryResult() = runTest {
        // Given: repository returns messages
        val messages = listOf(
            ChatMessage(
                id = 1L, branchId = 1L, turnId = 1L, role = "user",
                messageJson = """{"role":"user","content":"Hello"}""",
                displayText = "Hello", promptTokens = null,
                completionTokens = null, totalTokens = null,
                cachedTokens = null, cost = null, durationSec = null, timestamp = 1000L
            ),
            ChatMessage(
                id = 2L, branchId = 1L, turnId = 1L, role = "assistant",
                messageJson = """{"role":"assistant","content":"Hi"}""",
                displayText = "Hi", promptTokens = 10,
                completionTokens = 5, totalTokens = 15,
                cachedTokens = 0, cost = 0.001, durationSec = 0.5, timestamp = 1001L
            )
        )
        coEvery { repository.getAllMessages() } returns messages

        val useCase = LoadHistoryUseCase(repository)

        // When: invoking the use case
        val result = useCase()

        // Then: returns repository result
        assertEquals(messages, result)
        assertEquals(2, result.size)
        coVerify { repository.getAllMessages() }
    }

    @Test
    fun testLoadHistoryUseCase_emptyHistory() = runTest {
        // Given: repository returns empty list
        coEvery { repository.getAllMessages() } returns emptyList()

        val useCase = LoadHistoryUseCase(repository)

        // When: invoking the use case
        val result = useCase()

        // Then: returns empty list
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetLongTermMemoryUseCase_returnsRepositoryResult() = runTest {
        // Given: repository returns long-term memory entries
        val entries = listOf(
            LongTermMemoryEntry(
                id = 1L, category = "profile", keyName = "name", value = "Alice",
                createdAt = 1000L, updatedAt = 1000L
            ),
            LongTermMemoryEntry(
                id = 2L, category = "knowledge", keyName = "lang", value = "Python",
                createdAt = 2000L, updatedAt = 2000L
            )
        )
        coEvery { repository.getLongTermMemory() } returns entries

        val useCase = GetLongTermMemoryUseCase(repository)

        // When: invoking the use case
        val result = useCase()

        // Then: returns repository result
        assertEquals(entries, result)
        assertEquals(2, result.size)
        coVerify { repository.getLongTermMemory() }
    }

    @Test
    fun testGetLongTermMemoryUseCase_noMemory() = runTest {
        // Given: repository returns empty list
        coEvery { repository.getLongTermMemory() } returns emptyList()

        val useCase = GetLongTermMemoryUseCase(repository)

        // When: invoking the use case
        val result = useCase()

        // Then: returns empty list
        assertTrue(result.isEmpty())
    }

    @Test
    fun testDeleteLongTermMemoryUseCase_delegatesToRepository() = runTest {
        // Given: repository is mocked
        coEvery { repository.deleteLongTermMemory(5L) } returns Unit

        val useCase = DeleteLongTermMemoryUseCase(repository)

        // When: invoking with an ID
        useCase(5L)

        // Then: repository.deleteLongTermMemory is called with correct ID
        coVerify { repository.deleteLongTermMemory(5L) }
    }

    @Test
    fun testDeleteLongTermMemoryUseCase_differentId() = runTest {
        // Given: repository is mocked
        coEvery { repository.deleteLongTermMemory(999L) } returns Unit

        val useCase = DeleteLongTermMemoryUseCase(repository)

        // When: invoking with different ID
        useCase(999L)

        // Then: correct ID is passed
        coVerify { repository.deleteLongTermMemory(999L) }
    }

    @Test
    fun testClearHistoryUseCase_multipleInvocations() = runTest {
        // Given: repository is mocked
        coEvery { repository.clearAll() } returns Unit

        val useCase = ClearHistoryUseCase(repository)

        // When: calling multiple times
        useCase()
        useCase()

        // Then: repository is called twice
        coVerify(exactly = 2) { repository.clearAll() }
    }

    @Test
    fun testAddLongTermMemoryUseCase_emptyValues() = runTest {
        // Given: repository accepts empty strings
        coEvery {
            repository.addLongTermMemory("", "", "")
        } returns 123L

        val useCase = AddLongTermMemoryUseCase(repository)

        // When: invoking with empty strings (edge case)
        val result = useCase("", "", "")

        // Then: passes through to repository as-is (validation is repository's responsibility)
        assertEquals(123L, result)
    }

    @Test
    fun testLoadHistoryUseCase_singleMessage() = runTest {
        // Given: repository returns single message
        val message = ChatMessage(
            id = 1L, branchId = 1L, turnId = 1L, role = "user",
            messageJson = """{"role":"user","content":"Hi"}""",
            displayText = "Hi", promptTokens = null,
            completionTokens = null, totalTokens = null,
            cachedTokens = null, cost = null, durationSec = null, timestamp = 1000L
        )
        coEvery { repository.getAllMessages() } returns listOf(message)

        val useCase = LoadHistoryUseCase(repository)

        // When: invoking
        val result = useCase()

        // Then: returns single message
        assertEquals(1, result.size)
        assertEquals(message, result[0])
    }

    @Test
    fun testGetLongTermMemoryUseCase_singleEntry() = runTest {
        // Given: repository returns single entry
        val entry = LongTermMemoryEntry(
            id = 1L, category = "profile", keyName = "color", value = "blue",
            createdAt = 1000L, updatedAt = 1000L
        )
        coEvery { repository.getLongTermMemory() } returns listOf(entry)

        val useCase = GetLongTermMemoryUseCase(repository)

        // When: invoking
        val result = useCase()

        // Then: returns single entry
        assertEquals(1, result.size)
        assertEquals(entry, result[0])
    }

    @Test
    fun testDeleteLongTermMemoryUseCase_zeroId() = runTest {
        // Given: repository accepts any ID
        coEvery { repository.deleteLongTermMemory(0L) } returns Unit

        val useCase = DeleteLongTermMemoryUseCase(repository)

        // When: invoking with ID 0
        useCase(0L)

        // Then: passes through to repository
        coVerify { repository.deleteLongTermMemory(0L) }
    }
}
