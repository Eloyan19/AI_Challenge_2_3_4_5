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
 * - v3 → v4 ([MIGRATION_3_4]): added `working_memory` and `long_term_memory` tables for the
 *   MemoryLayers strategy.
 * - v4 → v5 ([MIGRATION_4_5]): added `user_profiles` table for named user instruction sets.
 * - v5 → v6 ([MIGRATION_5_6]): added `task_plan` singleton table for Task State Machine persistence.
 * - v6 → v7 ([MIGRATION_6_7]): added indices on `chat_messages(branch_id)` and `chat_messages(turn_id)`
 *   for faster branch reconstruction and turn grouping queries.
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
        WorkingMemoryEntity::class,
        LongTermMemoryEntity::class,
        UserProfileEntity::class,
        TaskPlanEntity::class,
    ],
    version = 7,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun summaryDao(): SummaryDao
    abstract fun branchDao(): BranchDao
    abstract fun stickyFactsDao(): StickyFactsDao
    abstract fun workingMemoryDao(): WorkingMemoryDao
    abstract fun longTermMemoryDao(): LongTermMemoryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun taskPlanDao(): TaskPlanDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `working_memory` " +
                    "(`id` INTEGER NOT NULL, `content` TEXT NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `long_term_memory` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`category` TEXT NOT NULL, `key_name` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profiles` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `instructions` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_plan` " +
                    "(`id` INTEGER NOT NULL DEFAULT 1, `user_input` TEXT NOT NULL, " +
                    "`plan` TEXT NOT NULL, `critique` TEXT, " +
                    "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_branch_id` ON `chat_messages` (`branch_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_turnId` ON `chat_messages` (`turnId`)")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            // Seed the root branch on fresh install.
                            // MIGRATION_2_3 handles upgrades; this covers first-time installs
                            // where Room creates the schema directly at version 4 (no migrations run).
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
