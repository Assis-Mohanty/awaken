package com.assismohanty.awaken.feature.focuslock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.lock.FocusLockSettingsLauncher
import com.assismohanty.awaken.core.lock.FocusLockSystemAccess
import com.assismohanty.awaken.core.reelcounter.ReelSystemAccess
import com.assismohanty.awaken.engine.OemFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One screen for what Android must allow for both accessibility services, Focus Lock and Reel counter. */
data class FocusAccessUiState(
    val focusLockAccessibility: Boolean = false,
    val reelAccessibility: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val oem: OemFamily = OemFamily.OTHER,
)

@HiltViewModel
class FocusAccessViewModel @Inject constructor(
    private val focusAccess: FocusLockSystemAccess,
    private val reelAccess: ReelSystemAccess,
) : ViewModel() {
    private val _state = MutableStateFlow(FocusAccessUiState(oem = focusAccess.oemFamily()))
    val uiState: StateFlow<FocusAccessUiState> = _state.asStateFlow()

    /** Back from an Android settings page: read the switches again. */
    fun refresh() = _state.update {
        it.copy(
            focusLockAccessibility = focusAccess.accessibilityEnabled(),
            reelAccessibility = reelAccess.accessibilityEnabled(),
            batteryUnrestricted = focusAccess.batteryUnrestricted(),
        )
    }
}

@Composable
fun FocusAccessRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: FocusAccessViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    FocusAccessScreen(
        state = state,
        onBack = onBack,
        onOpenAccessibility = { FocusLockSettingsLauncher.openAccessibility(context) },
        onOpenBattery = { FocusLockSettingsLauncher.openBatteryOptimisation(context) },
        onOpenAutostart = { FocusLockSettingsLauncher.openAutostart(context, state.oem) },
        modifier = modifier,
    )
}

@Composable
fun FocusAccessScreen(
    state: FocusAccessUiState,
    onBack: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenAutostart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    LargeTitleScaffold(title = lexString(R.string.access_title), onBack = onBack, modifier = modifier) {
        Text(lexString(R.string.access_note), style = type.body, color = colors.textSecondary)
        SystemWindow(title = lexString(R.string.access_focus_lock), accent = colors.accent, reveal = false) {
            StatusRow(R.string.focus_lock_status_accessibility, state.focusLockAccessibility, onOpenAccessibility)
        }
        SystemWindow(title = lexString(R.string.access_reel_counter), accent = colors.accent, reveal = false) {
            StatusRow(R.string.reel_status_accessibility, state.reelAccessibility, onOpenAccessibility)
        }
        SystemWindow(title = lexString(R.string.reel_status_battery), accent = colors.accent, reveal = false) {
            StatusRow(R.string.focus_lock_battery_optimisation, state.batteryUnrestricted, onOpenBattery)
            Text(lexString(batteryGuide(state.oem)), style = type.body, color = colors.textPrimary)
            SecondaryButton(lexString(R.string.focus_lock_autostart), onOpenAutostart, Modifier.fillMaxWidth())
        }
    }
}

@PhonePreviews
@Composable
private fun FocusAccessPreview() {
    AwakenTheme(reduceMotion = true) {
        FocusAccessScreen(
            FocusAccessUiState(focusLockAccessibility = true, reelAccessibility = false, batteryUnrestricted = false, oem = OemFamily.XIAOMI),
            {}, {}, {}, {},
        )
    }
}

@PhonePreviews
@Composable
private fun FocusAccessAllGoodPreview() {
    AwakenTheme(reduceMotion = true) {
        FocusAccessScreen(FocusAccessUiState(true, true, true), {}, {}, {}, {})
    }
}
