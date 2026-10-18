package com.assismohanty.awaken.feature.today

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
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
    if (state.sheet == HabitSheet.None) return
    ModalBottomSheet(
        onDismissRequest = { onEvent(HabitsEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.panelRaised,
        shape = AwakenTheme.shapes.window,
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
            HabitSheet.None -> Unit
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
            text = stringResource(R.string.habit_overview_title),
            style = type.windowTitle,
            color = colors.system,
            modifier = Modifier.semantics { heading() },
        )
        Text(pluralStringResource(R.plurals.habit_count, state.max, state.habits.size, state.max), style = type.caption, color = colors.mist)
        state.habits.forEachIndexed { index, habit ->
            if (index > 0) HorizontalDivider(thickness = AwakenTheme.dimens.hairline, color = colors.edge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_edit_habit)) { onEvent(HabitsEvent.Edit(habit.id)) }
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
                    Text(habit.title, style = type.body, color = colors.bone)
                    Text(
                        text = stringResource(R.string.habit_schedule_line, habit.days.sorted().joinToString(" ") { it.shortName(locale) }, habit.difficulty.label()),
                        style = type.caption,
                        color = colors.mist,
                    )
                }
            }
        }
        if (!state.canAdd) Text(stringResource(R.string.habit_limit), style = type.label, color = colors.mist)
        PrimaryButton(
            text = stringResource(R.string.action_add_habit),
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
            text = stringResource(if (editor.habitId == null) R.string.habit_editor_new else R.string.habit_editor_edit),
            style = type.windowTitle,
            color = colors.system,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = editor.title,
            onValueChange = { onEvent(HabitsEvent.TitleChanged(it)) },
            label = { Text(stringResource(R.string.habit_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

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
        Text(stringResource(R.string.habit_difficulty_note), style = type.caption, color = colors.mist)

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

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedTextField(
                value = editor.target,
                onValueChange = { onEvent(HabitsEvent.TargetChanged(it)) },
                label = { Text(stringResource(R.string.habit_target)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = editor.unit,
                onValueChange = { onEvent(HabitsEvent.UnitChanged(it)) },
                label = { Text(stringResource(R.string.habit_unit)) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
        }

        editor.error?.let { Text(stringResource(it), style = type.label, color = colors.ember) }

        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = { onEvent(HabitsEvent.Save) },
            enabled = !editor.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editor.habitId != null) {
            SecondaryButton(
                text = stringResource(R.string.action_archive_habit),
                onClick = { onEvent(HabitsEvent.Archive) },
                enabled = !editor.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        QuietButton(text = stringResource(R.string.action_back), onClick = { onEvent(HabitsEvent.OpenList) })
    }
}

@Composable
private fun SectionLabel(@StringRes resId: Int) {
    Text(stringResource(resId), style = AwakenTheme.type.label, color = AwakenTheme.colors.bone, modifier = Modifier.semantics { heading() })
}

private fun DayOfWeek.shortName(locale: Locale): String = getDisplayName(TextStyle.SHORT, locale)

@Composable
private fun awakenFieldColors(): TextFieldColors {
    val colors = AwakenTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.system,
        unfocusedBorderColor = colors.edge,
        focusedLabelColor = colors.system,
        cursorColor = colors.system,
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
private fun HabitEditorPreview() {
    AwakenTheme { HabitEditor(HabitEditorState(title = "Read", stat = Stat.INT, error = R.string.habit_error_days), onEvent = {}) }
}
