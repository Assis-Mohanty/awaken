package com.assismohanty.awaken.feature.protect

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
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
import com.assismohanty.awaken.core.data.repo.PauseSettings
import com.assismohanty.awaken.core.data.repo.PauseStore
import com.assismohanty.awaken.core.designsystem.components.AwakenSwitch
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.HourPicker
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.lock.FocusLockSettingsLauncher
import com.assismohanty.awaken.core.protect.ProtectSystemAccess
import com.assismohanty.awaken.engine.OemFamily
import com.assismohanty.awaken.engine.PauseConfig
import com.assismohanty.awaken.engine.PauseRules
import com.assismohanty.awaken.feature.focuslock.StatusRow
import com.assismohanty.awaken.feature.focuslock.batteryGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PauseSettingsUiState(
    val settings: PauseSettings = PauseSettings(),
    val usageAccess: Boolean = false,
    val appearOnTop: Boolean = false,
    val notifications: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val oem: OemFamily = OemFamily.OTHER,
) {
    val permissionsReady: Boolean get() = usageAccess && appearOnTop

    /** Pause is on, an app is chosen and Android allows it: the service is running. */
    val watching: Boolean get() = settings.config.enabled && settings.config.targets.isNotEmpty() && permissionsReady
}

sealed interface PauseSettingsEvent {
    data object Resumed : PauseSettingsEvent
    data class SetEnabled(val on: Boolean) : PauseSettingsEvent
    data class SetDelay(val seconds: Int) : PauseSettingsEvent
    data class SetProgressive(val on: Boolean) : PauseSettingsEvent
    data class SetCooldown(val minutes: Int) : PauseSettingsEvent
    data class SetRequireTap(val on: Boolean) : PauseSettingsEvent
    data class SetIntention(val on: Boolean) : PauseSettingsEvent
    data class SetHoursOn(val on: Boolean) : PauseSettingsEvent
    data class SetStartHour(val hour: Int) : PauseSettingsEvent
    data class SetEndHour(val hour: Int) : PauseSettingsEvent
    data class ToggleDay(val day: DayOfWeek) : PauseSettingsEvent
}

/** What the Pause screen can send the hunter to fix in Android's own settings. */
data class PauseSystemActions(
    val fixUsage: () -> Unit = {},
    val fixOverlay: () -> Unit = {},
    val fixNotifications: () -> Unit = {},
    val fixBattery: () -> Unit = {},
    val openAutostart: () -> Unit = {},
    val openPopupSettings: () -> Unit = {},
)

@HiltViewModel
class PauseSettingsViewModel @Inject constructor(
    private val store: PauseStore,
    private val access: ProtectSystemAccess,
) : ViewModel() {
    private val system = MutableStateFlow(readSystem())

    val uiState: StateFlow<PauseSettingsUiState> = combine(store.settings, system) { settings, checks ->
        PauseSettingsUiState(settings, checks.usage, checks.overlay, checks.notifications, checks.battery, checks.oem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PauseSettingsUiState())

    fun onEvent(event: PauseSettingsEvent) {
        viewModelScope.launch {
            when (event) {
                PauseSettingsEvent.Resumed -> system.value = readSystem()
                is PauseSettingsEvent.SetEnabled -> store.setEnabled(event.on)
                is PauseSettingsEvent.SetDelay -> if (event.seconds in PauseRules.DELAY_CHOICES) store.setDelay(event.seconds)
                is PauseSettingsEvent.SetProgressive -> store.setProgressive(event.on)
                is PauseSettingsEvent.SetCooldown -> if (event.minutes in PauseRules.COOLDOWN_CHOICES) store.setCooldown(event.minutes)
                is PauseSettingsEvent.SetRequireTap -> store.setRequireTap(event.on)
                is PauseSettingsEvent.SetIntention -> store.setIntention(event.on)
                is PauseSettingsEvent.SetHoursOn -> store.setHoursOn(event.on)
                is PauseSettingsEvent.SetStartHour -> store.current().let { store.setHours(event.hour, it.endHour) }
                is PauseSettingsEvent.SetEndHour -> store.current().let { store.setHours(it.startHour, event.hour) }
                is PauseSettingsEvent.ToggleDay -> {
                    val days = store.current().days
                    // At least one day stays selected, or the hours could never open.
                    val next = if (event.day in days) days - event.day else days + event.day
                    if (next.isNotEmpty()) store.setDays(next)
                }
            }
        }
    }

    private fun readSystem() = SystemChecks(access.usageAccess(), access.canAppearOnTop(), access.notificationsAllowed(), access.batteryUnrestricted(), access.oem())

    private data class SystemChecks(
        val usage: Boolean,
        val overlay: Boolean,
        val notifications: Boolean,
        val battery: Boolean,
        val oem: OemFamily,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun PauseSettingsRoute(
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PauseSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onEvent(PauseSettingsEvent.Resumed)
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(PauseSettingsEvent.Resumed)
        onPauseOrDispose { }
    }
    PauseSettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenApps = onOpenApps,
        onOpenStats = onOpenStats,
        actions = PauseSystemActions(
            fixUsage = { FocusLockSettingsLauncher.openUsageAccess(context) },
            fixOverlay = { FocusLockSettingsLauncher.openOverlayPermission(context) },
            fixNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    FocusLockSettingsLauncher.openAppDetails(context)
                }
            },
            fixBattery = { FocusLockSettingsLauncher.openBatteryOptimisation(context) },
            openAutostart = { FocusLockSettingsLauncher.openAutostart(context, state.oem) },
            openPopupSettings = { FocusLockSettingsLauncher.openAppDetails(context) },
        ),
        modifier = modifier,
    )
}

