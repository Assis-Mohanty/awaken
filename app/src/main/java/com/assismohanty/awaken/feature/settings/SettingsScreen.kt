package com.assismohanty.awaken.feature.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.GateBackground
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
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        uri?.let { viewModel.onEvent(SettingsEvent.ExportTo(it)) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onEvent(SettingsEvent.ImportPicked(it)) }
    }
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onExport = { exportLauncher.launch(viewModel.backupFileName()) },
        // Backups are plain JSON, but file managers label them inconsistently, so any type may be picked.
        onImport = { importLauncher.launch(arrayOf("*/*")) },
        modifier = modifier,
    )
}

/** Settings (DESIGN_SYSTEM §4.7). Edits apply immediately except name and focus, which have a Save button. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GateBackground(rank = Rank.E, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_title),
                        style = AwakenTheme.type.display,
                        color = AwakenTheme.colors.bone,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    QuietButton(text = stringResource(R.string.action_back), onClick = onBack)
                }
                if (!state.loading) {
                    ProfileWindow(state, onEvent)
                    DayWindow(state, onEvent)
                    TrainingWindow(state, onEvent)
                    ComfortWindow(state, onEvent)
                    DataWindow(state, onExport, onImport)
                }
            }
        }
    }
    if (state.showReminderPicker) ReminderDialog(state.reminder, onEvent)
    if (state.importPending) ImportConfirmDialog(onEvent)
    state.importSummary?.let { summary -> ImportDoneDialog(summary) { onEvent(SettingsEvent.DismissImportSummary) } }
}

@Composable
private fun accent() = AwakenTheme.colors.system

@Composable
private fun ProfileWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.settings_window_profile), accent = accent(), reveal = false) {
        OutlinedTextField(
            value = state.nameDraft,
            onValueChange = { onEvent(SettingsEvent.NameChanged(it)) },
            label = { Text(stringResource(R.string.settings_name_label)) },
            isError = state.showNameError,
            supportingText = if (state.showNameError) {
                { Text(stringResource(R.string.settings_name_error)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = textFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.action_save_name),
            onClick = { onEvent(SettingsEvent.SaveName) },
            enabled = state.canSaveName,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.settings_focus_title), style = type.label, color = colors.bone)
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
        Text(stringResource(R.string.settings_focus_note), style = type.caption, color = colors.mist)
        SecondaryButton(
            text = stringResource(R.string.action_save_focus),
            onClick = { onEvent(SettingsEvent.SaveFocus) },
            enabled = state.canSaveFocus,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DayWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.settings_window_day), accent = accent(), reveal = false) {
        Text(stringResource(R.string.settings_day_start_title), style = type.label, color = colors.bone)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            state.dayStartOptions.forEach { hour ->
                ChoiceChip(
                    text = stringResource(R.string.settings_hour_format, hour),
                    selected = hour == state.dayStartHour,
                    onClick = { onEvent(SettingsEvent.SetDayStart(hour)) },
                )
            }
        }
        Text(stringResource(R.string.settings_day_start_note), style = type.caption, color = colors.mist)
        Text(stringResource(R.string.settings_reminder_title), style = type.label, color = colors.bone)
        Text(
            text = state.reminder?.let { formatTime(it) } ?: stringResource(R.string.settings_reminder_off),
            style = type.numericSmall,
            color = colors.bone,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SecondaryButton(text = stringResource(R.string.action_change_reminder), onClick = { onEvent(SettingsEvent.OpenReminderPicker) })
            if (state.reminder != null) {
                QuietButton(text = stringResource(R.string.action_reminder_off), onClick = { onEvent(SettingsEvent.SetReminder(null)) })
            }
        }
        Text(stringResource(R.string.settings_reminder_note), style = type.caption, color = colors.mist)
    }
}

@Composable
private fun TrainingWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.settings_window_training), accent = accent(), reveal = false) {
        Text(stringResource(R.string.settings_equipment_title), style = type.label, color = colors.bone)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            state.equipmentOptions.forEach { id ->
                ChoiceChip(
                    text = equipmentNameRes(id)?.let { stringResource(it) } ?: id,
                    selected = id in state.equipment,
                    onClick = { onEvent(SettingsEvent.ToggleEquipment(id)) },
                    multiSelect = true,
                )
            }
        }
        Text(stringResource(R.string.settings_equipment_note), style = type.caption, color = colors.mist)
        SwitchRow(
            title = stringResource(R.string.settings_injury_title),
            note = stringResource(R.string.settings_injury_note),
            checked = state.injuryMode,
            onChange = { onEvent(SettingsEvent.SetInjuryMode(it)) },
        )
        if (state.injuryMode) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.injuryOptions.forEach { tag ->
                    ChoiceChip(
                        text = injuryTagNameRes(tag)?.let { stringResource(it) } ?: tag,
                        selected = tag in state.injuryTags,
                        onClick = { onEvent(SettingsEvent.ToggleInjuryTag(tag)) },
                        color = colors.ember,
                        multiSelect = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComfortWindow(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    SystemWindow(title = stringResource(R.string.settings_window_comfort), accent = accent(), reveal = false) {
        SwitchRow(
            title = stringResource(R.string.settings_haptics),
            note = null,
            checked = state.hapticsEnabled,
            onChange = { onEvent(SettingsEvent.SetHaptics(it)) },
        )
        SwitchRow(
            title = stringResource(R.string.settings_reduce_motion),
            note = null,
            checked = state.reduceMotion,
            onChange = { onEvent(SettingsEvent.SetReduceMotion(it)) },
        )
    }
}

@Composable
private fun DataWindow(state: SettingsUiState, onExport: () -> Unit, onImport: () -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.settings_window_data), accent = accent(), reveal = false) {
        Text(stringResource(R.string.settings_data_note), style = AwakenTheme.type.caption, color = colors.mist)
        PrimaryButton(
            text = stringResource(R.string.action_export_data),
            onClick = onExport,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.action_import_data),
            onClick = onImport,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        val version = LocalContext.current.let { context ->
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        Text(stringResource(R.string.settings_about, version), style = AwakenTheme.type.caption, color = colors.fog)
    }
}

/** The whole row is the toggle target (48dp minimum); the switch only shows state. */
@Composable
private fun SwitchRow(title: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
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
            Text(title, style = AwakenTheme.type.label, color = colors.bone)
            note?.let { Text(it, style = AwakenTheme.type.caption, color = colors.mist) }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.system, checkedThumbColor = colors.void),
        )
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
        SystemWindow(title = stringResource(R.string.settings_reminder_title).uppercase(), accent = accent()) {
            // TimeInput (not the dial) so the dialog fits small phones.
            TimeInput(state = timeState, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(SettingsEvent.DismissReminderPicker) })
                PrimaryButton(
                    text = stringResource(R.string.action_done),
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
        SystemWindow(title = stringResource(R.string.import_confirm_title), accent = colors.ember) {
            Text(stringResource(R.string.import_confirm_body), style = AwakenTheme.type.body, color = colors.bone)
            PrimaryButton(
                text = stringResource(R.string.action_import_replace),
                onClick = { onEvent(SettingsEvent.ConfirmImport) },
                color = colors.ember,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(SettingsEvent.CancelImport) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ImportDoneDialog(summary: com.assismohanty.awaken.core.data.export.ImportSummary, onDismiss: () -> Unit) {
    val colors = AwakenTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        SystemWindow(title = stringResource(R.string.import_done_title), accent = colors.verified) {
            Text(
                stringResource(R.string.import_done_body, summary.hunterName, summary.level),
                style = AwakenTheme.type.body,
                color = colors.bone,
            )
            Text(
                pluralStringResource(R.plurals.import_done_rows, summary.ledgerRows, summary.ledgerRows),
                style = AwakenTheme.type.caption,
                color = colors.mist,
            )
            if (summary.firstDate != null && summary.lastDate != null) {
                Text(stringResource(R.string.import_done_range, summary.firstDate, summary.lastDate), style = AwakenTheme.type.caption, color = colors.mist)
            }
            PrimaryButton(text = stringResource(R.string.action_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AwakenTheme.colors.system,
    unfocusedBorderColor = AwakenTheme.colors.edge,
    focusedLabelColor = AwakenTheme.colors.system,
    cursorColor = AwakenTheme.colors.system,
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
        )
    }
}
