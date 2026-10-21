package com.assismohanty.awaken.feature.paths

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathInputProblem
import com.assismohanty.awaken.engine.path.PathRules
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Create / edit sheet (DESIGN_SYSTEM §4.8): goal with example chips, level, notes, stats, days, pace and time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathEditorSheet(state: PathEditorState, onEvent: (PathEditorEvent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(PathEditorEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.panelRaised,
        shape = AwakenTheme.shapes.window,
    ) {
        PathEditorContent(state, onEvent, modifier = Modifier.navigationBarsPadding().imePadding())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PathEditorContent(state: PathEditorState, onEvent: (PathEditorEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val problems = if (state.showErrors) state.problems else emptyList()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(if (state.isEdit) R.string.editor_title_edit else R.string.editor_title_new),
            style = type.windowTitle,
            color = colors.system,
            modifier = Modifier.semantics { heading() },
        )

        PathTextField(
            value = state.goal,
            onValueChange = { onEvent(PathEditorEvent.GoalChanged(it)) },
            label = stringResource(R.string.editor_goal_label),
            error = if (PathInputProblem.GOAL_LENGTH in problems) stringResource(R.string.editor_error_goal) else null,
        )
        if (!state.isEdit) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    R.string.editor_example_cf, R.string.editor_example_run, R.string.editor_example_read, R.string.editor_example_japanese,
                ).forEach { example ->
                    val text = stringResource(example)
                    ChoiceChip(text = text, selected = state.goal == text, onClick = { onEvent(PathEditorEvent.GoalChanged(text)) })
                }
            }
        }
        PathTextField(
            value = state.currentLevel,
            onValueChange = { onEvent(PathEditorEvent.LevelChanged(it)) },
            label = stringResource(R.string.editor_level_label),
            error = if (PathInputProblem.LEVEL_LENGTH in problems) stringResource(R.string.editor_error_level) else null,
        )
        PathTextField(
            value = state.notes,
            onValueChange = { onEvent(PathEditorEvent.NotesChanged(it)) },
            label = stringResource(R.string.editor_notes_label),
            error = if (PathInputProblem.NOTES_LENGTH in problems) stringResource(R.string.editor_error_notes) else null,
            singleLine = false,
        )

        SectionLabel(stringResource(R.string.editor_primary_stat))
        state.suggestedStat?.takeIf { state.chosenPrimary == null }?.let { suggested ->
            Text(stringResource(R.string.editor_suggested_stat, suggested.label()), style = type.caption, color = colors.mist)
        }
        StatChoices(selected = state.primary, onPick = { onEvent(PathEditorEvent.PrimaryPicked(it)) }, exclude = null)
        if (state.showErrors && state.primaryMissing) {
            Text(stringResource(R.string.editor_error_primary), style = type.caption, color = colors.ember)
        }

        SectionLabel(stringResource(R.string.editor_secondary_stat))
        StatChoices(selected = state.secondary, onPick = { onEvent(PathEditorEvent.SecondaryPicked(it)) }, exclude = state.primary)
        if (PathInputProblem.SAME_STATS in problems) {
            Text(stringResource(R.string.editor_error_stats), style = type.caption, color = colors.ember)
        }

        SectionLabel(stringResource(R.string.editor_days))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            DayOfWeek.entries.forEach { day ->
                ChoiceChip(
                    text = day.getDisplayName(TextStyle.SHORT, currentLocale()),
                    selected = day in state.days,
                    onClick = { onEvent(PathEditorEvent.DayToggled(day)) },
                    multiSelect = true,
                )
            }
        }
        if (PathInputProblem.NO_DAYS in problems) Text(stringResource(R.string.editor_error_days), style = type.caption, color = colors.ember)

        SectionLabel(stringResource(R.string.editor_quests_per_day))
        FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PathRules.QUESTS_PER_DAY.forEach { count ->
                ChoiceChip(
                    text = count.toString(),
                    selected = state.questsPerDay == count,
                    onClick = { onEvent(PathEditorEvent.QuestsPerDayPicked(count)) },
                )
            }
        }

        SectionLabel(stringResource(R.string.editor_weekly_minutes, state.weeklyMinutes))
        Slider(
            value = state.weeklyMinutes.toFloat(),
            onValueChange = { onEvent(PathEditorEvent.MinutesChanged(snapMinutes(it))) },
            valueRange = PathRules.WEEKLY_MINUTES.first.toFloat()..PathRules.WEEKLY_MINUTES.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.system,
                activeTrackColor = colors.system,
                inactiveTrackColor = colors.edge,
            ),
        )
        Text(stringResource(R.string.editor_minutes_hint, state.minutesPerActiveDay), style = type.caption, color = colors.mist)
        if (PathInputProblem.WEEKLY_MINUTES in problems) Text(stringResource(R.string.editor_error_minutes), style = type.caption, color = colors.ember)

        if (state.isEdit) {
            ChoiceChip(
                text = stringResource(if (state.canReplan) R.string.editor_replan_toggle else R.string.path_action_replan_used),
                selected = state.replan,
                onClick = { onEvent(PathEditorEvent.ReplanToggled) },
                multiSelect = true,
                enabled = state.canReplan,
                icon = AwakenGlyphs.Path,
            )
        }

        state.failure?.let { failure ->
            Text(
                text = stringResource(
                    when (failure) {
                        EditorFailure.GENERIC -> R.string.editor_failed
                        EditorFailure.LIMIT -> R.string.editor_error_limit
                        EditorFailure.TODAY -> R.string.editor_error_today
                        EditorFailure.REPLAN -> R.string.editor_error_replan
                    },
                ),
                style = type.label,
                color = colors.ember,
            )
        }

        PrimaryButton(
            text = stringResource(if (state.isEdit) R.string.editor_save_edit else R.string.editor_save_new),
            onClick = { onEvent(PathEditorEvent.Save) },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        )
        QuietButton(
            text = stringResource(R.string.action_cancel),
            onClick = { onEvent(PathEditorEvent.Dismiss) },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AwakenTheme.type.label, color = AwakenTheme.colors.bone, modifier = Modifier.padding(top = Spacing.xs).semantics { heading() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatChoices(selected: Stat?, onPick: (Stat) -> Unit, exclude: Stat?) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Stat.entries.filter { it != exclude }.forEach { stat ->
            ChoiceChip(
                text = stat.label(),
                selected = selected == stat,
                onClick = { onPick(stat) },
                color = AwakenTheme.colors.stat(stat),
                icon = AwakenGlyphs.stat(stat),
            )
        }
    }
}

@Composable
private fun PathTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    singleLine: Boolean = true,
) {
    val colors = AwakenTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.system,
            unfocusedBorderColor = colors.edge,
            focusedLabelColor = colors.system,
            cursorColor = colors.system,
            errorBorderColor = colors.ember,
            errorLabelColor = colors.ember,
            errorSupportingTextColor = colors.ember,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Slider positions snap to 15-minute steps so the stored value stays tidy. */
private fun snapMinutes(raw: Float): Int {
    val step = PathEditorState.MINUTES_STEP
    return (Math.round(raw / step) * step).coerceIn(PathRules.WEEKLY_MINUTES.first, PathRules.WEEKLY_MINUTES.last)
}

@ComponentPreview
@Composable
private fun PathEditorPreview() {
    AwakenTheme {
        PathEditorContent(PathEditorState(goal = "Reach 1900 on Codeforces"), onEvent = {})
    }
}

@ComponentPreview
@Composable
private fun PathEditorErrorsPreview() {
    AwakenTheme {
        PathEditorContent(PathEditorState(goal = "Hi", days = emptySet(), showErrors = true, failure = EditorFailure.LIMIT), onEvent = {})
    }
}
