package com.example.petapp.domain.repository

import com.example.petapp.domain.model.Branch
import com.example.petapp.domain.model.ChatMessage

interface ChatRepository {
    // ── Linear history ──────────────────────────────────────────────────────────
    suspend fun getAllMessages(): List<ChatMessage>
    suspend fun saveMessages(messages: List<ChatMessage>)
    suspend fun clearAll()

    // ── Summary (SUMMARY strategy) ──────────────────────────────────────────────
    suspend fun getSummary(): String?
    suspend fun saveSummary(content: String)
    suspend fun clearSummary()

    // ── Sticky facts (STICKY_FACTS strategy) ───────────────────────────────────
    suspend fun getFacts(): String?
    suspend fun saveFacts(content: String)
    suspend fun clearFacts()

    // ── Branches (BRANCHING strategy) ──────────────────────────────────────────
    suspend fun getAllBranches(): List<Branch>
    suspend fun getBranch(id: Long): Branch?
    suspend fun createBranch(name: String, parentBranchId: Long, checkpointMessageId: Long?): Long
    suspend fun getMessagesForBranch(branchId: Long): List<ChatMessage>
    suspend fun getMessagesForBranchUpTo(branchId: Long, upToId: Long): List<ChatMessage>
    suspend fun getLastMessageIdForBranch(branchId: Long): Long?
    suspend fun clearBranchMessages(branchId: Long)
    suspend fun resetBranches()
}
