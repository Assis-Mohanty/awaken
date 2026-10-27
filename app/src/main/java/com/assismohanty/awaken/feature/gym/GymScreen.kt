package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.BodyMap
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.awakenTextFieldColors
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.formatNumber
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.ActiveWorkout
import com.assismohanty.awaken.core.domain.GymOverview
import com.assismohanty.awaken.core.domain.PersonalRecord
import com.assismohanty.awaken.core.domain.RoutineSummary
import com.assismohanty.awaken.core.domain.WorkoutExercise
import com.assismohanty.awaken.core.domain.WorkoutSummary
import com.assismohanty.awaken.core.domain.elapsedSince
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.TrainingMath
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToLong

@Composable
fun GymRoute(modifier: Modifier = Modifier, viewModel: GymViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GymScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Gym (DESIGN_SYSTEM §4.11): the running session when there is one, otherwise the week, routines, records and history. */
@Composable
fun GymScreen(state: GymUiState, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(
        modifier = modifier
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
            Text(
                stringResource(R.string.gym_title),
                style = AwakenTheme.type.metricL,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (state.loading) return@Column
            val active = state.active
            if (active != null) {
                ActiveSession(active, state.now, state.previous, onEvent)
            } else {
                state.notice?.let { NoticeLine(it, onEvent) }
                PrimaryButton(
                    text = stringResource(R.string.gym_start_empty),
                    onClick = { onEvent(GymEvent.StartEmpty) },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.overview?.let { GymHome(it, onEvent) }
            }
            Text(stringResource(R.string.gym_tracker_note), style = AwakenTheme.type.caption, color = colors.textMuted)
        }
    }
    state.picker?.let { ExercisePickerSheet(it, onEvent) }
    state.detail?.let { ExerciseDetailSheet(it, canAdd = true, onEvent = onEvent) }
    if (state.confirmDiscard) DiscardDialog(onEvent)
    if (state.routinePrompt) RoutineNameDialog(onEvent)
}

@Composable
private fun NoticeLine(notice: GymNotice, onEvent: (GymEvent) -> Unit) {
    val text = when (notice) {
        GymNotice.FINISHED -> R.string.gym_notice_finished
        GymNotice.DISCARDED_EMPTY -> R.string.gym_notice_discarded_empty
        GymNotice.ROUTINE_SAVED -> R.string.gym_notice_routine_saved
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(text), style = AwakenTheme.type.body, color = AwakenTheme.colors.accent, modifier = Modifier.weight(1f))
        IconButton(onClick = { onEvent(GymEvent.DismissNotice) }) {
            Icon(AwakenGlyphs.Close, contentDescription = stringResource(R.string.action_dismiss), tint = AwakenTheme.colors.textSecondary)
        }
    }
}

// Home

@Composable
private fun GymHome(overview: GymOverview, onEvent: (GymEvent) -> Unit) {
    WeekWindow(overview, Modifier.staggeredEntrance(0))
    RoutinesWindow(overview.routines, onEvent, Modifier.staggeredEntrance(1))
    RecordsWindow(overview.records, Modifier.staggeredEntrance(2))
    HistoryWindow(overview.history, onEvent, Modifier.staggeredEntrance(3))
}

@Composable
private fun WeekWindow(overview: GymOverview, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.gym_window_week), accent = colors.accent, modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat(stringResource(R.string.gym_week_workouts), formatCount(overview.weekWorkouts), Modifier.weight(1f))
            Stat(stringResource(R.string.gym_week_sets), formatCount(overview.weekSets), Modifier.weight(1f))
            Stat(stringResource(R.string.gym_week_volume), stringResource(R.string.gym_kg_value, formatCount(overview.weekVolumeKg.roundToLong())), Modifier.weight(1f))
        }
        val trained = overview.weekMuscleLoad.filterValues { it > 0.0 }.keys.sortedBy { it.ordinal }
        val names = trained.map { stringResource(it.nameRes()) }
        BodyMap(
            intensity = loadIntensity(overview.weekMuscleLoad),
            description = if (names.isEmpty()) {
                stringResource(R.string.cd_gym_week_map_empty)
            } else {
                stringResource(R.string.cd_gym_week_map, names.joinToString())
            },
        )
        Text(stringResource(R.string.gym_week_map_caption), style = AwakenTheme.type.caption, color = colors.textSecondary)
    }
}

