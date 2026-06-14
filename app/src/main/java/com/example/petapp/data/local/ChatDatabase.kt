package com.example.petapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatMessageEntity::class, SummaryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun summaryDao(): SummaryDao

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

        fun getInstance(context: Context): ChatDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
