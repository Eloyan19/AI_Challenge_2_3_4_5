package com.example.petapp.di

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider

/**
 * [androidx.compose.runtime.CompositionLocal] that propagates the Dagger [ViewModelFactory]
 * down the Compose tree without passing it explicitly through every composable parameter.
 *
 * Provided at the root in [com.example.petapp.MainActivity] via `CompositionLocalProvider`.
 * Any composable that needs to create a ViewModel calls:
 * ```
 * viewModel(factory = LocalViewModelFactory.current)
 * ```
 * Using `staticCompositionLocalOf` instead of `compositionLocalOf` means the factory reference
 * is not reactive — it is set once at startup and never changes, so recomposition is not triggered
 * if the factory instance were somehow replaced (it won't be in practice).
 */
val LocalViewModelFactory = staticCompositionLocalOf<ViewModelProvider.Factory> {
    error("No ViewModelFactory provided")
}
