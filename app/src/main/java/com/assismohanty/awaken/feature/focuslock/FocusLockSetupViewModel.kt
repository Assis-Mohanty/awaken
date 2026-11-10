package com.assismohanty.awaken.feature.focuslock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.lock.FocusLockSystemAccess
import com.assismohanty.awaken.engine.OemFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep { EXPLAIN, ENABLE, BATTERY, DONE }

data class FocusLockSetupUiState(
    val step: SetupStep = SetupStep.EXPLAIN,
    val permissionsReady: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val oem: OemFamily = OemFamily.OTHER,
    val finished: Boolean = false,
)

sealed interface FocusLockSetupEvent {
    /** The hunter read the disclosure and agreed: Focus Lock is switched on in AWAKEN. */
    data object Agree : FocusLockSetupEvent
    data object NotNow : FocusLockSetupEvent
    data object Next : FocusLockSetupEvent
    data object Finish : FocusLockSetupEvent

    /** Back from an Android settings page: read the switches again. */
    data object Resumed : FocusLockSetupEvent
}

/** Focus Lock setup (GD 8.2): disclosure, usage access and appear on top, then the battery steps. */
@HiltViewModel
class FocusLockSetupViewModel @Inject constructor(
    private val prefs: UiPrefsRepository,
    private val access: FocusLockSystemAccess,
) : ViewModel() {
    private val _state = MutableStateFlow(FocusLockSetupUiState(oem = access.oemFamily()))
    val uiState: StateFlow<FocusLockSetupUiState> = _state.asStateFlow()

    fun onEvent(event: FocusLockSetupEvent) {
        when (event) {
            FocusLockSetupEvent.Agree -> viewModelScope.launch {
                prefs.setFocusLock(true)
                _state.update { it.copy(step = SetupStep.ENABLE) }
                refresh()
            }
            FocusLockSetupEvent.NotNow -> _state.update { it.copy(finished = true) }
            FocusLockSetupEvent.Next -> _state.update { it.copy(step = SetupStep.BATTERY) }
            FocusLockSetupEvent.Finish -> _state.update { it.copy(step = SetupStep.DONE) }
            FocusLockSetupEvent.Resumed -> refresh()
        }
    }

    fun closed() = _state.update { it.copy(finished = true) }

    /** Opens the Android page for whichever permission is still missing. */
    fun openPermissions() = access.openMissingPermission()

    private fun refresh() = _state.update {
        it.copy(permissionsReady = access.permissionsReady(), batteryUnrestricted = access.batteryUnrestricted())
    }
}
