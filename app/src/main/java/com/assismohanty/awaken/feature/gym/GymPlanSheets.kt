package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.ConfirmDialog
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.awakenTextFieldColors
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.GymTracker
import com.assismohanty.awaken.core.domain.RoutineDetail
import com.assismohanty.awaken.core.domain.RoutineExerciseDetail
import com.assismohanty.awaken.engine.body.RestRules
import java.time.Duration

private const val WEEK_DAYS = 7
private val WEEKDAYS = (1..WEEK_DAYS).toList()

// Plan detail

@Composable
internal fun PlanDetailSheet(detail: PlanDetailUi, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.ClosePlan) }, title = detail.fit.plan.name) { PlanDetailContent(detail, onEvent) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanDetailContent(detail: PlanDetailUi, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val plan = detail.fit.plan
    val single = plan.dayCount == 1
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(lexPlural(R.plurals.gym_plan_days, plan.dayCount, plan.dayCount), style = AwakenTheme.type.caption, color = colors.textSecondary)
        if (detail.fit.swapped > 0) Text(lexPlural(R.plurals.gym_plan_swapped, detail.fit.swapped, detail.fit.swapped), style = AwakenTheme.type.caption, color = colors.textSecondary)
        if (detail.fit.dropped > 0) Text(lexPlural(R.plurals.gym_plan_dropped, detail.fit.dropped, detail.fit.dropped), style = AwakenTheme.type.caption, color = colors.textSecondary)
        if (single) {
            Text(lexString(R.string.gym_plan_pick_day), style = AwakenTheme.type.label, color = colors.textPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                WEEKDAYS.forEach { day ->
                    ChoiceChip(text = weekdayShort(day), selected = day in detail.weekdays, onClick = { onEvent(GymEvent.TogglePlanDay(day)) }, multiSelect = true)
                }
            }
        } else {
            Text(lexString(R.string.gym_plan_days_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        detail.fit.days.forEach { day ->
            AwakenCard(reveal = false, flat = true, modifier = Modifier.fillMaxWidth()) {
                if (!single) Text(lexString(R.string.gym_plan_day_header, day.weekday?.let { weekdayShort(it) }.orEmpty(), day.name), style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
                (day.warmup + day.work).forEach { item ->
                    ListRow(title = detail.names[item.exerciseId] ?: item.exerciseId, secondary = planTargetText(item))
                }
            }
        }
        PrimaryButton(
            text = lexString(R.string.gym_plan_add),
            onClick = { onEvent(GymEvent.AddPlan(plan.id)) },
            enabled = !single || detail.weekdays.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Routine menu, delete, days

@Composable
internal fun RoutineMenuSheet(routine: RoutineDetail, canMoveUp: Boolean, canMoveDown: Boolean, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val copyName = lexString(R.string.gym_routine_copy_name, routine.name)
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseRoutineMenu) }, title = routine.name) {
        ListRow(title = lexString(R.string.gym_routine_edit), icon = AwakenGlyphs.Pencil, onClick = { onEvent(GymEvent.OpenEditor(routine.id)) })
        ListRow(title = lexString(R.string.gym_routine_assign), icon = AwakenGlyphs.Calendar, onClick = { onEvent(GymEvent.OpenAssign(routine.id)) })
        ListRow(title = lexString(R.string.gym_routine_duplicate), icon = AwakenGlyphs.Plus, onClick = { onEvent(GymEvent.DuplicateRoutine(routine.id, copyName)) })
        if (canMoveUp) ListRow(title = lexString(R.string.gym_routine_up), icon = AwakenGlyphs.ArrowUp, onClick = { onEvent(GymEvent.MoveRoutine(routine.id, -1)) })
        if (canMoveDown) ListRow(title = lexString(R.string.gym_routine_down), icon = AwakenGlyphs.ArrowDown, onClick = { onEvent(GymEvent.MoveRoutine(routine.id, 1)) })
        ListRow(title = lexString(R.string.gym_routine_delete_action), icon = AwakenGlyphs.Trash, iconTint = colors.critical, onClick = { onEvent(GymEvent.AskDeleteRoutine(routine.id)) })
    }
}

@Composable
internal fun DeleteRoutineDialog(routine: RoutineDetail, onEvent: (GymEvent) -> Unit) {
    ConfirmDialog(
        title = lexString(R.string.gym_routine_delete_title),
        body = lexString(R.string.gym_routine_delete_body),
        confirm = lexString(R.string.gym_routine_delete_action),
        dismiss = lexString(R.string.action_cancel),
        onConfirm = { onEvent(GymEvent.DeleteRoutine(routine.id)) },
        onDismiss = { onEvent(GymEvent.DismissDeleteRoutine) },
        destructive = true,
    )
}

@Composable
internal fun AssignDaysSheet(routine: RoutineDetail, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseAssign) }, title = lexString(R.string.gym_assign_title)) { WeekdayChips(routine, onEvent) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(routine: RoutineDetail, onEvent: (GymEvent) -> Unit) {
    Text(lexString(R.string.gym_assign_note), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        WEEKDAYS.forEach { day ->
            val on = day in routine.weekdays
            ChoiceChip(text = weekdayShort(day), selected = on, onClick = { onEvent(GymEvent.SetRoutineDay(routine.id, day, !on)) }, multiSelect = true)
        }
    }
}

// Routine editor

@Composable
internal fun RoutineEditorSheet(routine: RoutineDetail, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseEditor) }, title = lexString(R.string.gym_editor_title)) { RoutineEditorContent(routine, onEvent) }
}

