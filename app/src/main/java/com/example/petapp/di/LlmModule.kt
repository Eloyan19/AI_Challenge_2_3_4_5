package com.example.petapp.di

import com.example.petapp.data.DeepSeekLlmService
import com.example.petapp.domain.LlmService
import com.example.petapp.domain.prompt.DefaultPromptBuilder
import com.example.petapp.domain.prompt.PromptBuilder
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

/**
 * Dagger module that binds the active [LlmService] and [PromptBuilder] implementations.
 *
 * To swap providers, change the @Binds target to a different implementation
 * (e.g. GroqLlmService, AnthropicLlmService) without touching any other file.
 */
@Module
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmService(impl: DeepSeekLlmService): LlmService

    @Binds
    @Singleton
    abstract fun bindPromptBuilder(impl: DefaultPromptBuilder): PromptBuilder
}
