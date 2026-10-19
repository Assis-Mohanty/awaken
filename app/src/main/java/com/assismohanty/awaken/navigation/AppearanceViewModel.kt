package com.assismohanty.awaken.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.engine.ShopRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide look: the equipped Vault theme (GD §9.2). Everything else about the theme lives in `AwakenTheme`. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(hunterDao: HunterDao) : ViewModel() {
    val themeId: StateFlow<String> = hunterDao.observeProfile()
        .map { it?.themeId ?: ShopRules.DEFAULT_THEME_ID }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShopRules.DEFAULT_THEME_ID)
}
