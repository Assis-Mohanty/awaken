package com.assismohanty.awaken.feature.focuslock

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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.lock.FocusLockSettingsLauncher

/** Settings → Focus (GD 8.2): the switch, what Android still needs, the allowed apps and the Distracting-apps pointer. */
@Composable
fun FocusLockWindowRoute(
    onOpenSetup: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenScreenTime: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FocusLockSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(FocusLockSettingsEvent.Resumed)
        onPauseOrDispose { }
    }
    FocusLockWindowContent(
        state = state,
        onToggle = { on ->
            if (!on || viewModel.canEnableDirectly()) viewModel.onEvent(FocusLockSettingsEvent.SetEnabled(on)) else onOpenSetup()
        },
        onFixAccessibility = { FocusLockSettingsLauncher.openAccessibility(context) },
        onFixBattery = { FocusLockSettingsLauncher.openBatteryOptimisation(context) },
        onFixUsage = { FocusLockSettingsLauncher.openUsageAccess(context) },
        onOpenAllowedApps = onOpenAllowedApps,
        onOpenScreenTime = onOpenScreenTime,
        modifier = modifier,
    )
}

@Composable
fun FocusLockWindowContent(
    state: FocusLockSettingsUiState,
    onToggle: (Boolean) -> Unit,
    onFixAccessibility: () -> Unit,
    onFixBattery: () -> Unit,
    onFixUsage: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenScreenTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.settings_window_focus), accent = colors.accent, reveal = false, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AwakenTheme.dimens.touchTarget)
                .toggleable(value = state.enabled, role = Role.Switch, onValueChange = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(lexString(R.string.focus_lock_setting_title), style = type.label, color = colors.textPrimary)
                Text(lexString(R.string.focus_lock_setting_note), style = type.caption, color = colors.textSecondary)
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.surface),
            )
        }
        if (state.enabled) {
            StatusRow(R.string.focus_lock_status_accessibility, state.accessibilityEnabled, onFixAccessibility)
            StatusRow(R.string.focus_lock_status_running, state.serviceRunning, onFixAccessibility)
            StatusRow(R.string.focus_lock_status_battery, state.batteryUnrestricted, onFixBattery)
            StatusRow(R.string.focus_lock_status_usage, state.usageAccess, onFixUsage)
        }
        Text(lexString(R.string.focus_lock_allowed_apps, state.allowedCount), style = type.label, color = colors.textPrimary)
        Text(lexString(R.string.focus_lock_allowed_apps_note), style = type.caption, color = colors.textSecondary)
        SecondaryButton(lexString(R.string.focus_lock_open_allowed), onOpenAllowedApps, Modifier.fillMaxWidth())
        Text(lexString(R.string.focus_lock_distracting_title), style = type.label, color = colors.textPrimary)
        Text(lexString(R.string.focus_lock_distracting_note), style = type.caption, color = colors.textSecondary)
        SecondaryButton(lexString(R.string.action_open_screen_time), onOpenScreenTime, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun StatusRow(label: Int, ok: Boolean, onFix: () -> Unit) {
    val colors = AwakenTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwakenTheme.dimens.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(lexString(label), style = AwakenTheme.type.label, color = colors.textPrimary)
            Text(
                lexString(if (ok) R.string.focus_lock_status_ok else R.string.focus_lock_status_missing),
                style = AwakenTheme.type.caption,
                color = if (ok) colors.positive else colors.critical,
            )
        }
        if (!ok) SecondaryButton(lexString(R.string.focus_lock_fix), onFix)
    }
}

@PhonePreviews
@Composable
private fun FocusLockWindowPreview() {
    AwakenTheme(reduceMotion = true) {
        FocusLockWindowContent(
            state = FocusLockSettingsUiState(enabled = true, accessibilityEnabled = true, serviceRunning = true, allowedCount = 3),
            onToggle = {}, onFixAccessibility = {}, onFixBattery = {}, onFixUsage = {}, onOpenAllowedApps = {}, onOpenScreenTime = {},
        )
    }
}
