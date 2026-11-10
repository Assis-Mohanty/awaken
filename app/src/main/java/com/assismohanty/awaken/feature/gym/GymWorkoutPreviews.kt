package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.ActiveWorkout
import com.assismohanty.awaken.core.domain.WorkoutExercise
import com.assismohanty.awaken.engine.body.ProgressionHint
import com.assismohanty.awaken.engine.body.WorkingSets
import java.time.Instant
import java.time.LocalDate

// Previews of the running workout in each state (GD §18.2). Sets are built by hand so each state shows what it names.

private val start = Instant.parse("2026-10-27T07:00:00Z")
private val clockNow = start.plusSeconds(1_745)

private fun workout(mode: String = WorkoutEntity.MODE_COACHED, restEndsAt: Instant? = null, total: Int? = null) =
    WorkoutEntity("w1", "Push day", LocalDate.of(2026, 10, 27), start, restEndsAt = restEndsAt, restTotalSec = total, mode = mode)

private fun set(
    position: Int,
    index: Int,
    exercise: String,
    id: String,
    kg: Double,
    reps: Int,
    done: Boolean = false,
    doneAgoSec: Long = 0,
    side: String? = null,
    superset: String? = null,
    hold: Int? = null,
    primary: Set<String> = setOf("chest"),
    secondary: Set<String> = setOf("triceps"),
    rest: Int? = 120,
) = WorkoutSetEntity(
    id = "$position-$index${side.orEmpty()}", workoutId = "w1", exerciseId = id, exerciseName = exercise, primaryMuscles = primary,
    secondaryMuscles = secondary, position = position, setIndex = index, weightKg = kg, reps = reps, done = done, restSec = rest,
    completedAt = if (done) clockNow.minusSeconds(doneAgoSec) else null, durationSec = hold, side = side, superset = superset,
    repMin = if (hold != null) 30 else 8, repMax = if (hold != null) 60 else 10,
)

private val labels = mapOf(
    "bench" to ExerciseLabel("Bench press", "Barbell · medium grip", 2.5, barbell = true),
    "incline" to ExerciseLabel("Incline dumbbell press", null, 2.5),
    "pushdown" to ExerciseLabel("Triceps pushdown", null, 5.0),
    "skull" to ExerciseLabel("Skullcrusher", "EZ bar", 2.5),
    "row" to ExerciseLabel("One-arm dumbbell row", null, 2.5),
    "plank" to ExerciseLabel("Plank", null, 2.5),
)

private fun exercise(position: Int, name: String, vararg sets: WorkoutSetEntity) = WorkoutExercise(position, sets.first().exerciseId, name, sets.toList())

private val benchPrevious = listOf(
    set(0, 0, "Barbell Bench Press", "bench", 57.5, 10, done = true),
    set(0, 1, "Barbell Bench Press", "bench", 57.5, 10, done = true),
    set(0, 2, "Barbell Bench Press", "bench", 57.5, 9, done = true),
)

private fun state(active: ActiveWorkout, extra: GymUiState.() -> GymUiState = { this }) = previewGymState(active = false).copy(
    active = active,
    now = clockNow,
    labels = labels,
    previous = mapOf("bench" to benchPrevious),
).extra()

private val firstTimeBench = ActiveWorkout(
    workout(),
    listOf(
        exercise(0, "Barbell Bench Press", set(0, 0, "Barbell Bench Press", "bench", 0.0, 8), set(0, 1, "Barbell Bench Press", "bench", 0.0, 8), set(0, 2, "Barbell Bench Press", "bench", 0.0, 8)),
        exercise(1, "Incline Dumbbell Press", set(1, 0, "Incline Dumbbell Press", "incline", 0.0, 8)),
    ),
)

private val benchMidway = ActiveWorkout(
    workout(),
    listOf(
        exercise(
            0, "Barbell Bench Press",
            set(0, 0, "Barbell Bench Press", "bench", 60.0, 10, done = true, doneAgoSec = 200),
            set(0, 1, "Barbell Bench Press", "bench", 60.0, 11),
            set(0, 2, "Barbell Bench Press", "bench", 60.0, 11),
        ),
        exercise(1, "Incline Dumbbell Press", set(1, 0, "Incline Dumbbell Press", "incline", 22.5, 10)),
    ),
)

