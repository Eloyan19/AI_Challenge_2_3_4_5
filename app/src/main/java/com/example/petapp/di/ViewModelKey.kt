package com.example.petapp.di

import androidx.lifecycle.ViewModel
import dagger.MapKey
import kotlin.reflect.KClass

/**
 * Dagger map key that uses a ViewModel's [KClass] as the key in the multi-binding map.
 *
 * Used by [ViewModelModule] to register ViewModels and by [ViewModelFactory] to look them up.
 * The `@MapKey` annotation tells Dagger to generate a `Map<Class<out ViewModel>, Provider<ViewModel>>`
 * binding where each entry's key is the class passed to [value].
 */
@MapKey
annotation class ViewModelKey(val value: KClass<out ViewModel>)
