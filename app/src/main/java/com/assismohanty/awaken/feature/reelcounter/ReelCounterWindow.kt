package com.assismohanty.awaken.feature.reelcounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.lock.FocusLockSettingsLauncher
import com.assismohanty.awaken.core.reelcounter.ReelSystemAccess
import com.assismohanty.awaken.feature.focuslock.StatusRow
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.assismohanty.awaken.reelcounter.inspector.InspectorHooks
import com.assismohanty.awaken.reelcounter.service.ServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Settings Focus window shows for Reel counter. */
data class ReelWindowUiState(
    val enabled: Boolean = false,
    val bubbleEnabled: Boolean = true,
    val accessibilityEnabled: Boolean = false,
    val serviceRunning: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val hasInspector: Boolean = false,
)

sealed interface ReelWindowEvent {
    /** Off switches Reel counter off. On needs the disclosure first, which the Route handles by opening the setup. */
    data class SetEnabled(val enabled: Boolean) : ReelWindowEvent
    data class SetBubble(val enabled: Boolean) : ReelWindowEvent

    /** Back from an Android settings page: read the switches again. */
    data object Resumed : ReelWindowEvent
}

@HiltViewModel
class ReelCounterWindowViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val access: ReelSystemAccess,
    status: ServiceStatus,
    inspector: InspectorHooks,
) : ViewModel() {
    private val system = MutableStateFlow(SystemChecks())
    private val hasInspector = inspector.screen != null

    val uiState: StateFlow<ReelWindowUiState> =
        combine(settings.enabled, settings.bubbleEnabled, status.connected, system) { enabled, bubble, running, checks ->
            ReelWindowUiState(
                enabled = enabled,
                bubbleEnabled = bubble,
                accessibilityEnabled = checks.accessibility,
                serviceRunning = running,
                batteryUnrestricted = checks.battery,
                hasInspector = hasInspector,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReelWindowUiState(hasInspector = hasInspector))

    fun onEvent(event: ReelWindowEvent) {
        when (event) {
            is ReelWindowEvent.SetEnabled -> viewModelScope.launch { settings.setEnabled(event.enabled) }
            is ReelWindowEvent.SetBubble -> viewModelScope.launch { settings.setBubbleEnabled(event.enabled) }
            ReelWindowEvent.Resumed -> system.update { SystemChecks(access.accessibilityEnabled(), access.batteryUnrestricted()) }
        }
    }

    private data class SystemChecks(val accessibility: Boolean = false, val battery: Boolean = false)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Settings → Focus → Reel counter: the switch, what Android still needs, the bubble, limits and stats. */
@Composable
fun ReelCounterWindowRoute(
    onOpenSetup: () -> Unit,
    onOpenLimits: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenInspector: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReelCounterWindowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(ReelWindowEvent.Resumed)
        onPauseOrDispose { }
    }
    ReelCounterWindowContent(
        state = state,
        onToggle = { on -> if (on) onOpenSetup() else viewModel.onEvent(ReelWindowEvent.SetEnabled(false)) },
        onBubble = { viewModel.onEvent(ReelWindowEvent.SetBubble(it)) },
        onFixAccessibility = { FocusLockSettingsLauncher.openAccessibility(context) },
        onFixBattery = { FocusLockSettingsLauncher.openBatteryOptimisation(context) },
        onOpenLimits = onOpenLimits,
        onOpenStats = onOpenStats,
        onOpenPermissions = onOpenPermissions,
        onOpenInspector = onOpenInspector,
        modifier = modifier,
    )
}

@Composable
fun ReelCounterWindowContent(
    state: ReelWindowUiState,
    onToggle: (Boolean) -> Unit,
    onBubble: (Boolean) -> Unit,
    onFixAccessibility: () -> Unit,
    onFixBattery: () -> Unit,
    onOpenLimits: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.reel_title), accent = colors.accent, reveal = false, modifier = modifier) {
        SwitchRow(R.string.reel_setting_title, R.string.reel_setting_note, state.enabled, onToggle)
        if (state.enabled) {
            StatusRow(R.string.reel_status_accessibility, state.accessibilityEnabled, onFixAccessibility)
            StatusRow(R.string.reel_status_running, state.serviceRunning, onFixAccessibility)
            StatusRow(R.string.reel_status_battery, state.batteryUnrestricted, onFixBattery)
            SwitchRow(R.string.reel_bubble_setting, null, state.bubbleEnabled, onBubble)
            SecondaryButton(lexString(R.string.reel_open_limits), onOpenLimits, Modifier.fillMaxWidth())
        }
        SecondaryButton(lexString(R.string.reel_open_stats), onOpenStats, Modifier.fillMaxWidth())
        SecondaryButton(lexString(R.string.reel_open_permissions), onOpenPermissions, Modifier.fillMaxWidth())
        if (state.hasInspector) {
            SecondaryButton(lexString(R.string.reel_open_inspector), onOpenInspector, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwitchRow(title: Int, note: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AwakenTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AwakenTheme.dimens.touchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(lexString(title), style = AwakenTheme.type.label, color = colors.textPrimary)
            note?.let { Text(lexString(it), style = AwakenTheme.type.caption, color = colors.textSecondary) }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.surface),
        )
    }
}

@PhonePreviews
@Composable
private fun ReelWindowOffPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelCounterWindowContent(ReelWindowUiState(), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@PhonePreviews
@Composable
private fun ReelWindowOnPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelCounterWindowContent(
            ReelWindowUiState(enabled = true, accessibilityEnabled = true, serviceRunning = true, batteryUnrestricted = false),
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
