package com.assismohanty.awaken.feature.reelcounter

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.lock.FocusLockSettingsLauncher
import com.assismohanty.awaken.core.reelcounter.ReelSystemAccess
import com.assismohanty.awaken.engine.OemFamily
import com.assismohanty.awaken.feature.focuslock.batteryGuide
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReelSetupStep { EXPLAIN, ENABLE, BATTERY, DONE }

data class ReelSetupUiState(
    val step: ReelSetupStep = ReelSetupStep.EXPLAIN,
    val accessibilityEnabled: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val oem: OemFamily = OemFamily.OTHER,
    val finished: Boolean = false,
)

sealed interface ReelSetupEvent {
    /** The hunter read the disclosure and agreed: Reel counter is switched on in AWAKEN. */
    data object Agree : ReelSetupEvent
    data object NotNow : ReelSetupEvent
    data object Next : ReelSetupEvent
    data object Finish : ReelSetupEvent

    /** Back from an Android settings page: read the switches again. */
    data object Resumed : ReelSetupEvent
}

/** Reel counter setup: the disclosure, the Android accessibility switch, then the battery steps. */
@HiltViewModel
class ReelCounterSetupViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val access: ReelSystemAccess,
) : ViewModel() {
    private val _state = MutableStateFlow(ReelSetupUiState(oem = access.oemFamily()))
    val uiState: StateFlow<ReelSetupUiState> = _state.asStateFlow()

    fun onEvent(event: ReelSetupEvent) {
        when (event) {
            ReelSetupEvent.Agree -> viewModelScope.launch {
                settings.setEnabled(true)
                _state.update { it.copy(step = ReelSetupStep.ENABLE) }
                refresh()
            }
            ReelSetupEvent.NotNow -> _state.update { it.copy(finished = true) }
            ReelSetupEvent.Next -> _state.update { it.copy(step = ReelSetupStep.BATTERY) }
            ReelSetupEvent.Finish -> _state.update { it.copy(step = ReelSetupStep.DONE) }
            ReelSetupEvent.Resumed -> refresh()
        }
    }

    fun closed() = _state.update { it.copy(finished = true) }

    private fun refresh() = _state.update {
        it.copy(accessibilityEnabled = access.accessibilityEnabled(), batteryUnrestricted = access.batteryUnrestricted())
    }
}

private const val PROGRESS_STEPS = 2

@Composable
fun ReelCounterSetupRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReelCounterSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(ReelSetupEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    ReelCounterSetupScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = viewModel::closed,
        onOpenAccessibility = { FocusLockSettingsLauncher.openAccessibility(context) },
        onOpenAppInfo = { FocusLockSettingsLauncher.openAppDetails(context) },
        onOpenBattery = { FocusLockSettingsLauncher.openBatteryOptimisation(context) },
        onOpenAutostart = { FocusLockSettingsLauncher.openAutostart(context, state.oem) },
        modifier = modifier,
    )
}

@Composable
fun ReelCounterSetupScreen(
    state: ReelSetupUiState,
    onEvent: (ReelSetupEvent) -> Unit,
    onClose: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenAutostart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val full = Modifier.fillMaxWidth()
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(title = lexString(R.string.reel_title), onBack = onClose) {
            when (state.step) {
                ReelSetupStep.EXPLAIN -> SystemWindow(title = lexString(R.string.reel_explain_title), accent = colors.accent, reveal = false) {
                    listOf(R.string.reel_explain_1, R.string.reel_explain_2, R.string.reel_explain_3)
                        .forEach { Text(lexString(it), style = type.body, color = colors.textPrimary) }
                    PrimaryButton(lexString(R.string.reel_continue), { onEvent(ReelSetupEvent.Agree) }, full)
                    QuietButton(lexString(R.string.reel_not_now), { onEvent(ReelSetupEvent.NotNow) }, full)
                }
                ReelSetupStep.ENABLE -> SystemWindow(title = lexString(R.string.reel_enable_title), accent = colors.accent, reveal = false) {
                    Text(lexString(R.string.focus_lock_step_count, 1, PROGRESS_STEPS), style = type.caption, color = colors.textSecondary)
                    Text(lexString(R.string.reel_enable_body), style = type.body, color = colors.textPrimary)
                    Text(
                        lexString(if (state.accessibilityEnabled) R.string.reel_enabled_yes else R.string.reel_enabled_no),
                        style = type.label,
                        color = if (state.accessibilityEnabled) colors.positive else colors.critical,
                    )
                    PrimaryButton(lexString(R.string.focus_lock_open_accessibility), onOpenAccessibility, full)
                    Text(lexString(R.string.focus_lock_restricted_note), style = type.caption, color = colors.textSecondary)
                    SecondaryButton(lexString(R.string.focus_lock_open_app_info), onOpenAppInfo, full)
                    SecondaryButton(lexString(R.string.focus_lock_next), { onEvent(ReelSetupEvent.Next) }, full)
                }
                ReelSetupStep.BATTERY -> SystemWindow(title = lexString(R.string.reel_battery_title), accent = colors.accent, reveal = false) {
                    Text(lexString(R.string.focus_lock_step_count, 2, PROGRESS_STEPS), style = type.caption, color = colors.textSecondary)
                    Text(lexString(R.string.reel_battery_body), style = type.body, color = colors.textPrimary)
                    Text(lexString(batteryGuide(state.oem)), style = type.body, color = colors.textPrimary)
                    Text(
                        lexString(if (state.batteryUnrestricted) R.string.focus_lock_battery_yes else R.string.focus_lock_battery_no),
                        style = type.label,
                        color = if (state.batteryUnrestricted) colors.positive else colors.critical,
                    )
                    SecondaryButton(lexString(R.string.focus_lock_battery_optimisation), onOpenBattery, full)
                    SecondaryButton(lexString(R.string.focus_lock_autostart), onOpenAutostart, full)
                    PrimaryButton(lexString(R.string.focus_lock_finish), { onEvent(ReelSetupEvent.Finish) }, full)
                }
                ReelSetupStep.DONE -> SystemWindow(title = lexString(R.string.reel_done_title), accent = colors.positive, reveal = false) {
                    Text(lexString(R.string.reel_done_body), style = type.body, color = colors.textPrimary)
                    PrimaryButton(lexString(R.string.focus_lock_close), onClose, full)
                }
            }
        }
    }
}

@PhonePreviews
@Composable
private fun ReelSetupExplainPreview() {
    AwakenTheme(reduceMotion = true) { ReelCounterSetupScreen(ReelSetupUiState(), {}, {}, {}, {}, {}, {}) }
}

@PhonePreviews
@Composable
private fun ReelSetupEnablePreview() {
    AwakenTheme(reduceMotion = true) {
        ReelCounterSetupScreen(ReelSetupUiState(step = ReelSetupStep.ENABLE), {}, {}, {}, {}, {}, {})
    }
}

@PhonePreviews
@Composable
private fun ReelSetupBatteryPreview() {
    AwakenTheme(reduceMotion = true) {
        ReelCounterSetupScreen(
            ReelSetupUiState(step = ReelSetupStep.BATTERY, accessibilityEnabled = true, oem = OemFamily.XIAOMI),
            {}, {}, {}, {}, {}, {},
        )
    }
}

@PhonePreviews
@Composable
private fun ReelSetupDonePreview() {
    AwakenTheme(reduceMotion = true) {
        ReelCounterSetupScreen(ReelSetupUiState(step = ReelSetupStep.DONE, accessibilityEnabled = true), {}, {}, {}, {}, {}, {})
    }
}
