package com.assismohanty.awaken.feature.settings

import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.engine.FocusGoalRules
import com.assismohanty.awaken.engine.TimeBlocks
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.domain.AiFeature
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.assismohanty.awaken.core.designsystem.components.AwakenSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.SegmentedControl
import com.assismohanty.awaken.debug.DebugEntry
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.core.audio.ChimeChoice
import com.assismohanty.awaken.core.data.repo.ThemeMode
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.DayChips
import androidx.compose.ui.platform.LocalLocale
import java.time.DayOfWeek
import java.time.format.TextStyle
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.equipmentNameRes
import com.assismohanty.awaken.core.designsystem.components.injuryTagNameRes
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.focuslock.FocusLockWindowRoute
import com.assismohanty.awaken.feature.reelcounter.ReelCounterWindowRoute
import com.assismohanty.awaken.feature.focuslock.FocusLockSettingsUiState
import com.assismohanty.awaken.feature.focuslock.FocusLockWindowContent
import com.assismohanty.awaken.feature.paths.AiConsentSheet
import com.assismohanty.awaken.feature.paths.WhatIsSent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenHealth: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenScreenTime: () -> Unit = {},
    onOpenFocusSetup: () -> Unit = {},
    onOpenAllowedApps: () -> Unit = {},
    onOpenReelSetup: () -> Unit = {},
    onOpenReelLimits: () -> Unit = {},
    onOpenReelStats: () -> Unit = {},
    onOpenFocusAccess: () -> Unit = {},
    onOpenReelInspector: () -> Unit = {},
    onOpenDebug: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    archivedViewModel: ArchivedHabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val archived by archivedViewModel.archived.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        uri?.let { viewModel.onEvent(SettingsEvent.ExportTo(it)) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onEvent(SettingsEvent.ImportPicked(it)) }
    }
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(SettingsEvent.Resumed)
        onPauseOrDispose { }
    }
    HealthActionEffect(
        action = state.healthAction,
        permissions = state.healthPermissions,
        onPermissionsResult = { viewModel.onEvent(SettingsEvent.HealthPermissionsResult(it)) },
        onHandled = { viewModel.onEvent(SettingsEvent.HealthActionHandled) },
    )
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onExport = { exportLauncher.launch(viewModel.backupFileName()) },
        // Backups are plain JSON, but file managers label them inconsistently, so any type may be picked.
        onImport = { importLauncher.launch(arrayOf("*/*")) },
        onOpenPrivacy = { context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)) },
        onOpenHealth = onOpenHealth,
        onOpenScreenTime = onOpenScreenTime,
        focusWindow = {
            FocusLockWindowRoute(onOpenSetup = onOpenFocusSetup, onOpenAllowedApps = onOpenAllowedApps, onOpenScreenTime = onOpenScreenTime)
            BedtimeSettingsRoute(onGrantAccess = { com.assismohanty.awaken.feature.screentime.openUsageAccessSettings(context) })
            ReelCounterWindowRoute(
                onOpenSetup = onOpenReelSetup,
                onOpenLimits = onOpenReelLimits,
                onOpenStats = onOpenReelStats,
                onOpenPermissions = onOpenFocusAccess,
                onOpenInspector = onOpenReelInspector,
            )
        },
        onOpenDebug = onOpenDebug,
        archivedHabits = archived,
        onRestoreHabit = archivedViewModel::restore,
        showAiPaths = AiFeature.enabled,
        onOpenNotificationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        modifier = modifier,
    )
}

