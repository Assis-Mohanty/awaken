package com.assismohanty.awaken.feature.today

import com.assismohanty.awaken.core.designsystem.components.glyph
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.engine.model.TimeOfDay
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.domain.SkipState
import com.assismohanty.awaken.engine.HabitRules
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.ConfirmDialog
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The habit overview and editor bottom sheets; nothing shows for [HabitSheet.None]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSheets(state: HabitsUiState, onEvent: (HabitsEvent) -> Unit) {
    when (val sheet = state.sheet) {
        HabitSheet.None -> return
        is HabitSheet.Confirm -> {
            HabitConfirmDialog(sheet, onEvent)
            return
        }
        else -> Unit
    }
    ModalBottomSheet(
        onDismissRequest = { onEvent(HabitsEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The card colour, like every sheet (DESIGN_SYSTEM §3); surfaceOverlay is the unselected-chip fill.
        containerColor = AwakenTheme.colors.surfaceElevated,
        shape = AwakenTheme.shapes.sheet,
    ) {
        val content = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md)
        when (val sheet = state.sheet) {
            HabitSheet.Overview -> HabitOverview(state, onEvent, content)
            is HabitSheet.Editor -> HabitEditor(sheet.editor, onEvent, content)
            is HabitSheet.Options -> HabitOptions(sheet.habit, onEvent, content, sheet.skip)
            HabitSheet.None, is HabitSheet.Confirm -> Unit
        }
    }
}

@Composable
fun HabitOverview(state: HabitsUiState, onEvent: (HabitsEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = lexString(R.string.habit_overview_title),
            style = type.windowTitle,
            color = colors.accent,
            modifier = Modifier.semantics { heading() },
        )
        Text(lexPlural(R.plurals.habit_count, state.max, state.habits.size, state.max), style = type.caption, color = colors.textSecondary)
        state.habits.forEachIndexed { index, habit ->
            if (index > 0) HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = lexString(R.string.action_edit_habit),
                        onLongClickLabel = lexString(R.string.action_habit_options),
                        onLongClick = { onEvent(HabitsEvent.Options(habit.id, fromList = true)) },
                        onClick = { onEvent(HabitsEvent.Edit(habit.id)) },
                    )
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    AwakenGlyphs.stat(habit.stat),
                    contentDescription = habit.stat.label(),
                    tint = colors.stat(habit.stat),
                    modifier = Modifier.size(AwakenTheme.dimens.sigil),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(habit.title, style = type.body, color = colors.textPrimary)
                    Text(
                        text = lexString(
                            R.string.habit_schedule_line,
                            if (habit.frequency == HabitFrequency.TIMES_PER_WEEK) {
                                lexString(R.string.habit_times_value, habit.timesPerWeek ?: 1)
                            } else {
                                habit.days.sorted().joinToString(" ") { it.shortName(locale) }
                            },
                            habit.difficulty.label(),
                        ),
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        if (!state.canAdd) Text(lexString(R.string.habit_limit), style = type.label, color = colors.textSecondary)
        PrimaryButton(
            text = lexString(R.string.action_add_habit),
            onClick = { onEvent(HabitsEvent.Add) },
            enabled = state.canAdd,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditor(editor: HabitEditorState, onEvent: (HabitsEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val locale = LocalConfiguration.current.locales[0]
    val fieldColors = awakenFieldColors()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = lexString(if (editor.habitId == null) R.string.habit_editor_new else R.string.habit_editor_edit),
            style = type.windowTitle,
            color = colors.accent,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = editor.title,
            onValueChange = { onEvent(HabitsEvent.TitleChanged(it)) },
            label = { Text(lexString(R.string.habit_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel(R.string.habit_type_label)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            HabitType.entries.forEach { habitType ->
                ChoiceChip(
                    text = lexString(habitType.nameRes()),
                    selected = editor.type == habitType,
                    onClick = { onEvent(HabitsEvent.TypeChanged(habitType)) },
                )
            }
        }
        Text(lexString(editor.type.noteRes()), style = type.caption, color = colors.textSecondary)

        SectionLabel(R.string.habit_stat)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Stat.entries.forEach { stat ->
                ChoiceChip(
                    text = stat.label(),
                    selected = editor.stat == stat,
                    onClick = { onEvent(HabitsEvent.StatChanged(stat)) },
                    color = colors.stat(stat),
                    icon = AwakenGlyphs.stat(stat),
                )
            }
        }

        SectionLabel(R.string.habit_difficulty)
        if (editor.type == HabitType.TIMED) {
            Text(
                text = editor.timedDifficulty?.let { lexString(R.string.habit_timed_difficulty, it.label(), editor.target.toIntOrNull() ?: 0) }.orEmpty(),
                style = type.body,
                color = colors.textPrimary,
            )
        } else {
            Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                editor.difficulties.forEach { difficulty ->
                    ChoiceChip(
                        text = difficulty.label(),
                        selected = editor.difficulty == difficulty,
                        onClick = { onEvent(HabitsEvent.DifficultyChanged(difficulty)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(lexString(R.string.habit_difficulty_note), style = type.caption, color = colors.textSecondary)
        }

        SectionLabel(R.string.habit_block)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            (TimeOfDay.entries + null).forEach { block ->
                val ui = block.toTimeBlock()
                ChoiceChip(
                    text = lexString(ui.nameRes),
                    selected = editor.timeOfDay == block,
                    onClick = { onEvent(HabitsEvent.BlockChanged(block)) },
                    icon = ui.glyph,
                )
            }
        }

        SectionLabel(R.string.habit_freq_label)
        if (editor.type != HabitType.NEGATIVE) {
            Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ChoiceChip(
                    text = lexString(R.string.habit_freq_days),
                    selected = editor.frequency == HabitFrequency.DAYS_OF_WEEK,
                    onClick = { onEvent(HabitsEvent.FrequencyChanged(HabitFrequency.DAYS_OF_WEEK)) },
                    modifier = Modifier.weight(1f),
                )
                ChoiceChip(
                    text = lexString(R.string.habit_freq_times),
                    selected = editor.frequency == HabitFrequency.TIMES_PER_WEEK,
                    onClick = { onEvent(HabitsEvent.FrequencyChanged(HabitFrequency.TIMES_PER_WEEK)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (editor.frequency == HabitFrequency.TIMES_PER_WEEK) {
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                (HabitRules.WEEKLY_MIN..HabitRules.WEEKLY_MAX).forEach { times ->
                    ChoiceChip(
                        text = lexString(R.string.habit_times_value, times),
                        selected = editor.timesPerWeek == times,
                        onClick = { onEvent(HabitsEvent.TimesPerWeekChanged(times)) },
                    )
                }
            }
            Text(lexString(R.string.habit_times_note), style = type.caption, color = colors.textSecondary)
        } else {
            SectionLabel(R.string.habit_days)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayOfWeek.entries.forEach { day ->
                    ChoiceChip(
                        text = day.shortName(locale),
                        selected = day in editor.days,
                        onClick = { onEvent(HabitsEvent.DayToggled(day)) },
                        multiSelect = true,
                    )
                }
            }
        }

        when (editor.type) {
            HabitType.CHECK, HabitType.QUANTITY -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(
                    value = editor.target,
                    onValueChange = { onEvent(HabitsEvent.TargetChanged(it)) },
                    label = { Text(lexString(R.string.habit_target)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = editor.unit,
                    onValueChange = { onEvent(HabitsEvent.UnitChanged(it)) },
                    label = { Text(lexString(R.string.habit_unit)) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.weight(1f),
                )
            }
            HabitType.TIMED -> OutlinedTextField(
                value = editor.target,
                onValueChange = { onEvent(HabitsEvent.TargetChanged(it)) },
                label = { Text(lexString(R.string.habit_timed_minutes)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            HabitType.NEGATIVE -> Unit
        }

        SectionLabel(R.string.habit_reminder_label)
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceChip(
                text = lexString(R.string.habit_reminder_off),
                selected = editor.reminderMinutes == null,
                onClick = { onEvent(HabitsEvent.ReminderChanged(null)) },
            )
            ChoiceChip(
                text = editor.reminderMinutes?.let { lexString(R.string.habit_reminder_at, reminderLabel(it)) } ?: lexString(R.string.habit_reminder_at, reminderLabel(HabitEditorState.DEFAULT_REMINDER_MINUTES)),
                selected = editor.reminderMinutes != null,
                onClick = { onEvent(HabitsEvent.OpenReminderPicker) },
            )
        }
        Text(lexString(R.string.habit_reminder_note), style = type.caption, color = colors.textSecondary)
        if (editor.reminderPickerOpen) {
            HabitReminderDialog(editor.reminderMinutes ?: HabitEditorState.DEFAULT_REMINDER_MINUTES, onEvent)
        }

        editor.error?.let { Text(lexString(it), style = type.label, color = colors.critical) }

        PrimaryButton(
            text = lexString(R.string.action_save),
            onClick = { onEvent(HabitsEvent.Save) },
            enabled = !editor.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editor.habitId != null) {
            SecondaryButton(
                text = lexString(R.string.action_archive_habit),
                onClick = { onEvent(HabitsEvent.Archive) },
                enabled = !editor.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietButton(
                text = lexString(R.string.action_delete_habit),
                onClick = { onEvent(HabitsEvent.Delete) },
                enabled = !editor.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        QuietButton(text = lexString(R.string.action_back), onClick = { onEvent(HabitsEvent.OpenList) })
    }
}

/** Long-press options: Archive (restorable in Settings) or Delete; campaign habits only explain why they can't. */
@Composable
fun HabitOptions(habit: HabitItem, onEvent: (HabitsEvent) -> Unit, modifier: Modifier = Modifier, skip: SkipState = SkipState.NONE) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(habit.title, style = type.windowTitle, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        SkipOffer(skip, onEvent)
        if (habit.campaign) {
            Text(lexString(R.string.habit_campaign_locked), style = type.body, color = colors.textSecondary)
        } else {
            Text(lexString(R.string.habit_options_body), style = type.body, color = colors.textSecondary)
            SecondaryButton(
                text = lexString(R.string.action_archive_habit),
                onClick = { onEvent(HabitsEvent.RequestRemove(HabitRemoveAction.ARCHIVE)) },
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = lexString(R.string.action_delete_habit),
                onClick = { onEvent(HabitsEvent.RequestRemove(HabitRemoveAction.DELETE)) },
                color = colors.critical,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(HabitsEvent.Dismiss) })
    }
}

/** Skip today and its one-a-week allowance (GD §16.2); nothing when there is no open instance today. */
@Composable
private fun SkipOffer(skip: SkipState, onEvent: (HabitsEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    when (skip) {
        SkipState.NONE -> Unit
        SkipState.AVAILABLE -> {
            SecondaryButton(
                text = lexString(R.string.habit_skip_today),
                onClick = { onEvent(HabitsEvent.SkipToday) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(lexString(R.string.habit_skip_left), style = type.caption, color = colors.textSecondary)
        }
        SkipState.NO_SKIPS_LEFT -> Text(lexString(R.string.habit_skip_none), style = type.caption, color = colors.textMuted)
        SkipState.NOT_NEEDED -> Text(lexString(R.string.habit_no_skip_flexible), style = type.caption, color = colors.textMuted)
        SkipState.SKIPPED -> SecondaryButton(
            text = lexString(R.string.habit_undo_skip),
            onClick = { onEvent(HabitsEvent.UndoSkip) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Pick the reminder time with the same compact input the daily reminder uses (it fits small phones). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitReminderDialog(initialMinutes: Int, onEvent: (HabitsEvent) -> Unit) {
    val timeState = rememberTimePickerState(
        initialHour = initialMinutes / MINUTES_PER_HOUR,
        initialMinute = initialMinutes % MINUTES_PER_HOUR,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    Dialog(onDismissRequest = { onEvent(HabitsEvent.DismissReminderPicker) }) {
        SystemWindow(title = lexString(R.string.habit_reminder_label), accent = AwakenTheme.colors.accent) {
            TimeInput(state = timeState, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(HabitsEvent.DismissReminderPicker) })
                PrimaryButton(
                    text = lexString(R.string.action_done),
                    onClick = { onEvent(HabitsEvent.ReminderChanged(timeState.hour * MINUTES_PER_HOUR + timeState.minute)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun reminderLabel(minutes: Int): String =
    LocalTime.ofSecondOfDay(minutes * SECONDS_PER_MINUTE).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun HabitType.nameRes(): Int = when (this) {
    HabitType.CHECK -> R.string.habit_type_check
    HabitType.QUANTITY -> R.string.habit_type_count
    HabitType.TIMED -> R.string.habit_type_timed
    HabitType.NEGATIVE -> R.string.habit_type_avoid
}

private fun HabitType.noteRes(): Int = when (this) {
    HabitType.CHECK -> R.string.habit_type_check_note
    HabitType.QUANTITY -> R.string.habit_type_count_note
    HabitType.TIMED -> R.string.habit_type_timed_note
    HabitType.NEGATIVE -> R.string.habit_type_avoid_note
}

private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60L

@Composable
private fun HabitConfirmDialog(confirm: HabitSheet.Confirm, onEvent: (HabitsEvent) -> Unit) {
    val delete = confirm.action == HabitRemoveAction.DELETE
    ConfirmDialog(
        title = lexString(if (delete) R.string.habit_delete_title else R.string.habit_archive_title, confirm.habit.title),
        body = lexString(if (delete) R.string.habit_delete_body else R.string.habit_archive_body),
        confirm = lexString(if (delete) R.string.action_delete_habit else R.string.action_archive_habit),
        dismiss = lexString(R.string.action_cancel),
        onConfirm = { onEvent(HabitsEvent.ConfirmRemove) },
        onDismiss = { onEvent(HabitsEvent.Dismiss) },
        destructive = delete,
    )
}

@Composable
private fun SectionLabel(@StringRes resId: Int) {
    Text(lexString(resId), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
}

private fun DayOfWeek.shortName(locale: Locale): String = getDisplayName(TextStyle.SHORT, locale)

@Composable
private fun awakenFieldColors(): TextFieldColors {
    val colors = AwakenTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.divider,
        focusedLabelColor = colors.accent,
        cursorColor = colors.accent,
    )
}

@PhonePreviews
@Composable
private fun HabitOverviewPreview() {
    AwakenTheme {
        HabitOverview(
            HabitsUiState(
                habits = listOf(
                    HabitItem("1", "Call home", Stat.CHA, Difficulty.EASY, setOf(DayOfWeek.SUNDAY), 1, "call"),
                    HabitItem("2", "Read", Stat.INT, Difficulty.NORMAL, DayOfWeek.entries.toSet(), 20, "pages"),
                ),
            ),
            onEvent = {},
        )
    }
}

@PhonePreviews
@Composable
private fun HabitOptionsPreview() {
    AwakenTheme { HabitOptions(HabitItem("2", "Read", Stat.INT, Difficulty.NORMAL, DayOfWeek.entries.toSet(), 20, "pages"), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun HabitOptionsCampaignPreview() {
    AwakenTheme {
        HabitOptions(HabitItem("3", "Walk 6,000 steps", Stat.VIT, Difficulty.EASY, DayOfWeek.entries.toSet(), 6_000, "steps", campaign = true), onEvent = {})
    }
}

@PhonePreviews
@Composable
private fun HabitEditorPreview() {
    AwakenTheme { HabitEditor(HabitEditorState(title = "Read", stat = Stat.INT, error = R.string.habit_error_days), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun HabitEditorCountPreview() {
    AwakenTheme {
        HabitEditor(
            HabitEditorState(title = "Drink water", stat = Stat.VIT, type = HabitType.QUANTITY, target = "8", unit = "glasses", reminderMinutes = 9 * 60),
            onEvent = {},
        )
    }
}

@PhonePreviews
@Composable
private fun HabitEditorTimedPreview() {
    AwakenTheme { HabitEditor(HabitEditorState(title = "Read", stat = Stat.INT, type = HabitType.TIMED, target = "30"), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun HabitEditorAvoidPreview() {
    AwakenTheme { HabitEditor(HabitEditorState(title = "No sugar", stat = Stat.WIL, type = HabitType.NEGATIVE), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun HabitEditorFlexiblePreview() {
    AwakenTheme {
        HabitEditor(
            HabitEditorState(title = "Gym", stat = Stat.STR, frequency = HabitFrequency.TIMES_PER_WEEK, timesPerWeek = 3, error = R.string.habit_reminder_limit),
            onEvent = {},
        )
    }
}

@PhonePreviews
@Composable
private fun HabitOptionsSkipPreview() {
    AwakenTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val habit = HabitItem("2", "Read", Stat.INT, Difficulty.NORMAL, DayOfWeek.entries.toSet(), 20, "pages")
            HabitOptions(habit, onEvent = {}, skip = SkipState.AVAILABLE)
            HabitOptions(habit, onEvent = {}, skip = SkipState.SKIPPED)
        }
    }
}

@PhonePreviews
@Composable
private fun HabitOptionsNoSkipPreview() {
    AwakenTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val habit = HabitItem("2", "Read", Stat.INT, Difficulty.NORMAL, DayOfWeek.entries.toSet(), 20, "pages")
            HabitOptions(habit, onEvent = {}, skip = SkipState.NO_SKIPS_LEFT)
            HabitOptions(habit, onEvent = {}, skip = SkipState.NOT_NEEDED)
        }
    }
}
