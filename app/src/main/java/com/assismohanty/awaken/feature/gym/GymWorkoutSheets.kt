package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ConfirmDialog
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.ActiveWorkout

// Sheets and dialogs of the running workout (GD §18.2).

/** The number pad: a set's weight, reps or hold, typed in one place. Writes as you type. */
@Composable
internal fun NumberPadSheet(pad: NumberPadUi, set: WorkoutSetEntity?, onEvent: (GymEvent) -> Unit) {
    val title = when (pad.field) {
        PadField.WEIGHT -> R.string.gym_pad_title_weight
        PadField.REPS -> R.string.gym_pad_title_reps
        PadField.HOLD -> R.string.gym_pad_title_hold
    }
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseNumberPad) }, title = lexString(title)) {
        if (set != null) PadInput(pad.field, set, onEvent)
        PrimaryButton(text = lexString(R.string.gym_pad_done), onClick = { onEvent(GymEvent.CloseNumberPad) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PadInput(field: PadField, set: WorkoutSetEntity, onEvent: (GymEvent) -> Unit) {
    when (field) {
        PadField.WEIGHT -> NumberField(
            value = set.weightKg,
            onValueChange = { onEvent(GymEvent.SetWeight(set.id, it)) },
            description = lexString(R.string.gym_pad_title_weight),
            key = set.id + field,
            suffix = lexString(R.string.gym_plates_unit_kg),
        )
        PadField.REPS -> NumberField(
            value = set.reps.toDouble(),
            onValueChange = { onEvent(GymEvent.SetReps(set.id, it.toInt())) },
            description = lexString(R.string.gym_pad_title_reps),
            key = set.id + field,
            decimals = false,
        )
        PadField.HOLD -> NumberField(
            value = (set.durationSec ?: 0).toDouble(),
            onValueChange = { onEvent(GymEvent.SetHold(set.id, it.toInt())) },
            description = lexString(R.string.gym_pad_title_hold),
            key = set.id + field,
            decimals = false,
        )
    }
}

/** "Log last set" on the rest screen: change the weight and reps (or hold) of the set just done. */
@Composable
internal fun LastSetSheet(set: WorkoutSetEntity?, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseLastSet) }, title = lexString(R.string.gym_last_set_title)) {
        if (set != null) {
            if (set.durationSec == null) {
                Text(lexString(R.string.gym_pad_title_weight), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
                PadInput(PadField.WEIGHT, set, onEvent)
                Text(lexString(R.string.gym_pad_title_reps), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
                PadInput(PadField.REPS, set, onEvent)
            } else {
                Text(lexString(R.string.gym_pad_title_hold), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
                PadInput(PadField.HOLD, set, onEvent)
            }
        }
        PrimaryButton(text = lexString(R.string.gym_pad_done), onClick = { onEvent(GymEvent.CloseLastSet) }, modifier = Modifier.fillMaxWidth())
    }
}

/** The ⋯ menu: what the exercise at [position] offers, and the workout-wide options (GD §18.2). */
@Composable
internal fun WorkoutMenuSheet(active: ActiveWorkout, position: Int, onEvent: (GymEvent) -> Unit) {
    val exercise = active.exercises.firstOrNull { it.position == position }
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseWorkoutMenu) }, title = exercise?.name) { WorkoutMenuContent(active, position, onEvent) }
}