/** Settings (DESIGN_SYSTEM §4.7). Edits apply immediately except name and focus, which have a Save button. */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPrivacy: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    onOpenScreenTime: () -> Unit = {},
    onOpenDebug: () -> Unit = {},
    focusWindow: @Composable () -> Unit = {},
    archivedHabits: List<ArchivedHabit> = emptyList(),
    onRestoreHabit: (habitId: String) -> Unit = {},
    showAiPaths: Boolean = true,
    initialSection: SettingsSection? = null,
) {
    // The menu is the first screen; each row opens its section in place, and back returns to the menu (design/IA.md section 5).
    var section by rememberSaveable { mutableStateOf(initialSection) }
    BackHandler(enabled = section != null) { section = null }
    LargeTitleScaffold(
        title = section?.let { lexString(it.titleRes) } ?: lexString(R.string.settings_title),
        onBack = { if (section != null) section = null else onBack() },
        modifier = modifier,
    ) {
        if (!state.loading) {
            when (section) {
                null -> SettingsMenu(state, archivedHabits.size, showAiPaths, onOpen = { section = it })
                SettingsSection.PROFILE -> {
                    ProfileWindow(state, onEvent)
                    GoalsWindow(state, onEvent)
                }
                SettingsSection.DAY -> DayWindow(state, onEvent, onOpenNotificationSettings)
                SettingsSection.HEALTH -> HealthTrainingWindow(state, onEvent, onOpenHealth)
                SettingsSection.APPEARANCE -> AppearanceWindow(state, onEvent)
                SettingsSection.WIDGETS -> WidgetsWindow()
                SettingsSection.PHONE_FOCUS -> PhoneFocusSection(onOpenScreenTime, focusWindow)
                SettingsSection.ARCHIVED -> ArchivedHabitsWindow(archivedHabits, onRestoreHabit)
                SettingsSection.DATA -> {
                    DataWindow(state, onExport, onImport, onOpenPrivacy)
                    if (showAiPaths) AiPathsWindow(state, onEvent)
                    // Debug builds only (DebugEntry is a no-op in release): never floats over content.
                    if (DebugEntry.ENABLED) {
                        SystemWindow(title = lexString(R.string.settings_window_developer), accent = accent(), reveal = false) {
                            DebugEntry.LauncherButton(onClick = onOpenDebug)
                        }
                    }
                }
            }
        }
    }
    if (state.showReminderPicker) ReminderDialog(state.reminder, onEvent)
    if (state.importPending) ImportConfirmDialog(onEvent)
    if (showAiPaths && state.showAiConsent) {
        AiConsentSheet(onEnable = { onEvent(SettingsEvent.EnableAiPaths) }, onNotNow = { onEvent(SettingsEvent.DeclineAiPaths) })
    }
    if (state.healthAction == HealthAction.SHOW_RATIONALE) {
        HealthRationaleDialog(
            onContinue = { onEvent(SettingsEvent.HealthRationaleAccepted) },
            onDismiss = { onEvent(SettingsEvent.DismissHealthRationale) },
        )
    }
    state.importSummary?.let { summary -> ImportDoneDialog(summary) { onEvent(SettingsEvent.DismissImportSummary) } }
}

/** The six sections of design/IA.md section 5, plus the two that have no other home yet (phone and focus, archived habits). */
internal enum class SettingsSection(val titleRes: Int, val icon: ImageVector) {
    PROFILE(R.string.settings_window_profile, AwakenGlyphs.Charisma),
    DAY(R.string.settings_window_day_reminders, AwakenGlyphs.Sun),
    HEALTH(R.string.settings_window_health_training, AwakenGlyphs.Vitality),
    APPEARANCE(R.string.settings_window_appearance, AwakenGlyphs.Sparkle),
    WIDGETS(R.string.settings_window_widgets, AwakenGlyphs.Home),
    DATA(R.string.settings_window_data, AwakenGlyphs.Vault),
    PHONE_FOCUS(R.string.settings_window_phone_focus, AwakenGlyphs.Phone),
    ARCHIVED(R.string.settings_window_habits, AwakenGlyphs.Calendar),
}

