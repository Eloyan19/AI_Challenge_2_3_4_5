package com.example.petapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the singleton `conversation_summary` row (always id = 1).
 *
 * Stores the rolling LLM-generated summary maintained by [com.example.petapp.domain.strategy.SummaryStrategy].
 * Uses REPLACE conflict strategy (in [SummaryDao]) so there is at most one row at all times.
 *
 * @property id Always 1.
 * @property content The accumulated summary text (3–6 sentences updated after each compression).
 * @property timestamp Unix epoch milliseconds of the last summary update.
 */
@Entity(tableName = "conversation_summary")
data class SummaryEntity(
    @PrimaryKey val id: Int = 1,
    val content: String,
    val timestamp: Long
)