@Composable
internal fun RoutineEditorContent(routine: RoutineDetail, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var name by rememberSaveable(routine.id) { mutableStateOf(routine.name) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(GymTracker.MAX_NAME)
                onEvent(GymEvent.RenameRoutine(routine.id, name))
            },
            label = { Text(lexString(R.string.gym_editor_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = awakenTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(lexString(R.string.gym_editor_days), style = AwakenTheme.type.label, color = colors.textPrimary)
        WeekdayChips(routine, onEvent)
        if (routine.exercises.isEmpty()) Text(lexString(R.string.gym_editor_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        routine.exercises.forEachIndexed { index, exercise ->
            EditorExercise(
                routine.id, exercise, isFirst = index == 0, isLast = index == routine.exercises.lastIndex,
                linkedToNext = exercise.superset != null && routine.exercises.getOrNull(index + 1)?.superset == exercise.superset, onEvent = onEvent,
            )
        }
        SecondaryButton(text = lexString(R.string.gym_editor_add), onClick = { onEvent(GymEvent.EditorAddExercise(routine.id)) }, modifier = Modifier.fillMaxWidth())
        PrimaryButton(text = lexString(R.string.gym_editor_done), onClick = { onEvent(GymEvent.CloseEditor) }, modifier = Modifier.fillMaxWidth())
        QuietButton(text = lexString(R.string.gym_routine_delete_action), onClick = { onEvent(GymEvent.AskDeleteRoutine(routine.id)) })
    }
}

@Composable
private fun EditorExercise(routineId: String, e: RoutineExerciseDetail, isFirst: Boolean, isLast: Boolean, linkedToNext: Boolean, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    fun update(sets: Int = e.sets, repMin: Int? = e.repMin, repMax: Int? = e.repMax, rest: Int? = e.restSec) =
        onEvent(GymEvent.EditorUpdate(routineId, e.position, sets, repMin, repMax, rest))
    AwakenCard(reveal = false, flat = true, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(e.name, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
                Text(targetText(e), style = AwakenTheme.type.caption, color = colors.textSecondary)
            }
            if (!isFirst) EditorIcon(AwakenGlyphs.ArrowUp, lexString(R.string.gym_editor_up, e.name)) { onEvent(GymEvent.EditorMove(routineId, e.position, -1)) }
            if (!isLast) EditorIcon(AwakenGlyphs.ArrowDown, lexString(R.string.gym_editor_down, e.name)) { onEvent(GymEvent.EditorMove(routineId, e.position, 1)) }
            EditorIcon(AwakenGlyphs.Close, lexString(R.string.gym_editor_remove, e.name)) { onEvent(GymEvent.EditorRemove(routineId, e.position)) }
        }
        val sets = lexString(R.string.gym_editor_sets)
        Stepper(sets, e.sets.toString(), onLess = { update(sets = e.sets - 1) }, onMore = { update(sets = e.sets + 1) })
        if (!e.timed) {
            val min = e.repMin
            val max = e.repMax
            if (min != null && max != null) {
                val from = lexString(R.string.gym_editor_reps_from)
                val to = lexString(R.string.gym_editor_reps_to)
                Stepper(from, min.toString(), onLess = { update(repMin = (min - 1).coerceAtLeast(1)) }, onMore = { update(repMin = min + 1) })
                Stepper(to, max.toString(), onLess = { update(repMax = max - 1) }, onMore = { update(repMax = max + 1) })
            }
        }
        val rest = e.restSec
        val restLabel = lexString(R.string.gym_editor_rest)
        Stepper(
            restLabel,
            rest?.let { formatElapsed(Duration.ofSeconds(it.toLong())) } ?: lexString(R.string.gym_rest_off),
            onLess = { update(rest = RestRules.stepDown(rest)) },
            onMore = { update(rest = RestRules.stepUp(rest)) },
        )
        if (!isLast) {
            ChoiceChip(
                text = lexString(R.string.gym_editor_superset),
                selected = linkedToNext,
                onClick = { onEvent(GymEvent.EditorSuperset(routineId, e.position)) },
                multiSelect = true,
            )
        }
    }
}

@Composable
private fun EditorIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(AwakenTheme.dimens.touchTarget)) {
        Icon(icon, contentDescription = description, tint = AwakenTheme.colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
    }
}

/** A label with a value between "less" and "more" buttons (48dp targets). */
@Composable
internal fun Stepper(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AwakenTheme.type.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
        EditorIcon(AwakenGlyphs.Minus, lexString(R.string.gym_editor_less, label), onLess)
        Text(value, style = AwakenTheme.type.numericSmall, color = colors.textPrimary)
        EditorIcon(AwakenGlyphs.Plus, lexString(R.string.gym_editor_more, label), onMore)
    }
}