/** One card of rows, each with its current value; a row opens its section. */
@Composable
private fun SettingsMenu(state: SettingsUiState, archivedCount: Int, showAiPaths: Boolean, onOpen: (SettingsSection) -> Unit) {
    val colors = AwakenTheme.colors
    val reminder = state.reminder?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(it) }
    val dayStart = lexString(R.string.settings_hour_format, state.dayStartHour)
    val values = mapOf(
        SettingsSection.PROFILE to (listOf(state.savedName) + state.savedFocus.map { it.label() }).filter { it.isNotBlank() }.joinToString(" · "),
        SettingsSection.DAY to if (reminder != null) {
            lexString(R.string.settings_value_day, dayStart, reminder)
        } else {
            lexString(R.string.settings_value_day_none, dayStart)
        },
        SettingsSection.HEALTH to lexString(if (state.healthConnected) R.string.settings_value_health_on else R.string.settings_value_health_off),
        SettingsSection.APPEARANCE to lexString(state.themeMode.labelRes()),
        SettingsSection.WIDGETS to lexString(R.string.settings_value_widgets),
        SettingsSection.DATA to lexString(if (showAiPaths) R.string.settings_value_data_ai else R.string.settings_value_data),
        SettingsSection.PHONE_FOCUS to lexString(R.string.settings_value_phone),
        SettingsSection.ARCHIVED to lexString(R.string.settings_value_archived, archivedCount),
    )
    val rows = SettingsSection.entries.filter { it != SettingsSection.ARCHIVED || archivedCount > 0 }
    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            ListRow(
                title = lexString(row.titleRes),
                icon = row.icon,
                secondary = values[row],
                onClick = { onOpen(row) },
                trailing = {
                    Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
                },
            )
        }
    }
}

/** How to put the widgets and the Quick Settings tile on the phone; nothing here to change. */
@Composable
private fun WidgetsWindow() {
    SystemWindow(title = null, accent = accent(), reveal = false) {
        Text(lexString(R.string.settings_widgets_body), style = AwakenTheme.type.body, color = AwakenTheme.colors.textPrimary)
        Text(lexString(R.string.settings_widgets_tile), style = AwakenTheme.type.body, color = AwakenTheme.colors.textPrimary)
    }
}

@Composable
private fun accent() = AwakenTheme.colors.accent

