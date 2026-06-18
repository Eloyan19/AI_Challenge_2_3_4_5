package com.example.petapp.di

import android.app.Application
import com.example.petapp.data.local.BranchDao
import com.example.petapp.data.local.ChatDatabase
import com.example.petapp.data.local.ChatMessageDao
import com.example.petapp.data.local.LongTermMemoryDao
import com.example.petapp.data.local.StickyFactsDao
import com.example.petapp.data.local.SummaryDao
import com.example.petapp.data.local.UserProfileDao
import com.example.petapp.data.local.WorkingMemoryDao
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Dagger module that provides the Room [ChatDatabase] and its DAOs.
 *
 * [ChatDatabase] is provided as a `@Singleton` (one instance per process).
 * Each DAO is obtained from the same database instance and does not need its own scope —
 * Room DAO objects are stateless wrappers around the database connection.
 */
@Module
object DatabaseModule {

    /** Creates or opens the Room database; migrations are applied automatically. */
    @Provides
    @Singleton
    fun provideChatDatabase(application: Application): ChatDatabase =
        ChatDatabase.getInstance(application)

    @Provides
    fun provideChatMessageDao(db: ChatDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun provideSummaryDao(db: ChatDatabase): SummaryDao = db.summaryDao()

    @Provides
    fun provideStickyFactsDao(db: ChatDatabase): StickyFactsDao = db.stickyFactsDao()

    @Provides
    fun provideBranchDao(db: ChatDatabase): BranchDao = db.branchDao()

    @Provides
    fun provideWorkingMemoryDao(db: ChatDatabase): WorkingMemoryDao = db.workingMemoryDao()

    @Provides
    fun provideLongTermMemoryDao(db: ChatDatabase): LongTermMemoryDao = db.longTermMemoryDao()

    @Provides
    fun provideUserProfileDao(db: ChatDatabase): UserProfileDao = db.userProfileDao()
}
