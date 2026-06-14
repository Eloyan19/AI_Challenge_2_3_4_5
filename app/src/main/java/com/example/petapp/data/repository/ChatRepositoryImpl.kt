package com.example.petapp.data.repository

import com.example.petapp.data.local.ChatMessageDao
import com.example.petapp.data.local.ChatMessageEntity
import com.example.petapp.domain.model.ChatMessage
import com.example.petapp.domain.repository.ChatRepository

class ChatRepositoryImpl(private val dao: ChatMessageDao) : ChatRepository {

    override suspend fun getAllMessages(): List<ChatMessage> =
        dao.getAll().map { it.toDomain() }

    override suspend fun saveMessages(messages: List<ChatMessage>) =
        dao.insertAll(messages.map { it.toEntity() })

    override suspend fun clearAll() = dao.clearAll()

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id, turnId = turnId, role = role, messageJson = messageJson,
        displayText = displayText, promptTokens = promptTokens,
        completionTokens = completionTokens, totalTokens = totalTokens,
        cachedTokens = cachedTokens, cost = cost, durationSec = durationSec,
        timestamp = timestamp
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = id, turnId = turnId, role = role, messageJson = messageJson,
        displayText = displayText, promptTokens = promptTokens,
        completionTokens = completionTokens, totalTokens = totalTokens,
        cachedTokens = cachedTokens, cost = cost, durationSec = durationSec,
        timestamp = timestamp
    )
}
