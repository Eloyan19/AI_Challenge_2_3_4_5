package com.example.petapp.di

import com.example.petapp.data.repository.ChatRepositoryImpl
import com.example.petapp.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

/**
 * Dagger module that binds [ChatRepositoryImpl] as the [ChatRepository] implementation.
 *
 * Using `@Binds` instead of `@Provides` avoids a wrapper method body — Dagger generates
 * a direct cast, which is marginally more efficient than a factory call.
 */
@Module
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