@Composable
internal fun WorkoutMenuContent(active: ActiveWorkout, position: Int, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val exercise = active.exercises.firstOrNull { it.position == position }
    val coached = active.workout.mode == WorkoutEntity.MODE_COACHED
    if (exercise != null) {
        ListRow(title = lexString(R.string.gym_menu_replace), icon = AwakenGlyphs.Search, onClick = { onEvent(GymEvent.OpenReplace(position)) })
        ListRow(
            title = lexString(R.string.gym_menu_plates),
            icon = AwakenGlyphs.Dumbbell,
            onClick = {
                onEvent(GymEvent.CloseWorkoutMenu)
                onEvent(GymEvent.OpenPlates(exercise.sets.lastOrNull()?.weightKg?.takeIf { it > 0.0 }))
            },
        )
        ListRow(
            title = lexString(R.string.gym_menu_history),
            icon = AwakenGlyphs.Calendar,
            onClick = {
                onEvent(GymEvent.CloseWorkoutMenu)
                onEvent(GymEvent.OpenHistory(exercise.exerciseId, exercise.name))
            },
        )
        ListRow(title = lexString(R.string.gym_menu_add_set), icon = AwakenGlyphs.Plus, onClick = { onEvent(GymEvent.AddSet(position)) })
        if (exercise.sets.map { it.setIndex }.distinct().size > 1) {
            ListRow(title = lexString(R.string.gym_menu_remove_set), icon = AwakenGlyphs.Minus, onClick = { onEvent(GymEvent.RemoveLastSet(position)) })
        }
        ListRow(title = lexString(R.string.gym_menu_skip), icon = AwakenGlyphs.ChevronRight, onClick = { onEvent(GymEvent.SkipExercise(position)) })
        ListRow(title = lexString(R.string.gym_menu_remove), icon = AwakenGlyphs.Close, onClick = { onEvent(GymEvent.RemoveExercise(position)) })
    }
    ListRow(
        title = lexString(if (coached) R.string.gym_menu_list_mode else R.string.gym_menu_coached_mode),
        icon = AwakenGlyphs.Bars,
        onClick = { onEvent(GymEvent.ToggleMode) },
    )
    ListRow(
        title = lexString(R.string.gym_menu_discard),
        icon = AwakenGlyphs.Trash,
        iconTint = colors.critical,
        onClick = {
            onEvent(GymEvent.CloseWorkoutMenu)
            onEvent(GymEvent.AskDiscard)
        },
    )
}

/** "Swap with": easier, similar and harder alternatives from the library. An empty list shows no option (GD §18.2). */
@Composable
internal fun ReplaceSheet(replace: ReplaceUi, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseReplace) }, title = lexString(R.string.gym_replace_title)) { ReplaceContent(replace, onEvent) }
}

@Composable
internal fun ReplaceContent(replace: ReplaceUi, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Text(replace.name, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
    if (replace.isEmpty) {
        Text(lexString(R.string.gym_replace_none), style = AwakenTheme.type.body, color = colors.textSecondary)
        return
    }
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.xs)) {
        listOf(
            R.string.gym_replace_easier to replace.easier,
            R.string.gym_replace_similar to replace.similar,
            R.string.gym_replace_harder to replace.harder,
        ).filter { it.second.isNotEmpty() }.forEach { (title, choices) ->
            Text(lexString(title), style = AwakenTheme.type.label, color = colors.textPrimary)
            choices.forEach { c ->
                ListRow(title = c.name, secondary = c.variant, onClick = { onEvent(GymEvent.ReplaceWith(replace.position, c.id)) })
            }
        }
        Text(lexString(R.string.gym_replace_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
    }
}

/** Finish with sets still not done: they are dropped, so ask first. */
@Composable
internal fun FinishConfirmDialog(notDone: Int, onEvent: (GymEvent) -> Unit) {
    ConfirmDialog(
        title = lexString(R.string.gym_finish_confirm_title),
        body = lexPlural(R.plurals.gym_finish_confirm_body, notDone, notDone),
        confirm = lexString(R.string.gym_finish),
        dismiss = lexString(R.string.gym_finish_confirm_keep),
        onConfirm = { onEvent(GymEvent.Finish) },
        onDismiss = { onEvent(GymEvent.DismissFinish) },
    )
}

/** Discard is only ever behind this confirm, in the danger colour. */
@Composable
internal fun DiscardDialog(onEvent: (GymEvent) -> Unit) {
    ConfirmDialog(
        title = lexString(R.string.gym_discard_title),
        body = lexString(R.string.gym_discard_body),
        confirm = lexString(R.string.gym_discard),
        dismiss = lexString(R.string.action_cancel),
        onConfirm = { onEvent(GymEvent.ConfirmDiscard) },
        onDismiss = { onEvent(GymEvent.DismissDiscard) },
        destructive = true,
    )
}
