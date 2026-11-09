package com.assismohanty.awaken.feature.focuslock

import androidx.compose.foundation.layout.fillMaxWidth
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import androidx.compose.foundation.layout.padding
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
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            AccessRow(lexString(R.string.access_focus_lock), state.focusLockAccessibility, onOpenAccessibility)
            AccessRow(lexString(R.string.access_reel_counter), state.reelAccessibility, onOpenAccessibility)
            AccessRow(lexString(R.string.reel_status_battery), state.batteryUnrestricted, onOpenBattery)
        }
        Text(lexString(batteryGuide(state.oem)), style = type.caption, color = colors.textSecondary)
        SecondaryButton(lexString(R.string.focus_lock_autostart), onOpenAutostart, Modifier.fillMaxWidth())
    }
}

/** One thing Android must allow: a check when it is, "Needs attention" and a Fix button when it is not. */
@Composable
private fun AccessRow(title: String, ok: Boolean, onFix: () -> Unit) {
    val colors = AwakenTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwakenTheme.dimens.touchTarget + Spacing.xs).padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
            Text(
                lexString(if (ok) R.string.access_status_on else R.string.access_status_attention),
                style = AwakenTheme.type.caption,
                color = if (ok) colors.textSecondary else colors.critical,
            )
        }
        if (ok) {
            Icon(AwakenGlyphs.Check, contentDescription = lexString(R.string.cd_access_ok, title), tint = colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.icon))
        } else {
            SecondaryButton(lexString(R.string.focus_lock_fix), onFix)
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