@PhonePreviews
@Composable
private fun GymCoachedFirstTimePreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(state(firstTimeBench).copy(previous = emptyMap()), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymCoachedHistoryPreview() {
    AwakenTheme(reduceMotion = true) {
        GymScreen(
            state(benchMidway) {
                copy(hints = mapOf("bench" to HintUi(WorkingSets(57.5, listOf(10, 10, 9)), ProgressionHint.Suggest(60.0, listOf(11, 11, 11)))), prSetIds = setOf("0-0"))
            },
            onEvent = {},
        )
    }
}

@PhonePreviews
@Composable
private fun GymCoachedWaitPreview() {
    // The first set was done 10 s ago with a 120 s rest: the next one unlocks after the 30 s minimum.
    val active = ActiveWorkout(
        workout(),
        listOf(
            exercise(
                0, "Barbell Bench Press",
                set(0, 0, "Barbell Bench Press", "bench", 60.0, 10, done = true, doneAgoSec = 10),
                set(0, 1, "Barbell Bench Press", "bench", 60.0, 10),
            ),
        ),
    )
    AwakenTheme(reduceMotion = true) { GymScreen(state(active), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymCoachedHoldPreview() {
    val active = ActiveWorkout(workout(), listOf(exercise(0, "Plank", set(0, 0, "Plank", "plank", 0.0, 0, hold = 45, primary = setOf("abdominals"), secondary = emptySet(), rest = 45), set(0, 1, "Plank", "plank", 0.0, 0, hold = 45, primary = setOf("abdominals"), secondary = emptySet(), rest = 45))))
    AwakenTheme(reduceMotion = true) { GymScreen(state(active) { copy(previous = emptyMap()) }, onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymCoachedSupersetPreview() {
    val active = ActiveWorkout(
        workout(),
        listOf(
            exercise(
                0, "Triceps Pushdown",
                set(0, 0, "Triceps Pushdown", "pushdown", 25.0, 12, done = true, doneAgoSec = 40, superset = "A", primary = setOf("triceps"), secondary = emptySet(), rest = 75),
                set(0, 1, "Triceps Pushdown", "pushdown", 25.0, 12, superset = "A", primary = setOf("triceps"), secondary = emptySet(), rest = 75),
            ),
            exercise(
                1, "Skullcrusher",
                set(1, 0, "Skullcrusher", "skull", 20.0, 10, superset = "A", primary = setOf("triceps"), secondary = emptySet(), rest = 75),
                set(1, 1, "Skullcrusher", "skull", 20.0, 10, superset = "A", primary = setOf("triceps"), secondary = emptySet(), rest = 75),
            ),
        ),
    )
    AwakenTheme(reduceMotion = true) { GymScreen(state(active) { copy(previous = emptyMap()) }, onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymCoachedSidesPreview() {
    val active = ActiveWorkout(
        workout(),
        listOf(
            exercise(
                0, "One-arm dumbbell row",
                set(0, 0, "One-Arm Dumbbell Row", "row", 28.0, 10, done = true, doneAgoSec = 20, side = "LEFT", primary = setOf("middle back"), secondary = setOf("biceps"), rest = 60),
                set(0, 0, "One-Arm Dumbbell Row", "row", 28.0, 10, side = "RIGHT", primary = setOf("middle back"), secondary = setOf("biceps"), rest = 60),
                set(0, 1, "One-Arm Dumbbell Row", "row", 28.0, 10, side = "LEFT", primary = setOf("middle back"), secondary = setOf("biceps"), rest = 60),
                set(0, 1, "One-Arm Dumbbell Row", "row", 28.0, 10, side = "RIGHT", primary = setOf("middle back"), secondary = setOf("biceps"), rest = 60),
            ),
        ),
    )
    AwakenTheme(reduceMotion = true) { GymScreen(state(active) { copy(previous = emptyMap()) }, onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymWorkoutAllDonePreview() {
    val active = ActiveWorkout(workout(), listOf(exercise(0, "Barbell Bench Press", set(0, 0, "Barbell Bench Press", "bench", 60.0, 10, done = true, doneAgoSec = 5))))
    AwakenTheme(reduceMotion = true) { GymScreen(state(active), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymRestScreenPreview() {
    val ends = clockNow.plusSeconds(74)
    val active = ActiveWorkout(
        workout(restEndsAt = ends, total = 120),
        listOf(
            exercise(
                0, "Barbell Bench Press",
                set(0, 0, "Barbell Bench Press", "bench", 60.0, 10, done = true, doneAgoSec = 46),
                set(0, 1, "Barbell Bench Press", "bench", 60.0, 10),
            ),
        ),
    )
    AwakenTheme(reduceMotion = true) { GymScreen(state(active), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymRestSkipReadyPreview() {
    val ends = clockNow.plusSeconds(40)
    val active = ActiveWorkout(
        workout(restEndsAt = ends, total = 120),
        listOf(exercise(0, "Barbell Bench Press", set(0, 0, "Barbell Bench Press", "bench", 60.0, 10, done = true, doneAgoSec = 80), set(0, 1, "Barbell Bench Press", "bench", 60.0, 10))),
    )
    AwakenTheme(reduceMotion = true) { GymScreen(state(active), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymListWorkoutPreview() {
    val active = ActiveWorkout(
        workout(mode = WorkoutEntity.MODE_LIST),
        listOf(
            exercise(
                0, "Barbell Bench Press",
                set(0, 0, "Barbell Bench Press", "bench", 60.0, 10, done = true, doneAgoSec = 200),
                set(0, 1, "Barbell Bench Press", "bench", 60.0, 10),
                set(0, 2, "Barbell Bench Press", "bench", 60.0, 10),
            ),
            exercise(1, "Plank", set(1, 0, "Plank", "plank", 0.0, 0, hold = 45, primary = setOf("abdominals"), secondary = emptySet(), rest = 45)),
        ),
    )
    AwakenTheme(reduceMotion = true) { GymScreen(state(active), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymStaleWorkoutPreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(state(benchMidway) { copy(staleWorkout = true) }, onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymWorkoutMenuPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            WorkoutMenuContent(benchMidway, 0, {})
        }
    }
}

@PhonePreviews
@Composable
private fun GymReplacePreview() {
    val choices = listOf(ExerciseChoice("dumbbell", "Dumbbell bench press", null), ExerciseChoice("machine", "Machine chest press", "Plate loaded"))
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ReplaceContent(ReplaceUi(0, "Bench press", easier = choices, similar = choices.take(1), harder = listOf(ExerciseChoice("incline", "Incline bench press", "Barbell"))), {})
        }
    }
}

@PhonePreviews
@Composable
private fun GymReplaceNoneForPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ReplaceContent(ReplaceUi(0, "Bench press", emptyList(), emptyList(), emptyList()), {})
        }
    }
}
