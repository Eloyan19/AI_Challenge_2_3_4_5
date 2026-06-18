package com.example.petapp.data.repository

import com.example.petapp.data.local.BranchDao
import com.example.petapp.data.local.BranchEntity
import com.example.petapp.data.local.ChatMessageDao
import com.example.petapp.data.local.ChatMessageEntity
import com.example.petapp.data.local.LongTermMemoryDao
import com.example.petapp.data.local.LongTermMemoryEntity
import com.example.petapp.data.local.StickyFactsDao
import com.example.petapp.data.local.StickyFactsEntity
import com.example.petapp.data.local.SummaryDao
import com.example.petapp.data.local.SummaryEntity
import com.example.petapp.data.local.UserProfileDao
import com.example.petapp.data.local.UserProfileEntity
import com.example.petapp.data.local.WorkingMemoryDao
import com.example.petapp.data.local.WorkingMemoryEntity
import com.example.petapp.domain.model.Branch
import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.model.LongTermMemoryEntry
import com.example.petapp.domain.model.UserProfile
import com.example.petapp.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * Room-backed implementation of [ChatRepository].
 *
 * Each repository method is a thin delegation to the appropriate DAO, plus
 * domain↔entity mapping via the private extension functions [toDomain] and [toEntity].
 * All DAOs are suspend functions so callers must use coroutines.
 *
 * Injected as a `@Singleton` via [com.example.petapp.di.RepositoryModule].
 */
class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao,
    private val summaryDao: SummaryDao,
    private val stickyFactsDao: StickyFactsDao,
    private val branchDao: BranchDao,
    private val workingMemoryDao: WorkingMemoryDao,
    private val longTermMemoryDao: LongTermMemoryDao,
    private val userProfileDao: UserProfileDao
) : ChatRepository {

    // ── Linear history ──────────────────────────────────────────────────────────

    override suspend fun getAllMessages(): List<ChatMessage> =
        dao.getAll().map { it.toDomain() }

    override suspend fun saveMessages(messages: List<ChatMessage>) =
        dao.insertAll(messages.map { it.toEntity() })

    override suspend fun clearAll() {
        dao.clearAll()
        summaryDao.clear()
        stickyFactsDao.clear()
    }

    // ── Summary ─────────────────────────────────────────────────────────────────

    override suspend fun getSummary(): String? = summaryDao.get()?.content

    override suspend fun saveSummary(content: String) =
        summaryDao.save(SummaryEntity(content = content, timestamp = System.currentTimeMillis()))

    override suspend fun clearSummary() = summaryDao.clear()

    // ── Sticky facts ─────────────────────────────────────────────────────────────

    override suspend fun getFacts(): String? = stickyFactsDao.get()?.content

    override suspend fun saveFacts(content: String) =
        stickyFactsDao.save(StickyFactsEntity(content = content, updatedAt = System.currentTimeMillis()))

    override suspend fun clearFacts() = stickyFactsDao.clear()

    // ── Branches ─────────────────────────────────────────────────────────────────

    override suspend fun getAllBranches(): List<Branch> =
        branchDao.getAll().map { it.toDomain() }

    override suspend fun getBranch(id: Long): Branch? =
        branchDao.getById(id)?.toDomain()

    override suspend fun createBranch(
        name: String,
        parentBranchId: Long,
        checkpointMessageId: Long?
    ): Long = branchDao.insert(
        BranchEntity(
            name                = name,
            parentBranchId      = parentBranchId,
            checkpointMessageId = checkpointMessageId,
            createdAt           = System.currentTimeMillis()
        )
    )

    override suspend fun getMessagesForBranch(branchId: Long): List<ChatMessage> =
        dao.getByBranch(branchId).map { it.toDomain() }

    override suspend fun getMessagesForBranchUpTo(branchId: Long, upToId: Long): List<ChatMessage> =
        dao.getByBranchUpTo(branchId, upToId).map { it.toDomain() }

    override suspend fun getLastMessageIdForBranch(branchId: Long): Long? =
        dao.getLastIdForBranch(branchId)

    override suspend fun clearBranchMessages(branchId: Long) =
        dao.clearBranch(branchId)

    override suspend fun resetBranches() {
        dao.clearNonMainBranches()
        branchDao.deleteAllExceptMain()
    }

    // ── Working memory ────────────────────────────────────────────────────────────

    override suspend fun getWorkingMemory(): String? = workingMemoryDao.get()?.content

    override suspend fun saveWorkingMemory(content: String) =
        workingMemoryDao.save(WorkingMemoryEntity(content = content, updatedAt = System.currentTimeMillis()))

    override suspend fun clearWorkingMemory() = workingMemoryDao.clear()

    // ── Long-term memory ──────────────────────────────────────────────────────────

    override suspend fun getLongTermMemory(): List<LongTermMemoryEntry> =
        longTermMemoryDao.getAll().map { it.toDomain() }

    override suspend fun addLongTermMemory(category: String, keyName: String, value: String): Long =
        longTermMemoryDao.insert(
            LongTermMemoryEntity(
                category  = category,
                keyName   = keyName,
                value     = value,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

    override suspend fun deleteLongTermMemory(id: Long) =
        longTermMemoryDao.deleteById(id)

    // ── User profiles ─────────────────────────────────────────────────────────────

    override suspend fun getProfiles(): List<UserProfile> =
        userProfileDao.getAll().map { it.toDomain() }

    override suspend fun getProfile(id: Long): UserProfile? =
        userProfileDao.getById(id)?.toDomain()

    override suspend fun saveProfile(id: Long?, name: String, instructions: String): Long {
        val now = System.currentTimeMillis()
        return if (id == null) {
            userProfileDao.insert(UserProfileEntity(name = name, instructions = instructions, createdAt = now))
        } else {
            val existing = userProfileDao.getById(id)
            val entity = existing?.copy(name = name, instructions = instructions)
                ?: UserProfileEntity(id = id, name = name, instructions = instructions, createdAt = now)
            userProfileDao.update(entity)
            id
        }
    }

    override suspend fun deleteProfile(id: Long) = userProfileDao.deleteById(id)

    // ── Mapping ──────────────────────────────────────────────────────────────────

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id, branchId = branchId, turnId = turnId, role = role,
        messageJson = messageJson, displayText = displayText,
        promptTokens = promptTokens, completionTokens = completionTokens,
        totalTokens = totalTokens, cachedTokens = cachedTokens,
        cost = cost, durationSec = durationSec, timestamp = timestamp
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = id, branchId = branchId, turnId = turnId, role = role,
        messageJson = messageJson, displayText = displayText,
        promptTokens = promptTokens, completionTokens = completionTokens,
        totalTokens = totalTokens, cachedTokens = cachedTokens,
        cost = cost, durationSec = durationSec, timestamp = timestamp
    )

    private fun BranchEntity.toDomain() = Branch(
        id = id, name = name, parentBranchId = parentBranchId,
        checkpointMessageId = checkpointMessageId, createdAt = createdAt
    )

    private fun LongTermMemoryEntity.toDomain() = LongTermMemoryEntry(
        id = id, category = category, keyName = keyName, value = value,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun UserProfileEntity.toDomain() = UserProfile(
        id = id, name = name, instructions = instructions, createdAt = createdAt
    )
}
