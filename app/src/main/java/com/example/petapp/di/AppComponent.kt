package com.example.petapp.di

import android.app.Application
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

/**
 * Root Dagger component for the application.
 *
 * Wires together [AppModule], [DatabaseModule], [RepositoryModule], and [ViewModelModule]
 * into a single `@Singleton` scope that lives for the lifetime of the [android.app.Application].
 *
 * Created in [com.example.petapp.App.onCreate] via the [Factory] and stored there so that
 * [com.example.petapp.MainActivity] can obtain the [ViewModelFactory] without a service locator.
 */
@Singleton
@Component(
    modules = [
        AppModule::class,
        DatabaseModule::class,
        RepositoryModule::class,
        ViewModelModule::class,
        LlmModule::class,
    ]
)
interface AppComponent {

    /** Returns the [ViewModelFactory] that the Compose UI uses to create ViewModels. */
    fun viewModelFactory(): ViewModelFactory

    /** Factory that injects the [Application] instance as a `@BindsInstance`. */
    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): AppComponent
    }
}
