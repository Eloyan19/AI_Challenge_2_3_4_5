package com.example.petapp.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.example.petapp.domain.model.StrategyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for the context-settings screen.
 *
 * Holds the user's in-progress strategy selection and window-size value while they
 * browse options in [com.example.petapp.ui.screens.ContextSettingsScreen].
 * State is **not** written back to [SharedPreferences] or applied to the agent until
 * the user taps "Применить" — at which point [MainViewModel.applyStrategyConfig] is called.
 *
 * Reads the currently saved settings from [prefs] on construction so the UI starts with the
 * values that are already active, rather than defaults.
 *
 * @param prefs Shared preferences file named [MainViewModel.PREFS_NAME].
 */
class ContextSettingsViewModel @Inject constructor(
    prefs: SharedPreferences
) : ViewModel() {

    private val _selectedStrategy = MutableStateFlow(
        runCatching {
            StrategyType.valueOf(prefs.getString(MainViewModel.KEY_STRATEGY, StrategyType.NONE.name)!!)
        }.getOrDefault(StrategyType.NONE)
    )

    /** The strategy option currently highlighted in the UI (not yet applied to the agent). */
    val selectedStrategy: StateFlow<StrategyType> = _selectedStrategy.asStateFlow()

    private val _keepLastN = MutableStateFlow(
        prefs.getInt(MainViewModel.KEY_KEEP_LAST, MainViewModel.DEFAULT_KEEP_LAST)
    )

    /** The window-size value currently shown in the text field (not yet applied to the agent). */
    val keepLastN: StateFlow<Int> = _keepLastN.asStateFlow()

    /** Updates the highlighted strategy option without applying it. */
    fun selectStrategy(type: StrategyType) { _selectedStrategy.value = type }

    /**
     * Updates the window-size value, clamping it to the valid range [2, 100].
     * Invalid text-field input is rejected at the call site before this is called.
     */
    fun setKeepLastN(n: Int) { _keepLastN.value = n.coerceIn(2, 100) }
}