@Composable
private fun ProfileWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = null, accent = accent(), reveal = false) {
        OutlinedTextField(
            value = state.nameDraft,
            onValueChange = { onEvent(SettingsEvent.NameChanged(it)) },
            label = { Text(lexString(R.string.settings_name_label)) },
            isError = state.showNameError,
            supportingText = if (state.showNameError) {
                { Text(lexString(R.string.settings_name_error)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = textFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = lexString(R.string.action_save_name),
            onClick = { onEvent(SettingsEvent.SaveName) },
            enabled = state.canSaveName,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(lexString(R.string.settings_focus_title), style = type.label, color = colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Stat.entries.forEach { stat ->
                ChoiceChip(
                    text = stat.label(),
                    selected = stat in state.focusDraft,
                    onClick = { onEvent(SettingsEvent.ToggleFocus(stat)) },
                    color = colors.stat(stat),
                    multiSelect = true,
                )
            }
        }
        Text(lexString(R.string.settings_focus_note), style = type.caption, color = colors.textSecondary)
        SecondaryButton(
            text = lexString(R.string.action_save_focus),
            onClick = { onEvent(SettingsEvent.SaveFocus) },
            enabled = state.canSaveFocus,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Daily targets: focus minutes and steps. Both change from today on. */
@Composable
private fun GoalsWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.settings_window_goals), accent = accent(), reveal = false) {
        Text(lexString(R.string.focus_goal_title), style = type.label, color = colors.textPrimary)
        val goalLessCd = lexString(R.string.focus_goal_less)
        val goalMoreCd = lexString(R.string.focus_goal_more)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                text = lexString(R.string.pomodoro_minus),
                onClick = { onEvent(SettingsEvent.StepFocusGoal(-1)) },
                enabled = state.focusGoal > FocusGoalRules.MIN_GOAL,
                modifier = Modifier.semantics { contentDescription = goalLessCd },
            )
            Text(lexString(R.string.focus_goal_value, state.focusGoal), style = type.metricL, color = colors.textPrimary, modifier = Modifier.weight(1f))
            SecondaryButton(
                text = lexString(R.string.pomodoro_plus),
                onClick = { onEvent(SettingsEvent.StepFocusGoal(1)) },
                enabled = state.focusGoal < FocusGoalRules.MAX_GOAL,
                modifier = Modifier.semantics { contentDescription = goalMoreCd },
            )
        }
        Text(lexString(R.string.focus_goal_settings_note), style = type.caption, color = colors.textSecondary)
        StepGoalRow(state, onEvent)
    }
}

@Composable
private fun DayWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, onOpenNotificationSettings: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = null, accent = accent(), reveal = false) {
        Text(lexString(R.string.settings_day_start_title), style = type.label, color = colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            state.dayStartOptions.forEach { hour ->
                ChoiceChip(
                    text = lexString(R.string.settings_hour_format, hour),
                    selected = hour == state.dayStartHour,
                    onClick = { onEvent(SettingsEvent.SetDayStart(hour)) },
                )
            }
        }
        Text(lexString(R.string.settings_day_start_note), style = type.caption, color = colors.textSecondary)
        Text(lexString(R.string.settings_morning_end), style = type.label, color = colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TimeBlocks.MORNING_END_HOURS.forEach { hour ->
                ChoiceChip(
                    text = lexString(R.string.settings_hour_format, hour),
                    selected = hour == state.morningEnd,
                    onClick = { onEvent(SettingsEvent.SetMorningEnd(hour)) },
                )
            }
        }
        Text(lexString(R.string.settings_day_end), style = type.label, color = colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TimeBlocks.DAY_END_HOURS.forEach { hour ->
                ChoiceChip(
                    text = lexString(R.string.settings_hour_format, hour),
                    selected = hour == state.dayEnd,
                    onClick = { onEvent(SettingsEvent.SetDayEnd(hour)) },
                )
            }
        }
        Text(lexString(R.string.settings_blocks_note), style = type.caption, color = colors.textSecondary)
        Text(lexString(R.string.settings_reminder_title), style = type.label, color = colors.textPrimary)
        Text(
            text = state.reminder?.let { formatTime(it) } ?: lexString(R.string.settings_reminder_off),
            style = type.numericSmall,
            color = colors.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SecondaryButton(text = lexString(R.string.action_change_reminder), onClick = { onEvent(SettingsEvent.OpenReminderPicker) })
            if (state.reminder != null) {
                QuietButton(text = lexString(R.string.action_reminder_off), onClick = { onEvent(SettingsEvent.SetReminder(null)) })
            }
        }
        Text(lexString(R.string.settings_reminder_note), style = type.caption, color = colors.textSecondary)
        if (state.notificationsBlocked) {
            Text(lexString(R.string.settings_notifications_blocked), style = type.caption, color = colors.critical)
            SecondaryButton(
                text = lexString(R.string.action_open_notification_settings),
                onClick = onOpenNotificationSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Habits the hunter archived (long-press on Today): each comes back with Restore, max 8 active (GD §6.1). */
@Composable
private fun ArchivedHabitsWindow(habits: List<ArchivedHabit>, onRestore: (String) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = null, accent = accent(), reveal = false) {
        if (habits.isEmpty()) {
            Text(lexString(R.string.settings_archived_habits_empty), style = type.caption, color = colors.textSecondary)
        }
        habits.forEach { habit ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(habit.title, style = type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                val label = lexString(R.string.settings_restore_habit_label, habit.title)
                QuietButton(
                    text = lexString(R.string.action_restore_habit),
                    onClick = { onRestore(habit.id) },
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
    }
}

@Composable
private fun HealthTrainingWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, onOpenHealth: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = null, accent = accent(), reveal = false) {
        HealthRow(state, onEvent, onOpenHealth)
        Text(lexString(R.string.settings_equipment_title), style = type.label, color = colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            state.equipmentOptions.forEach { id ->
                ChoiceChip(
                    text = equipmentNameRes(id)?.let { lexString(it) } ?: id,
                    selected = id in state.equipment,
                    onClick = { onEvent(SettingsEvent.ToggleEquipment(id)) },
                    multiSelect = true,
                )
            }
        }
        Text(lexString(R.string.settings_equipment_note), style = type.caption, color = colors.textSecondary)
        SwitchRow(
            title = lexString(R.string.settings_injury_title),
            note = lexString(R.string.settings_injury_note),
            checked = state.injuryMode,
            onChange = { onEvent(SettingsEvent.SetInjuryMode(it)) },
        )
        if (state.injuryMode) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.injuryOptions.forEach { tag ->
                    ChoiceChip(
                        text = injuryTagNameRes(tag)?.let { lexString(it) } ?: tag,
                        selected = tag in state.injuryTags,
                        onClick = { onEvent(SettingsEvent.ToggleInjuryTag(tag)) },
                        color = colors.critical,
                        multiSelect = true,
                    )
                }
            }
        }
        TrainingDaysRow(state, onEvent)
    }
}


@Composable
private fun HealthRow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, onOpenHealth: () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Text(lexString(R.string.settings_health_title), style = type.label, color = colors.textPrimary)
    val status = when {
        state.healthConnected -> R.string.settings_health_on
        state.healthStatus == null -> R.string.settings_health_checking
        state.healthStatus != HealthStatus.AVAILABLE -> R.string.health_not_installed
        else -> R.string.settings_health_off
    }
    Text(lexString(status), style = type.caption, color = if (state.healthConnected) colors.positive else colors.textSecondary)
    if (state.healthConnected) {
        PrimaryButton(
            text = lexString(R.string.settings_health_view),
            onClick = onOpenHealth,
            color = colors.positive,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = lexString(R.string.action_disconnect),
            onClick = { onEvent(SettingsEvent.DisconnectHealth) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        PrimaryButton(
            text = lexString(R.string.action_connect),
            onClick = { onEvent(SettingsEvent.ConnectHealth) },
            color = colors.positive,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
/** Settings → AI Paths (DESIGN_SYSTEM §4.7): on/off, "what we send", and what turning it off does. */
@Composable
private fun AiPathsWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    SystemWindow(title = lexString(R.string.settings_window_ai), accent = accent(), reveal = false) {
        SwitchRow(
            title = lexString(R.string.settings_ai_paths),
            note = lexString(if (state.aiPathsEnabled) R.string.settings_ai_on_note else R.string.settings_ai_off_note),
            checked = state.aiPathsEnabled,
            onChange = { onEvent(SettingsEvent.SetAiPaths(it)) },
        )
        WhatIsSent()
    }
}

@Composable
private fun AppearanceWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    SystemWindow(title = null, accent = accent(), reveal = false) {
        Text(lexString(R.string.settings_appearance_title), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
        SegmentedControl(
            options = ThemeMode.entries.map { lexString(it.labelRes()) },
            selected = ThemeMode.entries.indexOf(state.themeMode),
            onSelect = { onEvent(SettingsEvent.SetThemeMode(ThemeMode.entries[it])) },
        )
        Text(lexString(R.string.settings_chime_title), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ChimeChoice.entries.forEach { choice ->
                ChoiceChip(
                    text = lexString(choice.labelRes()),
                    selected = choice == state.chime,
                    onClick = { onEvent(SettingsEvent.SetChime(choice)) },
                )
            }
        }
        SwitchRow(
            title = lexString(R.string.settings_haptics),
            note = null,
            checked = state.hapticsEnabled,
            onChange = { onEvent(SettingsEvent.SetHaptics(it)) },
        )
        SwitchRow(
            title = lexString(R.string.settings_reduce_motion),
            note = null,
            checked = state.reduceMotion,
            onChange = { onEvent(SettingsEvent.SetReduceMotion(it)) },
        )
    }
}

@Composable
private fun DataRow(icon: ImageVector, title: String, note: String?, onClick: () -> Unit, enabled: Boolean = true) {
    ListRow(
        title = title,
        icon = icon,
        secondary = note,
        onClick = if (enabled) onClick else null,
        trailing = {
            Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = AwakenTheme.colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
        },
    )
}

@Composable
private fun DataWindow(state: SettingsUiState, onExport: () -> Unit, onImport: () -> Unit, onOpenPrivacy: () -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = null, accent = accent(), reveal = false) {
        Text(lexString(R.string.settings_data_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        val version = LocalContext.current.let { context ->
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        var licences by rememberSaveable { mutableStateOf(false) }
        DataRow(AwakenGlyphs.Vault, lexString(R.string.action_export_data), lexString(R.string.settings_export_note), onExport, enabled = !state.busy)
        DataRow(AwakenGlyphs.Vault, lexString(R.string.action_import_data), lexString(R.string.settings_import_note), onImport, enabled = !state.busy)
        DataRow(AwakenGlyphs.Lock, lexString(R.string.action_privacy_policy), null, onOpenPrivacy)
        DataRow(AwakenGlyphs.Info, lexString(R.string.settings_licences), null, { licences = true })
        if (licences) LicencesSheet(onDismiss = { licences = false })
        Text(lexString(R.string.settings_about, version), style = AwakenTheme.type.caption, color = colors.textMuted)
    }
}

private fun ChimeChoice.labelRes(): Int = when (this) {
    ChimeChoice.OFF -> R.string.settings_chime_off
    ChimeChoice.SOFT_BELL -> R.string.settings_chime_bell
    ChimeChoice.WOOD_BLOCK -> R.string.settings_chime_wood
    ChimeChoice.DIGITAL -> R.string.settings_chime_digital
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.LIGHT -> R.string.settings_theme_light
}

/** The whole row is the toggle target (48dp minimum); the switch only shows state. */
@Composable
internal fun SwitchRow(title: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
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
            Text(title, style = AwakenTheme.type.label, color = colors.textPrimary)
            note?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary) }
        }
        AwakenSwitch(checked = checked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(current: LocalTime?, onEvent: (SettingsEvent) -> Unit) {
    val initial = current ?: DEFAULT_REMINDER
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    Dialog(onDismissRequest = { onEvent(SettingsEvent.DismissReminderPicker) }) {
        SystemWindow(title = lexString(R.string.settings_reminder_title), accent = accent()) {
            // TimeInput (not the dial) so the dialog fits small phones.
            TimeInput(state = timeState, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(SettingsEvent.DismissReminderPicker) })
                PrimaryButton(
                    text = lexString(R.string.action_done),
                    onClick = { onEvent(SettingsEvent.SetReminder(LocalTime.of(timeState.hour, timeState.minute))) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ImportConfirmDialog(onEvent: (SettingsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Dialog(onDismissRequest = { onEvent(SettingsEvent.CancelImport) }) {
        SystemWindow(title = lexString(R.string.import_confirm_title), accent = colors.critical) {
            Text(lexString(R.string.import_confirm_body), style = AwakenTheme.type.body, color = colors.textPrimary)
            PrimaryButton(
                text = lexString(R.string.action_import_replace),
                onClick = { onEvent(SettingsEvent.ConfirmImport) },
                color = colors.critical,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(SettingsEvent.CancelImport) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ImportDoneDialog(summary: com.assismohanty.awaken.core.data.export.ImportSummary, onDismiss: () -> Unit) {
    val colors = AwakenTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        SystemWindow(title = lexString(R.string.import_done_title), accent = colors.positive) {
            Text(
                lexString(R.string.import_done_body, summary.hunterName, summary.level),
                style = AwakenTheme.type.body,
                color = colors.textPrimary,
            )
            Text(
                lexPlural(R.plurals.import_done_rows, summary.ledgerRows, summary.ledgerRows),
                style = AwakenTheme.type.caption,
                color = colors.textSecondary,
            )
            if (summary.firstDate != null && summary.lastDate != null) {
                Text(lexString(R.string.import_done_range, summary.firstDate, summary.lastDate), style = AwakenTheme.type.caption, color = colors.textSecondary)
            }
            PrimaryButton(text = lexString(R.string.action_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AwakenTheme.colors.accent,
    unfocusedBorderColor = AwakenTheme.colors.divider,
    focusedLabelColor = AwakenTheme.colors.accent,
    cursorColor = AwakenTheme.colors.accent,
)

@Composable
private fun formatTime(time: LocalTime): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0]).format(time)

private val DEFAULT_REMINDER: LocalTime = LocalTime.of(8, 0)
private const val BACKUP_MIME = "application/json"

internal fun previewSettingsState() = SettingsUiState(
    loading = false,
    savedName = "Assis",
    nameDraft = "Assis",
    savedFocus = listOf(Stat.STR, Stat.INT),
    focusDraft = listOf(Stat.STR, Stat.INT),
    dayStartHour = 4,
    reminder = LocalTime.of(7, 30),
    equipment = setOf("dumbbells", "jump_rope"),
    equipmentOptions = listOf("dumbbells", "kettlebell", "pullup_bar", "resistance_band", "jump_rope", "gym_access", "bicycle"),
    injuryMode = true,
    injuryTags = setOf("impact", "legs"),
)

@PhonePreviews
@Composable
private fun SettingsScreenPreview() {
    AwakenTheme(reduceMotion = true) {
        SettingsScreen(state = previewSettingsState(), onEvent = {}, onBack = {}, onExport = {}, onImport = {})
    }
}

@PhonePreviews
@Composable
private fun SettingsScreenEditingPreview() {
    AwakenTheme(reduceMotion = true) {
        SettingsScreen(
            state = previewSettingsState().copy(nameDraft = "As", showNameError = true, injuryMode = false, reminder = null),
            onEvent = {},
            onBack = {},
            onExport = {},
            onImport = {},
            initialSection = SettingsSection.PROFILE,
        )
    }
}

@PhonePreviews
@Composable
private fun SettingsDayPreview() {
    AwakenTheme(reduceMotion = true) {
        SettingsScreen(previewSettingsState(), onEvent = {}, onBack = {}, onExport = {}, onImport = {}, initialSection = SettingsSection.DAY)
    }
}

@PhonePreviews
@Composable
private fun SettingsHealthPreview() {
    AwakenTheme(reduceMotion = true) {
        SettingsScreen(previewSettingsState(), onEvent = {}, onBack = {}, onExport = {}, onImport = {}, initialSection = SettingsSection.HEALTH)
    }
}

@PhonePreviews
@Composable
private fun SettingsAppearancePreview() {
    AwakenTheme(reduceMotion = true) {
        SettingsScreen(previewSettingsState(), onEvent = {}, onBack = {}, onExport = {}, onImport = {}, initialSection = SettingsSection.APPEARANCE)
    }
}

@PhonePreviews
@Composable
private fun SettingsWidgetsPreview() {
    AwakenTheme(reduceMotion = true) {
        SettingsScreen(previewSettingsState(), onEvent = {}, onBack = {}, onExport = {}, onImport = {}, initialSection = SettingsSection.WIDGETS)
    }
}

@PhonePreviews
@Composable
private fun SettingsDataPreview() {
    AwakenTheme(reduceMotion = true) {
        SettingsScreen(previewSettingsState(), onEvent = {}, onBack = {}, onExport = {}, onImport = {}, initialSection = SettingsSection.DATA)
    }
}

@PhonePreviews
@Composable
private fun ArchivedHabitsPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ArchivedHabitsWindow(
                habits = listOf(ArchivedHabit("1", "Read 10 pages", Stat.INT), ArchivedHabit("2", "Cold shower", Stat.WIL)),
                onRestore = {},
            )
            ArchivedHabitsWindow(habits = emptyList(), onRestore = {})
        }
    }
}

@PhonePreviews
@Composable
private fun SettingsScreenWithFocusPreview() {
    AwakenTheme(reduceMotion = true) {
        SettingsScreen(
            state = previewSettingsState(),
            onEvent = {},
            onBack = {},
            onExport = {},
            onImport = {},
            initialSection = SettingsSection.PHONE_FOCUS,
            focusWindow = {
                FocusLockWindowContent(
                    state = FocusLockSettingsUiState(enabled = true, accessibilityEnabled = true, serviceRunning = true, allowedCount = 2),
                    onToggle = {}, onFixAccessibility = {}, onFixBattery = {}, onFixUsage = {}, onOpenAllowedApps = {}, onOpenScreenTime = {},
                )
            },
        )
    }
}

/** The daily step goal for the Today ring (GD §16.11): − and + in 500-step moves. */
@Composable
private fun StepGoalRow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val lessCd = lexString(R.string.step_goal_less)
    val moreCd = lexString(R.string.step_goal_more)
    Text(lexString(R.string.step_goal_title), style = type.label, color = colors.textPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SecondaryButton(
            text = lexString(R.string.pomodoro_minus),
            onClick = { onEvent(SettingsEvent.StepStepGoal(-1)) },
            enabled = state.stepGoal > com.assismohanty.awaken.engine.StepGoalRules.MIN_GOAL,
            modifier = Modifier.semantics { contentDescription = lessCd },
        )
        Text(lexString(R.string.step_goal_value, formatCount(state.stepGoal)), style = type.metricL, color = colors.textPrimary, modifier = Modifier.weight(1f))
        SecondaryButton(
            text = lexString(R.string.pomodoro_plus),
            onClick = { onEvent(SettingsEvent.StepStepGoal(1)) },
            enabled = state.stepGoal < com.assismohanty.awaken.engine.StepGoalRules.MAX_GOAL,
            modifier = Modifier.semantics { contentDescription = moreCd },
        )
    }
    Text(lexString(R.string.step_goal_note), style = type.caption, color = colors.textSecondary)
}

/**
 * Phone and focus: the screen-time link, then Focus Lock, phone-down nights and the Reel counter (each in the [windows]
 * slot, wired by the route), under one heading.
 */
@Composable
private fun PhoneFocusSection(onOpenScreenTime: () -> Unit, windows: @Composable () -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = null, accent = accent(), reveal = false) {
        Text(lexString(R.string.settings_screen_time_title), style = type.label, color = colors.textPrimary)
        Text(lexString(R.string.settings_screen_time_note), style = type.caption, color = colors.textSecondary)
        SecondaryButton(text = lexString(R.string.action_open_screen_time), onClick = onOpenScreenTime, modifier = Modifier.fillMaxWidth())
    }
    windows()
}

/** The days the person trains, and whether the morning reminder says so (docs/onboarding/PLAN.md §4, step 15). */
@Composable
private fun TrainingDaysRow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val locale = LocalLocale.current.platformLocale
    Text(lexString(R.string.settings_training_title), style = type.label, color = colors.textPrimary)
    DayChips(
        selectedDays = state.trainingDays,
        // No day is marked as today here: the screen has no clock, and the days are a weekly pattern.
        today = NO_DAY,
        onToggle = { onEvent(SettingsEvent.ToggleTrainingDay(it)) },
        dayLetter = { DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, locale) },
        dayDescription = { day, _ -> DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale) },
        todayLabel = lexString(R.string.onb_days_today),
    )
    if (state.trainingDays.isNotEmpty()) {
        SwitchRow(
            title = lexString(R.string.onb_days_remind),
            note = lexString(if (state.reminder == null) R.string.settings_training_needs_reminder else R.string.settings_training_note),
            checked = state.trainingReminder,
            onChange = { onEvent(SettingsEvent.SetTrainingReminder(it)) },
        )
    }
}

private const val NO_DAY = 0
