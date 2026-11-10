package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.engine.body.PlateUnit
import com.assismohanty.awaken.engine.body.PlateSetup
import com.assismohanty.awaken.engine.body.PlatePlan
import com.assismohanty.awaken.engine.body.PlateMath
import com.assismohanty.awaken.core.designsystem.components.PlateDiagram
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.core.designsystem.components.SessionScreen
import com.assismohanty.awaken.core.designsystem.components.SessionDial
import com.assismohanty.awaken.core.designsystem.components.FieldQuietButton
import com.assismohanty.awaken.core.designsystem.components.FieldPrimaryButton
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.BigNumber
import com.assismohanty.awaken.core.designsystem.components.BodyMap
import com.assismohanty.awaken.core.designsystem.components.HeaderIconButton
import com.assismohanty.awaken.core.designsystem.components.PrChip
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.ProgressRing
import com.assismohanty.awaken.core.designsystem.components.ProgressionHint
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SegmentBar
import com.assismohanty.awaken.core.designsystem.components.SegmentState
import com.assismohanty.awaken.core.designsystem.components.formatNumber
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.ActiveWorkout
import com.assismohanty.awaken.core.domain.WorkoutExercise
import com.assismohanty.awaken.core.domain.elapsedSince
import com.assismohanty.awaken.core.domain.toFlow
import com.assismohanty.awaken.engine.body.CoachedFlow
import com.assismohanty.awaken.engine.body.FlowCursor
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.RestRules
import java.time.Duration
import java.time.Instant

// The running workout (GD §18.2): coached (one exercise at a time) or a list of every set, with the rest screen over both.

@Composable
internal fun ActiveWorkoutContent(state: GymUiState, active: ActiveWorkout, onEvent: (GymEvent) -> Unit) {
    val ends = active.workout.restEndsAt
    if (state.staleWorkout) StaleWorkoutCard(active, onEvent)
    if (ends != null) RestBanner(state, ends, onEvent)
    if (active.workout.mode == WorkoutEntity.MODE_COACHED) CoachedWorkout(state, active, onEvent) else ListWorkout(state, active, onEvent)
}

// Shared pieces

internal fun ActiveWorkout.flowRows() = exercises.flatMap { e -> e.sets.map { it.toFlow() } }

private fun WorkoutExercise.setAt(cursor: FlowCursor): WorkoutSetEntity? = sets.firstOrNull { it.setIndex == cursor.setIndex && it.side == cursor.side }

private fun WorkoutSetEntity.timed() = durationSec != null

/** Seconds still to wait before this set may be completed (0 = ready), from the minimum rest since the previous set of the exercise. */
internal fun minRestLeft(set: WorkoutSetEntity, exercise: WorkoutExercise, now: Instant): Int {
    val previousAt = exercise.sets.filter { it.done && it.setIndex != set.setIndex }.mapNotNull { it.completedAt }.maxOrNull()
    return RestRules.minRestLeft(previousAt, now, set.restSec)
}

private fun WorkoutSetEntity.logged() = durationSec?.let { it > 0 } ?: (reps > 0)

/** The coached workout's page heading: the exercise now showing, and "29:05 · exercise 1 of 4" under it. Null when there is no current set. */
@Composable
internal fun coachedHeading(state: GymUiState, active: ActiveWorkout): Pair<String, String>? {
    if (active.workout.mode != WorkoutEntity.MODE_COACHED) return null
    val cursor = CoachedFlow.current(active.flowRows()) ?: return null
    val exercise = active.exercises.firstOrNull { it.position == cursor.position } ?: return null
    val index = active.exercises.indexOf(exercise) + 1
    val elapsed = formatElapsed(elapsedSince(active.workout.startedAt, state.now))
    return (state.labels[exercise.exerciseId]?.name ?: exercise.name) to lexString(R.string.gym_session_sub, elapsed, index, active.exercises.size)
}

/** Finish and the exercise menu, in the header beside the heading from [coachedHeading]. */
@Composable
internal fun CoachedActions(active: ActiveWorkout, onEvent: (GymEvent) -> Unit) {
    val position = CoachedFlow.current(active.flowRows())?.position ?: 0
    QuietButton(text = lexString(R.string.gym_finish), onClick = { onEvent(GymEvent.AskFinish) })
    HeaderIconButton(AwakenGlyphs.More, lexString(R.string.gym_menu_cd), { onEvent(GymEvent.OpenWorkoutMenu(position)) })
}

