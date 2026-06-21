package com.example.petapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity that persists one [com.example.petapp.domain.model.ChatMessage] to `chat_messages`.
 *
 * Fields mirror [com.example.petapp.domain.model.ChatMessage] exactly; mapping is handled in
 * [com.example.petapp.data.repository.ChatRepositoryImpl].
 *
 * @property id Auto-generated primary key (0 = not yet persisted).
 * @property branchId Branch this row belongs to; 1 = main branch (added in migration 2→3).
 * @property turnId Groups all messages from a single user↔agent round-trip.
 * @property role One of `"user"`, `"assistant"`, `"tool"`.
 * @property messageJson Full JSON of [com.example.petapp.data.Message] for agent history restore.
 * @property displayText Plain text for the chat bubble; null for invisible tool messages.
 * @property promptTokens Token counts set only on the last assistant message of a turn.
 * @property completionTokens See [promptTokens].
 * @property totalTokens See [promptTokens].
 * @property cachedTokens KV-cache hits; reduces effective cost.
 * @property cost Estimated USD cost of the turn.
 * @property durationSec Wall-clock seconds for the API call.
 * @property timestamp Unix epoch millis; offset by message index to preserve intra-turn order.
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index("branch_id"), Index("turnId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "branch_id") val branchId: Long = 1L,
    val turnId: Long,
    val role: String,
    val messageJson: String,
    val displayText: String?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val cachedTokens: Int?,
    val cost: Double?,
    val durationSec: Double?,
    val timestamp: Long
)