/** Weekly sets per muscle → the heatmap alpha steps via the engine's bands (GD §13.2). */
internal fun loadIntensity(load: Map<Muscle, Double>): Map<Muscle, Float> =
    load.mapValues { (_, sets) -> TrainingMath.band(sets) }
        .filterValues { it > 0 }
        .mapValues { (_, band) -> AwakenAlpha.HEATMAP_STEPS[band.coerceAtMost(AwakenAlpha.HEATMAP_STEPS.lastIndex)] }

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = AwakenTheme.type.numeric, color = AwakenTheme.colors.textPrimary)
        Text(label, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

@Composable
private fun RoutinesWindow(routines: List<RoutineSummary>, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.gym_window_routines), accent = colors.accent, modifier = modifier, reveal = false) {
        if (routines.isEmpty()) {
            Text(stringResource(R.string.gym_routines_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        }
        routines.forEachIndexed { i, routine ->
            if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(routine.name, style = AwakenTheme.type.title, color = colors.textPrimary)
                    Text(
                        stringResource(R.string.gym_routine_meta, routine.exerciseNames.size, routine.sets),
                        style = AwakenTheme.type.caption,
                        color = colors.textSecondary,
                    )
                    Text(routine.exerciseNames.joinToString(), style = AwakenTheme.type.caption, color = colors.textMuted, maxLines = 2)
                }
                QuietButton(stringResource(R.string.gym_routine_start), onClick = { onEvent(GymEvent.StartRoutine(routine.routineId, routine.name)) })
                IconButton(onClick = { onEvent(GymEvent.DeleteRoutine(routine.routineId)) }) {
                    Icon(AwakenGlyphs.Close, contentDescription = stringResource(R.string.gym_routine_delete, routine.name), tint = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun RecordsWindow(records: List<PersonalRecord>, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.gym_window_records), accent = colors.reward, modifier = modifier, reveal = false) {
        if (records.isEmpty()) Text(stringResource(R.string.gym_records_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        records.take(MAX_RECORDS).forEach { record ->
            Column {
                Text(record.name, style = AwakenTheme.type.label, color = colors.textPrimary)
                Text(
                    stringResource(R.string.gym_record_line, formatNumber(record.weightKg), record.reps, formatNumber(record.estimatedMaxKg.roundTo1())),
                    style = AwakenTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun HistoryWindow(history: List<WorkoutSummary>, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.gym_window_history), accent = colors.accent, modifier = modifier, reveal = false) {
        if (history.isEmpty()) Text(stringResource(R.string.gym_history_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        history.take(MAX_HISTORY).forEachIndexed { i, workout ->
            if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
            val date = formatDate(workout.gameDate)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(workout.name.ifBlank { stringResource(R.string.gym_workout_default_name) }, style = AwakenTheme.type.title, color = colors.textPrimary)
                    Text(
                        stringResource(
                            R.string.gym_history_meta,
                            date,
                            workout.duration.toMinutes().toInt(),
                            workout.sets,
                            formatCount(workout.volumeKg.roundToLong()),
                        ),
                        style = AwakenTheme.type.caption,
                        color = colors.textSecondary,
                    )
                    Text(workout.exerciseNames.joinToString(), style = AwakenTheme.type.caption, color = colors.textMuted, maxLines = 2)
                }
                IconButton(onClick = { onEvent(GymEvent.DeleteWorkout(workout.workoutId)) }) {
                    Icon(AwakenGlyphs.Close, contentDescription = stringResource(R.string.gym_history_delete, date), tint = colors.textSecondary)
                }
            }
        }
    }
}

// Session in progress

@Composable
private fun ActiveSession(
    active: ActiveWorkout,
    now: Instant,
    previous: Map<String, List<WorkoutSetEntity>>,
    onEvent: (GymEvent) -> Unit,
) {
    val colors = AwakenTheme.colors
    SystemWindow(
        title = active.workout.name.ifBlank { stringResource(R.string.gym_workout_default_name) },
        accent = colors.accent,
        reveal = false,
        headerTrailing = {
            Text(
                stringResource(R.string.gym_session_timer, formatElapsed(elapsedSince(active.workout.startedAt, now))),
                style = AwakenTheme.type.numericSmall,
                color = colors.accent,
            )
        },
    ) {
        Text(
            stringResource(R.string.gym_session_summary, active.doneSets, formatCount(active.volumeKg.roundToLong())),
            style = AwakenTheme.type.label,
            color = colors.textSecondary,
        )
        if (active.exercises.isEmpty()) Text(stringResource(R.string.gym_session_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
    }
    active.exercises.forEach { exercise -> ExerciseCard(exercise, previous[exercise.exerciseId].orEmpty(), onEvent) }
    SecondaryButton(text = stringResource(R.string.gym_add_exercise), onClick = { onEvent(GymEvent.OpenPicker) }, modifier = Modifier.fillMaxWidth())
    PrimaryButton(text = stringResource(R.string.gym_finish), onClick = { onEvent(GymEvent.Finish) }, modifier = Modifier.fillMaxWidth())
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuietButton(text = stringResource(R.string.gym_save_routine), onClick = { onEvent(GymEvent.AskSaveRoutine) }, enabled = active.exercises.isNotEmpty())
        QuietButton(text = stringResource(R.string.gym_discard), onClick = { onEvent(GymEvent.AskDiscard) })
    }
}

@Composable
private fun ExerciseCard(exercise: WorkoutExercise, previous: List<WorkoutSetEntity>, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(
        title = exercise.name,
        accent = colors.accent,
        reveal = false,
        headerTrailing = {
            IconButton(onClick = { onEvent(GymEvent.RemoveExercise(exercise.position)) }) {
                Icon(AwakenGlyphs.Close, contentDescription = stringResource(R.string.gym_remove_exercise, exercise.name), tint = colors.textSecondary)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            HeaderCell(stringResource(R.string.gym_col_set), Modifier.width(Spacing.lg))
            HeaderCell(stringResource(R.string.gym_col_previous), Modifier.weight(1f))
            HeaderCell(stringResource(R.string.gym_col_kg), Modifier.width(AwakenTheme.dimens.numberField))
            HeaderCell(stringResource(R.string.gym_col_reps), Modifier.width(AwakenTheme.dimens.numberField))
            Spacer(Modifier.width(AwakenTheme.dimens.touchTarget))
        }
        exercise.sets.forEachIndexed { i, set -> SetRow(i + 1, set, previous.getOrNull(i), onEvent) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            QuietButton(text = stringResource(R.string.gym_add_set), onClick = { onEvent(GymEvent.AddSet(exercise.position)) })
            exercise.sets.lastOrNull()?.takeIf { exercise.sets.size > 1 }?.let { last ->
                QuietButton(text = stringResource(R.string.gym_remove_set), onClick = { onEvent(GymEvent.RemoveSet(last.id)) })
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textMuted, modifier = modifier)
}

@Composable
private fun SetRow(number: Int, set: WorkoutSetEntity, previous: WorkoutSetEntity?, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var weight by remember(set.id) { mutableDoubleStateOf(set.weightKg) }
    var reps by remember(set.id) { mutableIntStateOf(set.reps) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(number.toString(), style = AwakenTheme.type.label, color = if (set.done) colors.accent else colors.textPrimary, modifier = Modifier.width(Spacing.lg))
        Text(
            previous?.let { stringResource(R.string.gym_previous_value, formatNumber(it.weightKg).ifEmpty { "0" }, it.reps) }
                ?: stringResource(R.string.gym_previous_none),
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = weight,
            onValueChange = {
                weight = it
                onEvent(GymEvent.UpdateSet(set, weight, reps, set.done))
            },
            description = stringResource(R.string.cd_gym_set_weight, number),
            key = set.id,
        )
        NumberField(
            value = reps.toDouble(),
            onValueChange = {
                reps = it.toInt()
                onEvent(GymEvent.UpdateSet(set, weight, reps, set.done))
            },
            description = stringResource(R.string.cd_gym_set_reps, number),
            key = set.id,
            decimals = false,
        )
        val doneLabel = stringResource(R.string.cd_gym_set_done, number)
        Checkbox(
            checked = set.done,
            onCheckedChange = { onEvent(GymEvent.UpdateSet(set, weight, reps, it)) },
            enabled = reps > 0,
            colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.divider, checkmarkColor = colors.surface),
            modifier = Modifier
                .size(AwakenTheme.dimens.touchTarget)
                .semantics { this.contentDescription = doneLabel },
        )
    }
}

@Composable
private fun DiscardDialog(onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    AlertDialog(
        onDismissRequest = { onEvent(GymEvent.DismissDiscard) },
        containerColor = colors.surfaceOverlay,
        title = { Text(stringResource(R.string.gym_discard_title), style = AwakenTheme.type.title, color = colors.textPrimary) },
        text = { Text(stringResource(R.string.gym_discard_body), style = AwakenTheme.type.body, color = colors.textSecondary) },
        confirmButton = { QuietButton(text = stringResource(R.string.gym_discard), onClick = { onEvent(GymEvent.ConfirmDiscard) }) },
        dismissButton = { QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(GymEvent.DismissDiscard) }) },
    )
}

@Composable
private fun RoutineNameDialog(onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onEvent(GymEvent.DismissSaveRoutine) },
        containerColor = colors.surfaceOverlay,
        title = { Text(stringResource(R.string.gym_routine_name_title), style = AwakenTheme.type.title, color = colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = { Text(stringResource(R.string.gym_routine_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = awakenTextFieldColors(),
            )
        },
        confirmButton = { QuietButton(text = stringResource(R.string.action_save), onClick = { onEvent(GymEvent.SaveRoutine(name)) }, enabled = name.isNotBlank()) },
        dismissButton = { QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(GymEvent.DismissSaveRoutine) }) },
    )
}

// Formatting

internal fun formatElapsed(duration: Duration): String {
    val seconds = duration.seconds
    val h = seconds / 3_600
    val m = (seconds % 3_600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun Double.roundTo1(): Double = Math.round(this * 10.0) / 10.0

@Composable
private fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(date)

private const val MAX_RECORDS = 8
private const val MAX_HISTORY = 15
private const val MAX_NAME = 60

// Previews

internal fun previewGymState(active: Boolean = false): GymUiState {
    val start = Instant.parse("2026-10-27T07:00:00Z")
    val workout = WorkoutEntity("w1", "Push day", LocalDate.of(2026, 10, 27), start)
    fun set(i: Int, kg: Double, reps: Int, done: Boolean, pos: Int = 0, name: String = "Barbell Bench Press") = WorkoutSetEntity(
        id = "s$pos$i", workoutId = "w1", exerciseId = "e$pos", exerciseName = name, primaryMuscles = setOf("chest"),
        secondaryMuscles = setOf("triceps"), position = pos, setIndex = i, weightKg = kg, reps = reps, done = done,
    )
    return GymUiState(
        loading = false,
        now = start.plusSeconds(1_745),
        active = if (active) {
            ActiveWorkout(
                workout,
                listOf(
                    WorkoutExercise(0, "e0", "Barbell Bench Press", listOf(set(0, 60.0, 10, true), set(1, 70.0, 8, true), set(2, 70.0, 8, false))),
                    WorkoutExercise(1, "e1", "Dips - Triceps Version", listOf(set(0, 0.0, 12, false, pos = 1, name = "Dips - Triceps Version"))),
                ),
            )
        } else {
            null
        },
        previous = mapOf("e0" to listOf(set(0, 57.5, 10, true), set(1, 67.5, 8, true))),
        overview = GymOverview(
            weekStart = LocalDate.of(2026, 10, 26),
            weekWorkouts = 3,
            weekSets = 41,
            weekVolumeKg = 12_480.0,
            weekMuscleLoad = mapOf(Muscle.CHEST to 12.0, Muscle.TRICEPS to 8.0, Muscle.SHOULDERS to 6.0, Muscle.QUADRICEPS to 16.0, Muscle.LATS to 4.0),
            history = listOf(WorkoutSummary("w0", "Legs", LocalDate.of(2026, 10, 26), Duration.ofMinutes(52), 18, 6_240.0, listOf("Barbell Squat", "Leg Press"))),
            records = listOf(PersonalRecord("e0", "Barbell Bench Press", 80.0, 5, 93.3, LocalDate.of(2026, 10, 24))),
            routines = listOf(RoutineSummary("r1", "Push day", listOf("Barbell Bench Press", "Dips - Triceps Version"), 7)),
        ),
    )
}

@PhonePreviews
@Composable
private fun GymHomePreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(previewGymState(), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymSessionPreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(previewGymState(active = true), onEvent = {}) }
}
