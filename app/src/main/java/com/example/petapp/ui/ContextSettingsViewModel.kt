package com.example.petapp.ui

import androidx.lifecycle.ViewModel
import com.example.petapp.domain.model.StrategyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds temporary settings state while the user is on the ContextSettingsScreen.
 * Changes are committed to MainViewModel only when the user confirms.
 */
class ContextSettingsViewModel : ViewModel() {

    private val _selectedStrategy = MutableStateFlow(StrategyType.NONE)
    val selectedStrategy: StateFlow<StrategyType> = _selectedStrategy.asStateFlow()

    private val _keepLastN = MutableStateFlow(MainViewModel.DEFAULT_KEEP_LAST)
    val keepLastN: StateFlow<Int> = _keepLastN.asStateFlow()

    fun init(currentStrategy: StrategyType, currentN: Int) {
        _selectedStrategy.value = currentStrategy
        _keepLastN.value        = currentN
    }

    fun selectStrategy(type: StrategyType) { _selectedStrategy.value = type }

    fun setKeepLastN(n: Int) { _keepLastN.value = n.coerceIn(2, 100) }
}