@Composable
private fun WorkoutHeader(active: ActiveWorkout, now: Instant, canFinish: Boolean, menuPosition: Int, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                lexString(R.string.gym_session_timer, formatElapsed(elapsedSince(active.workout.startedAt, now))),
                style = AwakenTheme.type.numericSmall,
                color = colors.accent,
            )
            Text(
                lexString(R.string.gym_session_summary, lexPlural(R.plurals.gym_sets_done, active.doneSets, active.doneSets), formatKg(active.volumeKg)),
                style = AwakenTheme.type.caption,
                color = colors.textSecondary,
            )
        }
        if (canFinish) QuietButton(text = lexString(R.string.gym_finish), onClick = { onEvent(GymEvent.AskFinish) })
        HeaderIconButton(AwakenGlyphs.More, lexString(R.string.gym_menu_cd), { onEvent(GymEvent.OpenWorkoutMenu(menuPosition)) })
    }
}

@Composable
private fun SegmentsFor(active: ActiveWorkout, cursor: FlowCursor?) {
    val states = active.exercises.map { e ->
        when {
            e.sets.all { it.done } -> SegmentState.DONE
            e.position == cursor?.position -> SegmentState.CURRENT
            else -> SegmentState.TODO
        }
    }
    val current = active.exercises.indexOfFirst { it.position == cursor?.position } + 1
    SegmentBar(states, lexString(R.string.gym_progress_cd, current.coerceAtLeast(1), active.exercises.size))
}

@Composable
private fun RestBanner(state: GymUiState, ends: Instant, onEvent: (GymEvent) -> Unit) {
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lexString(R.string.gym_rest_banner, formatElapsed(RestRules.remaining(ends, state.now))),
                style = AwakenTheme.type.numericSmall,
                color = AwakenTheme.colors.accent,
                modifier = Modifier.weight(1f),
            )
            QuietButton(text = lexString(R.string.gym_rest_show), onClick = { onEvent(GymEvent.ShowRest) })
        }
    }
}

@Composable
private fun StaleWorkoutCard(active: ActiveWorkout, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
        Text(lexString(R.string.gym_stale_title), style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
        Text(lexString(R.string.gym_stale_body, formatDate(active.workout.gameDate)), style = AwakenTheme.type.body, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(text = lexString(R.string.gym_finish), onClick = { onEvent(GymEvent.AskFinish) }, modifier = Modifier.weight(1f))
            QuietButton(text = lexString(R.string.gym_discard), onClick = { onEvent(GymEvent.AskDiscard) })
        }
    }
}

/** "Last time: 60 kg × 10, 10, 9". */
@Composable
internal fun lastTimeText(previous: List<WorkoutSetEntity>): String {
    if (previous.isEmpty()) return lexString(R.string.gym_last_time_none)
    val body = when {
        previous.first().timed() -> previous.map { lexString(R.string.gym_done_hold, it.durationSec ?: 0) }.joinToString(", ")
        previous.map { it.weightKg }.distinct().size == 1 -> {
            val reps = previous.joinToString(", ") { it.reps.toString() }
            if (previous.first().weightKg > 0.0) lexString(R.string.gym_last_weighted, formatNumber(previous.first().weightKg), reps) else lexString(R.string.gym_last_bodyweight, reps)
        }
        else -> previous.map { lexString(R.string.gym_done_weighted, formatNumber(it.weightKg).ifEmpty { "0" }, it.reps) }.joinToString(", ")
    }
    return lexString(R.string.gym_last_time, body)
}

/** "60 kg × 10", "12 reps" or "45 s" for a set. */
@Composable
internal fun setValueText(set: WorkoutSetEntity): String = when {
    set.timed() -> lexString(R.string.gym_done_hold, set.durationSec ?: 0)
    set.weightKg > 0.0 -> lexString(R.string.gym_done_weighted, formatNumber(set.weightKg), set.reps)
    else -> lexString(R.string.gym_done_bodyweight, set.reps)
}

