package com.example.petapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

        fun getInstance(context: Context): ChatDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
