package com.example.petapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity that persists one [com.example.petapp.domain.model.Branch] to `branches`.
 *
 * The `branches` table was introduced in migration 2→3. All column names are snake_case to match
 * the SQL DDL in the migration; explicit `@ColumnInfo` annotations keep the entity consistent
 * with both migrated and freshly-installed databases.
 *
 * @property id Auto-generated primary key; id = 1 is the permanent main branch.
 * @property name User-visible label for this branch.
 * @property parentBranchId Id of the parent branch, or null for the root.
 * @property checkpointMessageId Last message id inherited from the parent (inclusive).
 *   Messages in the parent with id > this value are not visible in this branch.
 * @property createdAt Unix epoch milliseconds when this branch was created.
 */
@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "parent_branch_id")      val parentBranchId: Long?,
    @ColumnInfo(name = "checkpoint_message_id") val checkpointMessageId: Long?,
    @ColumnInfo(name = "created_at")            val createdAt: Long
)
