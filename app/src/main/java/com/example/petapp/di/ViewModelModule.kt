package com.example.petapp.di

import androidx.lifecycle.ViewModel
import com.example.petapp.ui.ContextSettingsViewModel
import com.example.petapp.ui.MainViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

/**
 * Dagger module that registers ViewModels into the multi-binding map consumed by [ViewModelFactory].
 *
 * Each ViewModel is bound with `@IntoMap` + `@ViewModelKey` so [ViewModelFactory] can look up the
 * correct [javax.inject.Provider] by class at runtime. Adding a new ViewModel requires only a new
 * `@Binds @IntoMap @ViewModelKey(...)` entry here — no other DI wiring is needed.
 */
@Module
abstract class ViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    abstract fun bindMainViewModel(vm: MainViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ContextSettingsViewModel::class)
    abstract fun bindContextSettingsViewModel(vm: ContextSettingsViewModel): ViewModel
}