@Composable
private fun sideText(side: String?): String? = when (side) {
    "LEFT" -> lexString(R.string.gym_side_left)
    "RIGHT" -> lexString(R.string.gym_side_right)
    else -> null
}

// Coached

@Composable
private fun CoachedWorkout(state: GymUiState, active: ActiveWorkout, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val rows = active.flowRows()
    val cursor = CoachedFlow.current(rows)
    val exercise = active.exercises.firstOrNull { it.position == cursor?.position }
    val set = if (cursor != null) exercise?.setAt(cursor) else null
    if (cursor == null) WorkoutHeader(active, state.now, canFinish = false, menuPosition = 0, onEvent = onEvent)
    SegmentsFor(active, cursor)
    if (cursor == null || exercise == null || set == null) {
        if (active.exercises.isEmpty()) {
            Text(lexString(R.string.gym_session_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        } else {
            Text(lexString(R.string.gym_all_done), style = AwakenTheme.type.body, color = colors.textSecondary)
            PrimaryButton(text = lexString(R.string.gym_finish_workout), onClick = { onEvent(GymEvent.Finish) }, modifier = Modifier.fillMaxWidth())
        }
        QuietButton(text = lexString(R.string.gym_add_exercise), onClick = { onEvent(GymEvent.OpenPicker) }, modifier = Modifier.fillMaxWidth())
        return
    }
    val label = state.labels[exercise.exerciseId]
    val (number, total) = CoachedFlow.setNumber(rows, exercise.position, cursor.setIndex)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        label?.variant?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary) }
        Text(setLine(active, exercise, number, total, cursor), style = AwakenTheme.type.label, color = colors.accent)
        targetLine(set)?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary) }
    }
    if (label?.barbell == true && !set.timed()) {
        BarbellPlates(set.weightKg, state.prefs.setup(PlateUnit.KG))
    } else {
        val muscles = set.primaryMuscles.mapNotNull(Muscle::fromKey).associateWith { 1f } +
            set.secondaryMuscles.mapNotNull(Muscle::fromKey).filter { it.key !in set.primaryMuscles }.associateWith { HELPER_INTENSITY }
        BodyMap(
            intensity = muscles,
            description = lexString(R.string.cd_gym_exercise_map, set.primaryMuscles.mapNotNull(Muscle::fromKey).map { lexString(it.nameRes()) }.joinToString()),
            figureHeight = AwakenTheme.dimens.bodyMapHeight * MAP_SHARE,
        )
    }
    TargetNumbers(set, label?.stepKg ?: DEFAULT_STEP_KG, onEvent)
    Text(lastTimeText(state.previous[exercise.exerciseId].orEmpty()), style = AwakenTheme.type.body, color = colors.textSecondary)
    state.hints[exercise.exerciseId]?.let { ProgressionHint(tryText(it)) }
    DoneLines(exercise, state.prSetIds, onEvent)
    CompleteButton(set, exercise, state.now, onEvent)
    QuietButton(text = lexString(R.string.gym_add_exercise), onClick = { onEvent(GymEvent.OpenPicker) }, modifier = Modifier.fillMaxWidth())
}

