package com.assismohanty.awaken.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.repo.UiPrefs
import com.assismohanty.awaken.core.data.repo.ThemeMode
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.engine.ShopRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val appearance: StateFlow<Appearance> = combine(
        hunterDao.observeProfile().map { it?.themeId ?: ShopRules.DEFAULT_THEME_ID }.distinctUntilChanged(),
        uiPrefs.prefs,
    ) { themeId, prefs: UiPrefs -> Appearance(themeId, prefs.hapticsEnabled, prefs.reduceMotion, prefs.themeMode) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
}
