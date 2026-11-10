package com.assismohanty.awaken.feature.focuslock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.assismohanty.awaken.engine.OemFamily
import com.assismohanty.awaken.engine.model.Rank

/** Steps after the disclosure: the two Android permissions, then the battery page. */
private const val PROGRESS_STEPS = 2

@Composable
fun FocusLockSetupRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: FocusLockSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(FocusLockSetupEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    FocusLockSetupScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = viewModel::closed,
        onOpenPermissions = viewModel::openPermissions,
        onOpenBattery = { FocusLockSettingsLauncher.openBatteryOptimisation(context) },
        onOpenAutostart = { FocusLockSettingsLauncher.openAutostart(context, state.oem) },
        modifier = modifier,
    )
}

/** Focus Lock setup (GD 8.2): disclosure first, then the Android switch, then the battery steps. */
@Composable
fun FocusLockSetupScreen(
    state: FocusLockSetupUiState,
    onEvent: (FocusLockSetupEvent) -> Unit,
    onClose: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenAutostart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val full = Modifier.fillMaxWidth()
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(
            title = lexString(R.string.focus_lock_title),
            subtitle = if (state.step == SetupStep.EXPLAIN) lexString(R.string.focus_lock_explain_title) else null,
            onBack = onClose,
        ) {
            when (state.step) {
                SetupStep.EXPLAIN -> SystemWindow(title = null, accent = colors.accent, reveal = false) {
                    listOf(
                        R.string.focus_lock_explain_intro,
                        R.string.focus_lock_explain_how,
                        R.string.focus_lock_explain_listens,
                        R.string.focus_lock_explain_reads_nothing,
                        R.string.focus_lock_explain_idle,
                        R.string.focus_lock_explain_never,
                        R.string.focus_lock_explain_stores,
                        R.string.focus_lock_explain_off,
                    ).forEach { Text(lexString(it), style = type.body, color = colors.textPrimary) }
                    PrimaryButton(lexString(R.string.focus_lock_continue), { onEvent(FocusLockSetupEvent.Agree) }, full)
                    QuietButton(lexString(R.string.focus_lock_not_now), { onEvent(FocusLockSetupEvent.NotNow) }, full)
                }
                SetupStep.ENABLE -> SystemWindow(title = lexString(R.string.focus_lock_enable_title), accent = colors.accent, reveal = false) {
                    Text(lexString(R.string.focus_lock_step_count, 1, PROGRESS_STEPS), style = type.caption, color = colors.textSecondary)
                    Text(lexString(R.string.focus_lock_enable_body), style = type.body, color = colors.textPrimary)
                    Text(
                        lexString(if (state.permissionsReady) R.string.focus_lock_enabled_yes else R.string.focus_lock_enabled_no),
                        style = type.label,
                        color = if (state.permissionsReady) colors.positive else colors.textSecondary,
                    )
                    PrimaryButton(lexString(R.string.focus_lock_open_permissions), onOpenPermissions, full)
                    SecondaryButton(lexString(R.string.focus_lock_next), { onEvent(FocusLockSetupEvent.Next) }, full)
                }
                SetupStep.BATTERY -> SystemWindow(title = lexString(R.string.focus_lock_battery_title), accent = colors.accent, reveal = false) {
                    Text(lexString(R.string.focus_lock_step_count, 2, PROGRESS_STEPS), style = type.caption, color = colors.textSecondary)
                    Text(lexString(R.string.focus_lock_battery_body), style = type.body, color = colors.textPrimary)
                    Text(lexString(batteryGuide(state.oem)), style = type.body, color = colors.textPrimary)
                    Text(
                        lexString(if (state.batteryUnrestricted) R.string.focus_lock_battery_yes else R.string.focus_lock_battery_no),
                        style = type.label,
                        color = if (state.batteryUnrestricted) colors.positive else colors.textSecondary,
                    )
                    SecondaryButton(lexString(R.string.focus_lock_battery_optimisation), onOpenBattery, full)
                    SecondaryButton(lexString(R.string.focus_lock_autostart), onOpenAutostart, full)
                    PrimaryButton(lexString(R.string.focus_lock_finish), { onEvent(FocusLockSetupEvent.Finish) }, full)
                }
                SetupStep.DONE -> SystemWindow(title = lexString(R.string.focus_lock_done_title), accent = colors.positive, reveal = false) {
                    Text(lexString(R.string.focus_lock_done_body), style = type.body, color = colors.textPrimary)
                    PrimaryButton(lexString(R.string.focus_lock_close), onClose, full)
                }
            }
        }
    }
}

internal fun batteryGuide(family: OemFamily): Int = when (family) {
    OemFamily.XIAOMI -> R.string.focus_lock_battery_xiaomi
    OemFamily.SAMSUNG -> R.string.focus_lock_battery_samsung
    OemFamily.OPPO -> R.string.focus_lock_battery_oppo
    OemFamily.ONEPLUS -> R.string.focus_lock_battery_oneplus
    OemFamily.VIVO -> R.string.focus_lock_battery_vivo
    OemFamily.HUAWEI -> R.string.focus_lock_battery_huawei
    OemFamily.OTHER -> R.string.focus_lock_battery_generic
}

@PhonePreviews
@Composable
private fun SetupExplainPreview() {
    AwakenTheme(reduceMotion = true) {
        FocusLockSetupScreen(FocusLockSetupUiState(), {}, {}, {}, {}, {})
    }
}

@PhonePreviews
@Composable
private fun SetupBatteryPreview() {
    AwakenTheme(reduceMotion = true) {
        FocusLockSetupScreen(
            FocusLockSetupUiState(step = SetupStep.BATTERY, permissionsReady = true, oem = OemFamily.XIAOMI),
            {}, {}, {}, {}, {},
        )
    }
}
