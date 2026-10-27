package com.assismohanty.awaken.navigation

import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
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
import com.assismohanty.awaken.core.designsystem.lexicon.PresentationMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide look: the equipped Vault theme (GD §9.2), the System / Dark / Light choice and the haptics / reduce-motion toggles. */
data class Appearance(
    val themeId: String = ShopRules.DEFAULT_THEME_ID,
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val presentationMode: PresentationMode = PresentationMode.CLEAN,
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val hunterDao: HunterDao, private val uiPrefs: UiPrefsRepository) : ViewModel() {
    val appearance: StateFlow<Appearance> = combine(hunterDao.observeProfile(), uiPrefs.prefs) { profile, prefs: UiPrefs ->
        Appearance(
            themeId = profile?.themeId ?: ShopRules.DEFAULT_THEME_ID,
            hapticsEnabled = prefs.hapticsEnabled,
            reduceMotion = prefs.reduceMotion,
            themeMode = prefs.themeMode,
            presentationMode = prefs.presentationMode ?: firstLaunchMode(profile),
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    init {
        // GD §14.9: decide the default once and store it, so finishing onboarding never flips a new install to System.
        viewModelScope.launch {
            if (uiPrefs.prefs.first().presentationMode == null) uiPrefs.initPresentationMode(firstLaunchMode(hunterDao.getProfile()))
        }
    }

    /** A hunter who finished onboarding before v1.1 keeps the System look; everyone else starts Clean. */
    private fun firstLaunchMode(profile: HunterProfileEntity?): PresentationMode =
        if (profile?.onboardingDone == true) PresentationMode.SYSTEM else PresentationMode.CLEAN
}
