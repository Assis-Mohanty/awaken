package com.assismohanty.awaken.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.repo.ThemeMode
import com.assismohanty.awaken.core.data.repo.UiPrefs
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.engine.ShopRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** App-wide look: the equipped Vault theme (GD §9.2), the System / Dark / Light choice and the haptics / reduce-motion toggles. */
data class Appearance(
    val themeId: String = ShopRules.DEFAULT_THEME_ID,
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(hunterDao: HunterDao, uiPrefs: UiPrefsRepository) : ViewModel() {
    val appearance: StateFlow<Appearance> = combine(hunterDao.observeProfile(), uiPrefs.prefs) { profile, prefs: UiPrefs ->
        Appearance(
            themeId = profile?.themeId ?: ShopRules.DEFAULT_THEME_ID,
            hapticsEnabled = prefs.hapticsEnabled,
            reduceMotion = prefs.reduceMotion,
            themeMode = prefs.themeMode,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
}
