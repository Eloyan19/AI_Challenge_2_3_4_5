package com.example.petapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for the chat application.
 *
 * **Schema version history:**
 * - v1 → v2 ([MIGRATION_1_2]): added the `conversation_summary` table for the Summary strategy.
 * - v2 → v3 ([MIGRATION_2_3]): added `branch_id` column to `chat_messages`, created the
 *   `branches` and `sticky_facts` tables, and seeded the root main branch (id = 1).
 *
 * Obtained via [getInstance] which guarantees a single instance per process using
 * double-checked locking. Provided to DI via [com.example.petapp.di.DatabaseModule].
 */
@Database(
    entities = [
        ChatMessageEntity::class,
        SummaryEntity::class,
        BranchEntity::class,
        StickyFactsEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun summaryDao(): SummaryDao
    abstract fun branchDao(): BranchDao
    abstract fun stickyFactsDao(): StickyFactsDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_summary` " +
                    "(`id` INTEGER NOT NULL, `content` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add branchId to chat_messages (all existing rows → main branch 1)
                db.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN branch_id INTEGER NOT NULL DEFAULT 1"
                )

                // Branches table (tree of conversation forks)
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `branches` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `parent_branch_id` INTEGER,
                        `checkpoint_message_id` INTEGER,
                        `created_at` INTEGER NOT NULL
                    )"""
                )

                // Insert the default root branch so foreign key references are valid
                db.execSQL(
                    "INSERT OR IGNORE INTO branches (id, name, parent_branch_id, checkpoint_message_id, created_at) " +
                    "VALUES (1, 'main', NULL, NULL, ${System.currentTimeMillis()})"
                )

                // Sticky facts (singleton row id=1)
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sticky_facts` (
                        `id` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
            }
        }

        /**
         * Returns the singleton [ChatDatabase] instance, creating it on first call.
         *
         * Thread-safe via double-checked locking with a `@Volatile` field.
         * Migrations are applied automatically by Room when the on-disk schema version
         * is lower than the current [version].
         */
        fun getInstance(context: Context): ChatDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            // Seed the root branch on fresh install.
                            // MIGRATION_2_3 handles upgrades; this covers first-time installs
                            // where Room creates the schema directly at version 3 (no migrations run).
                            db.execSQL(
                                "INSERT OR IGNORE INTO branches " +
                                "(id, name, parent_branch_id, checkpoint_message_id, created_at) " +
                                "VALUES (1, 'main', NULL, NULL, ${System.currentTimeMillis()})"
                            )
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
