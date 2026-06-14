package com.example.petapp.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * [ViewModelProvider.Factory] backed by a Dagger multi-binding map.
 *
 * Each ViewModel registered in [ViewModelModule] contributes an entry to
 * `Map<Class<out ViewModel>, Provider<ViewModel>>`. On [create], this factory looks up
 * the provider by exact class or, failing that, by `isAssignableFrom` to handle subclasses.
 *
 * Provided as `@Singleton` via Dagger; shared between [com.example.petapp.MainActivity] and
 * the Compose tree via [LocalViewModelFactory].
 */
@Singleton
class ViewModelFactory @Inject constructor(
    private val creators: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {

    /**
     * Creates a ViewModel of type [T] using the corresponding Dagger [Provider].
     *
     * @throws IllegalArgumentException if no provider is registered for [modelClass].
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = creators[modelClass]
            ?: creators.entries.firstOrNull { modelClass.isAssignableFrom(it.key) }?.value
            ?: throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        @Suppress("UNCHECKED_CAST")
        return creator.get() as T
    }
}