/** The bar for the weight in the set: the plates on each end and "Bar 20 kg + 25 kg a side" under it. */
@Composable
private fun BarbellPlates(weightKg: Double, setup: PlateSetup) {
    val perSide = when (val plan = PlateMath.plan(weightKg, setup)) {
        is PlatePlan.Exact -> plan.perSide
        is PlatePlan.Short -> plan.perSide
        is PlatePlan.BelowBar -> emptyList()
    }
    val labels = perSide.map { formatNumber(it) }
    val line = if (labels.isEmpty()) {
        lexString(R.string.gym_plates_empty_bar)
    } else {
        lexString(R.string.gym_bar_line, formatNumber(setup.barWeight), labels.joinToString(" + "))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
        PlateDiagram(platesPerSide = perSide, labels = labels, description = line)
        Text(line, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

@Composable
private fun setLine(active: ActiveWorkout, exercise: WorkoutExercise, number: Int, total: Int, cursor: FlowCursor): String {
    val letter = exercise.sets.firstOrNull()?.superset
    val group = if (letter == null) emptyList() else active.exercises.filter { e -> e.sets.firstOrNull()?.superset == letter }
    val base = if (group.size > 1) {
        lexString(R.string.gym_superset_of, letter ?: "", group.indexOfFirst { it.position == exercise.position } + 1, group.size) + " · " + lexString(R.string.gym_set_of, number, total)
    } else {
        lexString(R.string.gym_set_of, number, total)
    }
    return listOfNotNull(base, sideText(cursor.side)).joinToString(" · ")
}

@Composable
private fun targetLine(set: WorkoutSetEntity): String? {
    val min = set.repMin ?: return null
    val max = set.repMax ?: min
    return lexString(if (set.timed()) R.string.gym_target_hold else R.string.gym_target_range, min, max)
}

/** Weight and reps (or seconds held) as big numbers: tap to type, − and + to step. */
@Composable
private fun TargetNumbers(set: WorkoutSetEntity, stepKg: Double, onEvent: (GymEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        val kg = formatNumber(set.weightKg).ifEmpty { "0" }
        BigNumber(
            value = kg,
            unit = lexString(R.string.gym_plates_unit_kg),
            description = lexString(R.string.gym_number_weight_cd, kg),
            onClick = { onEvent(GymEvent.OpenNumberPad(set.id, PadField.WEIGHT)) },
            onLess = { onEvent(GymEvent.SetWeight(set.id, (set.weightKg - stepKg).coerceAtLeast(0.0))) },
            onMore = { onEvent(GymEvent.SetWeight(set.id, set.weightKg + stepKg)) },
            lessDescription = lexString(R.string.gym_less_weight),
            moreDescription = lexString(R.string.gym_more_weight),
            modifier = Modifier.weight(1f),
        )
        val hold = set.durationSec
        if (hold != null) {
            BigNumber(
                value = hold.toString(),
                unit = lexString(R.string.gym_unit_seconds),
                description = lexString(R.string.gym_number_hold_cd, hold),
                onClick = { onEvent(GymEvent.OpenNumberPad(set.id, PadField.HOLD)) },
                onLess = { onEvent(GymEvent.SetHold(set.id, (hold - HOLD_STEP_SEC).coerceAtLeast(0))) },
                onMore = { onEvent(GymEvent.SetHold(set.id, hold + HOLD_STEP_SEC)) },
                lessDescription = lexString(R.string.gym_less_hold),
                moreDescription = lexString(R.string.gym_more_hold),
                modifier = Modifier.weight(1f),
            )
        } else {
            BigNumber(
                value = set.reps.toString(),
                unit = lexString(R.string.gym_unit_reps),
                description = lexString(R.string.gym_number_reps_cd, set.reps),
                onClick = { onEvent(GymEvent.OpenNumberPad(set.id, PadField.REPS)) },
                onLess = { onEvent(GymEvent.SetReps(set.id, (set.reps - 1).coerceAtLeast(0))) },
                onMore = { onEvent(GymEvent.SetReps(set.id, set.reps + 1)) },
                lessDescription = lexString(R.string.gym_less_reps),
                moreDescription = lexString(R.string.gym_more_reps),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Done sets as quiet lines; tapping one opens it again for a change. */
@Composable
private fun DoneLines(exercise: WorkoutExercise, prSetIds: Set<String>, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    exercise.sets.filter { it.done }.forEach { done ->
        val number = exercise.sets.map { it.setIndex }.distinct().sorted().indexOf(done.setIndex) + 1
        val text = listOfNotNull(setValueText(done), sideText(done.side)).joinToString(" · ")
        val description = lexString(R.string.gym_done_set_cd, number, text)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
                .clickable { onEvent(GymEvent.ReopenSet(done.id)) }
                .semantics { contentDescription = description },
        ) {
            Icon(AwakenGlyphs.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = Spacing.xxs))
            Text(lexString(R.string.gym_done_set_line, number, text), style = AwakenTheme.type.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
            if (done.id in prSetIds) PrChip()
        }
    }
}

@Composable
private fun CompleteButton(set: WorkoutSetEntity, exercise: WorkoutExercise, now: Instant, onEvent: (GymEvent) -> Unit) {
    val left = minRestLeft(set, exercise, now)
    PrimaryButton(
        text = lexString(R.string.gym_complete_set),
        onClick = { onEvent(GymEvent.CompleteSet(set.id)) },
        enabled = set.logged() && left == 0,
        modifier = Modifier.fillMaxWidth(),
    )
    if (left > 0) {
        Text(lexString(R.string.gym_complete_wait, RestRules.minRest(set.restSec), left), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

// Rest

/** The full-screen rest: the round dial in the Strength colour, the nudge buttons, what comes next, and Skip once the minimum rest has passed. */
@Composable
internal fun RestScreen(state: GymUiState, active: ActiveWorkout, ends: Instant, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val total = active.workout.restTotalSec ?: 0
    val left = RestRules.remaining(ends, state.now)
    val elapsed = (total - left.seconds).coerceAtLeast(0)
    val minRest = RestRules.minRest(total)
    val rows = active.flowRows()
    val next = CoachedFlow.current(rows)
    val nextExercise = active.exercises.firstOrNull { it.position == next?.position }
    val nextSet = if (next != null) nextExercise?.setAt(next) else null
    val remaining = formatElapsed(left)
    SessionScreen(
        stat = Stat.STR,
        title = lexString(R.string.gym_rest_screen_title),
        subtitle = null,
        body = { diameter ->
            SessionDial(
                diameter = diameter,
                fraction = if (total > 0) left.seconds.toFloat() / total else 0f,
                numeral = remaining,
                caption = lexString(R.string.gym_rest_of, formatElapsed(Duration.ofSeconds(total.toLong()))),
                description = lexString(R.string.gym_rest_ring_cd, remaining, formatElapsed(Duration.ofSeconds(total.toLong()))),
            )
            val lessDescription = lexString(R.string.gym_rest_less_cd)
            val moreDescription = lexString(R.string.gym_rest_more_cd)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FieldQuietButton(
                    text = lexString(R.string.gym_rest_less),
                    onClick = { onEvent(GymEvent.AdjustRest(-REST_STEP_SEC)) },
                    modifier = Modifier.semantics { contentDescription = lessDescription },
                )
                FieldQuietButton(
                    text = lexString(R.string.gym_rest_more),
                    onClick = { onEvent(GymEvent.AdjustRest(REST_STEP_SEC)) },
                    modifier = Modifier.semantics { contentDescription = moreDescription },
                )
            }
            if (nextExercise != null && nextSet != null && next != null) {
                val (number, count) = CoachedFlow.setNumber(rows, nextExercise.position, next.setIndex)
                Text(lexString(R.string.gym_next_up), style = AwakenTheme.type.caption, color = colors.onFieldMuted)
                Text(lexString(R.string.gym_set_of, number, count), style = AwakenTheme.type.title, color = colors.onField)
                Text(
                    (state.labels[nextExercise.exerciseId]?.name ?: nextExercise.name) + ", " + setValueText(nextSet),
                    style = AwakenTheme.type.caption,
                    color = colors.onFieldMuted,
                    textAlign = TextAlign.Center,
                )
            }
        },
        actions = {
            if (elapsed >= minRest) {
                FieldPrimaryButton(text = lexString(R.string.gym_rest_skip), onClick = { onEvent(GymEvent.SkipRest) }, modifier = Modifier.fillMaxWidth())
            } else {
                // Skipping opens once the minimum rest has passed, so a set cannot be ticked seconds after the last.
                Text(lexString(R.string.gym_rest_skip_in, minRest - elapsed), style = AwakenTheme.type.body, color = colors.onFieldMuted)
            }
            if (active.exercises.any { e -> e.sets.any { it.done } }) {
                FieldQuietButton(text = lexString(R.string.gym_rest_log_last), onClick = { onEvent(GymEvent.OpenLastSet) })
            }
            FieldQuietButton(text = lexString(R.string.gym_rest_minimise), onClick = { onEvent(GymEvent.MinimiseRest) })
        },
    )
}

// List

@Composable
private fun ListWorkout(state: GymUiState, active: ActiveWorkout, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val finished = active.exercises.isNotEmpty() && active.exercises.all { e -> e.sets.all { it.done } }
    WorkoutHeader(active, state.now, canFinish = !finished, menuPosition = active.exercises.firstOrNull()?.position ?: 0, onEvent = onEvent)
    if (active.exercises.isEmpty()) Text(lexString(R.string.gym_session_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
    active.exercises.forEach { exercise ->
        val label = state.labels[exercise.exerciseId]
        AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label?.name ?: exercise.name, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
                    label?.variant?.let { Text(it, style = AwakenTheme.type.caption, color = colors.textSecondary) }
                }
                HeaderIconButton(AwakenGlyphs.More, lexString(R.string.gym_menu_cd), { onEvent(GymEvent.OpenWorkoutMenu(exercise.position)) })
            }
            Text(lastTimeText(state.previous[exercise.exerciseId].orEmpty()), style = AwakenTheme.type.caption, color = colors.textSecondary)
            state.hints[exercise.exerciseId]?.let { ProgressionHint(tryText(it)) }
            val next = exercise.sets.firstOrNull { !it.done }
            exercise.sets.forEach { set -> ListSetRow(set, exercise, state, enabled = set === next, onEvent = onEvent) }
            next?.let { n ->
                val left = minRestLeft(n, exercise, state.now)
                if (left > 0) Text(lexString(R.string.gym_complete_wait, RestRules.minRest(n.restSec), left), style = AwakenTheme.type.caption, color = colors.textSecondary)
            }
        }
    }
    QuietButton(text = lexString(R.string.gym_add_exercise), onClick = { onEvent(GymEvent.OpenPicker) }, modifier = Modifier.fillMaxWidth())
    if (finished) PrimaryButton(text = lexString(R.string.gym_finish_workout), onClick = { onEvent(GymEvent.Finish) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ListSetRow(set: WorkoutSetEntity, exercise: WorkoutExercise, state: GymUiState, enabled: Boolean, onEvent: (GymEvent) -> Unit) {
    val number = exercise.sets.map { it.setIndex }.distinct().sorted().indexOf(set.setIndex) + 1
    val side = sideText(set.side)
    val title = if (side == null) lexString(R.string.gym_list_set, number) else lexString(R.string.gym_list_set_side, number, side)
    val kg = formatNumber(set.weightKg).ifEmpty { "0" }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        val weightCd = lexString(R.string.gym_number_weight_cd, kg)
        val repsCd = lexString(R.string.gym_number_reps_cd, set.reps)
        val holdCd = lexString(R.string.gym_number_hold_cd, set.durationSec ?: 0)
        if (set.durationSec == null) {
            SecondaryButton(
                text = lexString(R.string.gym_kg_value, kg),
                onClick = { onEvent(GymEvent.OpenNumberPad(set.id, PadField.WEIGHT)) },
                modifier = Modifier.semantics { contentDescription = weightCd },
            )
            SecondaryButton(
                text = lexString(R.string.gym_done_bodyweight, set.reps),
                onClick = { onEvent(GymEvent.OpenNumberPad(set.id, PadField.REPS)) },
                modifier = Modifier.semantics { contentDescription = repsCd },
            )
        } else {
            SecondaryButton(
                text = lexString(R.string.gym_done_hold, set.durationSec ?: 0),
                onClick = { onEvent(GymEvent.OpenNumberPad(set.id, PadField.HOLD)) },
                modifier = Modifier.semantics { contentDescription = holdCd },
            )
        }
        if (set.done) {
            val reopen = lexString(R.string.gym_list_reopen_cd, number)
            SecondaryButton(text = lexString(R.string.gym_list_done), onClick = { onEvent(GymEvent.ReopenSet(set.id)) }, modifier = Modifier.semantics { contentDescription = reopen })
            if (set.id in state.prSetIds) PrChip()
        } else {
            val complete = lexString(R.string.gym_list_complete_cd, number)
            PrimaryButton(
                text = lexString(R.string.gym_list_done),
                onClick = { onEvent(GymEvent.CompleteSet(set.id)) },
                enabled = enabled && set.logged() && minRestLeft(set, exercise, state.now) == 0,
                modifier = Modifier.semantics { contentDescription = complete },
            )
        }
    }
}

private const val HELPER_INTENSITY = 0.5f
private const val MAP_SHARE = 0.5f
private const val DEFAULT_STEP_KG = 2.5
private const val HOLD_STEP_SEC = 5
private const val REST_STEP_SEC = 15
