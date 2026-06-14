package com.example.petapp.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.example.petapp.BuildConfig
import com.example.petapp.data.DeepSeekApiService
import com.example.petapp.ui.MainViewModel
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dagger module that provides application-level singletons:
 * networking (OkHttp + Retrofit), [SharedPreferences], and [Gson].
 *
 * **OkHttp pool strategy:** A single `@Named("base")` client is created with standard timeouts.
 * The `@Named("api")` client inherits the base client's connection pool via `newBuilder()`,
 * adding a longer read timeout and the DeepSeek authorization header — without creating a
 * second connection pool.
 */
@Module
object AppModule {

    /** Singleton [Gson] instance shared across the app to avoid repeated allocation. */
    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    /** [SharedPreferences] file that persists context strategy settings between sessions. */
    @Provides
    @Singleton
    fun provideSharedPreferences(application: Application): SharedPreferences =
        application.getSharedPreferences(MainViewModel.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Base OkHttpClient with a shared connection pool and conservative 30 s timeouts.
     * All derived clients (e.g. the API client) call `newBuilder()` on this instance
     * so they reuse the same pool instead of creating independent pools.
     */
    @Provides
    @Singleton
    @Named("base")
    fun provideBaseOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * DeepSeek API client — shares the base connection pool, extends the read timeout to
     * 90 s (LLM responses can be slow), and adds the Bearer auth header on every request.
     */
    @Provides
    @Singleton
    @Named("api")
    fun provideApiOkHttpClient(@Named("base") base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
                        .addHeader("Content-Type", "application/json")
                        .build()
                )
            }
            .build()

    /** Retrofit [DeepSeekApiService] wired to the `@Named("api")` client. */
    @Provides
    @Singleton
    fun provideDeepSeekApiService(@Named("api") client: OkHttpClient): DeepSeekApiService =
        Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeepSeekApiService::class.java)

    /** Yandex search username from `BuildConfig.YANDEX_SEARCH_USER` (set in `local.properties`). */
    @Provides
    @Named("yandexUser")
    fun provideYandexUser(): String = BuildConfig.YANDEX_SEARCH_USER

    /** Yandex XML API key from `BuildConfig.YANDEX_SEARCH_KEY` (set in `local.properties`). */
    @Provides
    @Named("yandexKey")
    fun provideYandexKey(): String = BuildConfig.YANDEX_SEARCH_KEY
}
