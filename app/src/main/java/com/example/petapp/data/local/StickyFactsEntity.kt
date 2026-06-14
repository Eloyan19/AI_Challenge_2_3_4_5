package com.example.petapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the singleton `sticky_facts` row (always id = 1).
 *
 * Stores the current bullet-point facts block maintained by [com.example.petapp.domain.strategy.StickyFactsStrategy].
 * The `@ColumnInfo` annotation ensures the column name matches the snake_case name from the
 * migration 2→3 DDL on both fresh installs and upgraded databases.
 *
 * @property id Always 1; uses REPLACE conflict strategy so there is at most one row.
 * @property content The current facts text (bullet points separated by newlines).
 * @property updatedAt Unix epoch milliseconds of the last facts update.
 */
@Entity(tableName = "sticky_facts")
data class StickyFactsEntity(
    @PrimaryKey val id: Int = 1,
    val content: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
