package com.assismohanty.awaken.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.repo.ThemeMode
import com.assismohanty.awaken.core.data.repo.UiPrefs
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide look: the System / Dark / Light choice and the haptics / reduce-motion toggles. */
data class Appearance(
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(uiPrefs: UiPrefsRepository) : ViewModel() {
    val appearance: StateFlow<Appearance> = uiPrefs.prefs.map { prefs: UiPrefs ->
        Appearance(
            hapticsEnabled = prefs.hapticsEnabled,
            reduceMotion = prefs.reduceMotion,
            themeMode = prefs.themeMode,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
}