/** Pause (GD §19.1): the switch, the apps, the length of the pause and its quiet time, the hours, and what Android must allow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseSettingsScreen(
    state: PauseSettingsUiState,
    onEvent: (PauseSettingsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenStats: () -> Unit,
    actions: PauseSystemActions,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val config: PauseConfig = state.settings.config
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(title = lexString(R.string.pause_title), subtitle = lexString(R.string.pause_subtitle), onBack = onBack) {
            SystemWindow(title = null, accent = colors.accent, reveal = false) {
                SwitchRow(R.string.pause_switch_title, R.string.pause_switch_note, config.enabled) { onEvent(PauseSettingsEvent.SetEnabled(it)) }
                Text(
                    lexString(if (state.watching) R.string.pause_ready else R.string.pause_not_ready),
                    style = type.caption,
                    color = if (state.watching) colors.positive else colors.textSecondary,
                )
            }
            SystemWindow(title = lexString(R.string.pause_setup_title), accent = colors.accent, reveal = false) {
                if (!state.permissionsReady) Text(lexString(R.string.pause_disclosure), style = type.body, color = colors.textPrimary)
                StatusRow(R.string.pause_perm_usage, state.usageAccess, actions.fixUsage)
                StatusRow(R.string.pause_perm_overlay, state.appearOnTop, actions.fixOverlay)
                StatusRow(R.string.pause_perm_notifications, state.notifications, actions.fixNotifications)
                if (state.oem == OemFamily.XIAOMI) {
                    Text(lexString(R.string.pause_perm_popup), style = type.label, color = colors.textPrimary)
                    Text(lexString(R.string.pause_perm_popup_note), style = type.caption, color = colors.textSecondary)
                    SecondaryButton(lexString(R.string.focus_lock_fix), actions.openPopupSettings, Modifier.fillMaxWidth())
                }
                StatusRow(R.string.focus_lock_status_battery, state.batteryUnrestricted, actions.fixBattery)
                Text(lexString(batteryGuide(state.oem)), style = type.caption, color = colors.textSecondary)
                SecondaryButton(lexString(R.string.focus_lock_autostart), actions.openAutostart, Modifier.fillMaxWidth())
            }
            SystemWindow(title = lexString(R.string.pause_apps_title), accent = colors.accent, reveal = false) {
                Text(
                    if (config.targets.isEmpty()) lexString(R.string.pause_apps_none) else lexPlural(R.plurals.pause_apps_count, config.targets.size, config.targets.size),
                    style = type.label,
                    color = colors.textPrimary,
                )
                SecondaryButton(lexString(R.string.pause_apps_open), onOpenApps, Modifier.fillMaxWidth())
            }
            SystemWindow(title = null, accent = colors.accent, reveal = false) {
                Text(lexString(R.string.pause_delay_title), style = type.label, color = colors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PauseRules.DELAY_CHOICES.forEach { seconds ->
                        ChoiceChip(
                            text = lexString(R.string.pause_delay_value, seconds),
                            selected = config.delayBaseSeconds == seconds,
                            onClick = { onEvent(PauseSettingsEvent.SetDelay(seconds)) },
                        )
                    }
                }
                SwitchRow(R.string.pause_progressive_title, R.string.pause_progressive_note, config.progressive) { onEvent(PauseSettingsEvent.SetProgressive(it)) }
                Text(lexString(R.string.pause_cooldown_title), style = type.label, color = colors.textPrimary)
                Text(lexString(R.string.pause_cooldown_note), style = type.caption, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PauseRules.COOLDOWN_CHOICES.forEach { minutes ->
                        ChoiceChip(
                            text = lexString(R.string.pause_cooldown_value, minutes),
                            selected = config.cooldownMinutes == minutes,
                            onClick = { onEvent(PauseSettingsEvent.SetCooldown(minutes)) },
                        )
                    }
                }
                SwitchRow(R.string.pause_require_tap_title, R.string.pause_require_tap_note, state.settings.requireTap) { onEvent(PauseSettingsEvent.SetRequireTap(it)) }
                SwitchRow(R.string.pause_intention_setting_title, R.string.pause_intention_setting_note, config.intentionPrompt) { onEvent(PauseSettingsEvent.SetIntention(it)) }
            }
            SystemWindow(title = null, accent = colors.accent, reveal = false) {
                SwitchRow(R.string.pause_hours_title, R.string.pause_hours_note, state.settings.hoursOn) { onEvent(PauseSettingsEvent.SetHoursOn(it)) }
                if (state.settings.hoursOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lexString(R.string.pause_hours_from), style = type.label, color = colors.textPrimary)
                            HourPicker(HOURS, state.settings.startHour, { onEvent(PauseSettingsEvent.SetStartHour(it)) }, { lexString(R.string.pause_hours_label, it) })
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lexString(R.string.pause_hours_to), style = type.label, color = colors.textPrimary)
                            HourPicker(HOURS, state.settings.endHour, { onEvent(PauseSettingsEvent.SetEndHour(it)) }, { lexString(R.string.pause_hours_label, it) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        DayOfWeek.entries.forEach { day ->
                            ChoiceChip(
                                text = lexString(day.label()),
                                selected = day in state.settings.days,
                                onClick = { onEvent(PauseSettingsEvent.ToggleDay(day)) },
                                multiSelect = true,
                            )
                        }
                    }
                }
            }
            SecondaryButton(lexString(R.string.pause_stats_open), onOpenStats, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwitchRow(title: Int, note: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
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
            Text(lexString(note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        AwakenSwitch(checked = checked)
    }
}

private fun DayOfWeek.label(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.pause_day_mon
    DayOfWeek.TUESDAY -> R.string.pause_day_tue
    DayOfWeek.WEDNESDAY -> R.string.pause_day_wed
    DayOfWeek.THURSDAY -> R.string.pause_day_thu
    DayOfWeek.FRIDAY -> R.string.pause_day_fri
    DayOfWeek.SATURDAY -> R.string.pause_day_sat
    DayOfWeek.SUNDAY -> R.string.pause_day_sun
}

private val HOURS: List<Int> = (0..23).toList()

private val previewConfig = PauseConfig(
    enabled = true,
    targets = setOf("com.instagram.android", "com.google.android.youtube", "com.reddit.frontpage"),
    delayBaseSeconds = 5,
    intentionPrompt = true,
)

@PhonePreviews
@Composable
private fun PauseSettingsReadyPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseSettingsScreen(
            state = PauseSettingsUiState(PauseSettings(previewConfig, requireTap = true), usageAccess = true, appearOnTop = true, notifications = true, batteryUnrestricted = true),
            onEvent = {}, onBack = {}, onOpenApps = {}, onOpenStats = {}, actions = PauseSystemActions(),
        )
    }
}

@PhonePreviews
@Composable
private fun PauseSettingsNeedsSetupPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseSettingsScreen(
            state = PauseSettingsUiState(
                PauseSettings(previewConfig, hoursOn = true, startHour = 9, endHour = 18, days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)),
                oem = OemFamily.XIAOMI,
            ),
            onEvent = {}, onBack = {}, onOpenApps = {}, onOpenStats = {}, actions = PauseSystemActions(),
        )
    }
}
