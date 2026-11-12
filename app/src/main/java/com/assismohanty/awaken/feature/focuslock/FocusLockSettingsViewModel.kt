package com.assismohanty.awaken.feature.focuslock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.domain.FocusLockStatus
import com.assismohanty.awaken.core.lock.FocusLockSystemAccess
import com.assismohanty.awaken.core.protect.ProtectServiceStatus
import com.assismohanty.awaken.core.protect.stalled
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Settings Focus window shows (GD 8.2). */
data class FocusLockSettingsUiState(
    val enabled: Boolean = false,
    val permissionsReady: Boolean = false,
    val serviceRunning: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val usageAccess: Boolean = false,
    val allowedCount: Int = 0,
)

sealed interface FocusLockSettingsEvent {
    /** Off switches Focus Lock off; on needs the disclosure first, which the Route handles by opening the setup. */
    data class SetEnabled(val enabled: Boolean) : FocusLockSettingsEvent

    /** Back from an Android settings page: read the switches again. */
    data object Resumed : FocusLockSettingsEvent
}

@HiltViewModel
class FocusLockSettingsViewModel @Inject constructor(
    private val prefs: UiPrefsRepository,
    private val access: FocusLockSystemAccess,
    private val usage: UsageGateway,
    status: FocusLockStatus,
    service: ProtectServiceStatus,
) : ViewModel() {
    private val system = MutableStateFlow(SystemChecks())

    val uiState: StateFlow<FocusLockSettingsUiState> =
        combine(prefs.prefs, status.serviceConnected, system, service.running, service.wanted) { p, connected, checks, _, _ ->
            FocusLockSettingsUiState(
                enabled = p.focusLock,
                permissionsReady = checks.permissions,
                // Permissions alone are not "running": a session whose service Android refused to start is not covered.
                serviceRunning = connected && !service.stalled,
                batteryUnrestricted = checks.battery,
                usageAccess = checks.usage,
                allowedCount = p.allowedApps.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FocusLockSettingsUiState())

    /** True when turning Focus Lock on can skip the setup: both Android permissions are already on. */
    fun canEnableDirectly(): Boolean = system.value.permissions

    fun onEvent(event: FocusLockSettingsEvent) {
        when (event) {
            is FocusLockSettingsEvent.SetEnabled -> viewModelScope.launch { prefs.setFocusLock(event.enabled) }
            FocusLockSettingsEvent.Resumed -> system.update {
                SystemChecks(access.permissionsReady(), access.batteryUnrestricted(), usage.hasAccess())
            }
        }
    }

    /** Opens the Android page for whichever permission is still missing. */
    fun openPermissions() = access.openMissingPermission()

    private data class SystemChecks(val permissions: Boolean = false, val battery: Boolean = false, val usage: Boolean = false)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
