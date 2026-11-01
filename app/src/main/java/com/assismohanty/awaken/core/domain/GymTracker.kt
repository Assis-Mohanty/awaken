package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.CatalogSearch
import com.assismohanty.awaken.core.data.catalog.Exercise
import com.assismohanty.awaken.core.data.catalog.ExerciseCatalog
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.GymDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.CustomExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.LoggedSet
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.MuscleTargets
import com.assismohanty.awaken.engine.body.RestRules
import com.assismohanty.awaken.engine.body.TrainingMath
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** One exercise in a workout with its set rows, plus last session's sets for the "previous" column. */
data class WorkoutExercise(
    val position: Int,
    val exerciseId: String,
    val name: String,
    val sets: List<WorkoutSetEntity>,
) {
    /** Rest between sets in seconds; null = no rest timer. */
    val restSec: Int? get() = sets.firstOrNull()?.restSec
}

data class ActiveWorkout(val workout: WorkoutEntity, val exercises: List<WorkoutExercise>) {
    val doneSets: Int get() = exercises.sumOf { e -> e.sets.count { it.done } }
    val volumeKg: Double get() = TrainingMath.volume(exercises.flatMap { e -> e.sets.map { it.toLogged() } })
}

data class WorkoutSummary(
    val workoutId: String,
    val name: String,
    val gameDate: LocalDate,
    val duration: Duration,
    val sets: Int,
    val volumeKg: Double,
    val exerciseNames: List<String>,
)

data class PersonalRecord(val exerciseId: String, val name: String, val weightKg: Double, val reps: Int, val estimatedMaxKg: Double, val gameDate: LocalDate)

data class RoutineSummary(val routineId: String, val name: String, val exerciseNames: List<String>, val sets: Int)

/** Everything the Gym home shows, derived from finished workouts (GD §13.2). */
data class GymOverview(
    val weekStart: LocalDate,
    val weekWorkouts: Int,
    val weekSets: Int,
    val weekVolumeKg: Double,
    /** Sets per muscle this week; drive the body-map bands. */
    val weekMuscleLoad: Map<Muscle, Double>,
    /** Volume (kg × reps) per muscle this week, for the body-map sheet (ENGINE_DECISIONS #101). */
    val weekMuscleVolume: Map<Muscle, Double> = emptyMap(),
    val history: List<WorkoutSummary>,
    val records: List<PersonalRecord>,
    val routines: List<RoutineSummary>,
)

/** A rest timer that ran out; [chime] is false when it ended long ago (the phone was away). */
data class RestEnd(val chime: Boolean)

sealed interface FinishResult {
    data class Finished(val workoutId: String) : FinishResult
    /** Nothing was ticked done, so the session was dropped rather than saved empty. */
    data object DiscardedEmpty : FinishResult
}

internal fun WorkoutSetEntity.toLogged() = LoggedSet(weightKg, reps, done)

internal fun WorkoutSetEntity.targets() =
    MuscleTargets(primaryMuscles.mapNotNull(Muscle::fromKey).toSet(), secondaryMuscles.mapNotNull(Muscle::fromKey).toSet())

private fun CustomExerciseEntity.toExercise() = Exercise(
    id = id,
    name = name,
    equipment = Equipment.fromKey(equipment),
    primary = primaryMuscles.mapNotNull(Muscle::fromKey).toSet(),
    secondary = secondaryMuscles.mapNotNull(Muscle::fromKey).toSet(),
    custom = true,
)

/**
 * The Gym tracker (GD §13.2): exercise library, the session in progress, routines and the derived overview. A tracker
 * only: nothing here touches the ledger, XP or caps (ENGINE_DECISIONS #95).
 */
@Singleton
class GymTracker @Inject constructor(
    private val gymDao: GymDao,
    private val hunterDao: HunterDao,
    private val catalog: ExerciseCatalog,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend fun today(): LocalDate = clock.gameDate(hunterDao.getProfile()?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR)

    // Library

    suspend fun exercises(): List<Exercise> = gymDao.customExercises().map { it.toExercise() } + catalog.all()

    suspend fun exercise(id: String): Exercise? = exercises().firstOrNull { it.id == id }

    /** Custom exercises first, then the catalog; a blank query lists everything that passes the filters, A→Z. */
    suspend fun searchExercises(query: String, equipment: Set<Equipment>, muscle: Muscle?, limit: Int = SEARCH_LIMIT): List<Exercise> {
        val filtered = exercises().filter { e ->
            (equipment.isEmpty() || e.equipment in equipment) && (muscle == null || muscle in e.primary || muscle in e.secondary)
        }
        if (query.isBlank()) return filtered.sortedWith(compareBy({ !it.custom }, { it.name })).take(limit)
        return CatalogSearch.search(filtered, filtered.map { CatalogSearch.normalize(it.name) }, query, limit)
    }

    suspend fun createCustomExercise(name: String, equipment: Equipment, primary: Set<Muscle>, secondary: Set<Muscle>): Exercise {
        val entity = CustomExerciseEntity(
            id = "custom:${UUID.randomUUID()}",
            name = name.trim().take(MAX_NAME),
            equipment = equipment.key,
            primaryMuscles = primary.map { it.key }.toSet(),
            secondaryMuscles = (secondary - primary).map { it.key }.toSet(),
            createdAt = clock.now(),
        )
        gymDao.upsertCustomExercise(entity)
        return entity.toExercise()
    }

    suspend fun deleteCustomExercise(id: String) = gymDao.deleteCustomExercise(id)

    // Session in progress

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActive(): Flow<ActiveWorkout?> = gymDao.observeActiveWorkout().flatMapLatest { workout ->
        if (workout == null) flowOf(null) else gymDao.observeSets(workout.id).map { sets -> ActiveWorkout(workout, group(sets)) }
    }

    suspend fun activeWorkoutId(): String? = gymDao.activeWorkout()?.id

    /** Starts a session (or returns the one already running), optionally filled from [routineId]. */
    suspend fun start(name: String, routineId: String? = null): String = transactions.run {
        gymDao.activeWorkout()?.let { return@run it.id }
        val workout = WorkoutEntity(id = UUID.randomUUID().toString(), name = name.trim().take(MAX_NAME), gameDate = today(), startedAt = clock.now())
        gymDao.upsertWorkout(workout)
        if (routineId != null) {
            val byId = exercises().associateBy { it.id }
            gymDao.routineExercises(routineId).forEach { row ->
                byId[row.exerciseId]?.let { addExerciseRows(workout.id, it, row.position, row.setCount, row.restSec) }
            }
        }
        workout.id
    }

    suspend fun addExercise(workoutId: String, exerciseId: String) = transactions.run {
        val exercise = exercise(exerciseId) ?: return@run
        val position = (gymDao.sets(workoutId).maxOfOrNull { it.position } ?: -1) + 1
        addExerciseRows(workoutId, exercise, position, setCount = null)
    }

    /** Set rows prefilled from the last session with this exercise; [setCount] null = as many as last time (at least 1). */
    private suspend fun addExerciseRows(workoutId: String, exercise: Exercise, position: Int, setCount: Int?, restSec: Int? = null) {
        val previous = gymDao.previousSets(exercise.id)
        val count = (setCount ?: previous.size).coerceIn(1, MAX_SETS_PER_EXERCISE)
        val rows = (0 until count).map { i ->
            val template = previous.getOrNull(i) ?: previous.lastOrNull()
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                primaryMuscles = exercise.primary.map { it.key }.toSet(),
                secondaryMuscles = exercise.secondary.map { it.key }.toSet(),
                position = position,
                setIndex = i,
                weightKg = template?.weightKg ?: 0.0,
                reps = template?.reps ?: 0,
                done = false,
                restSec = RestRules.clamp(restSec ?: previous.lastOrNull()?.restSec),
            )
        }
        gymDao.upsertSets(rows)
    }

    /** Last session's sets of [exerciseId], for the "previous" column. */
    suspend fun previousSets(exerciseId: String): List<WorkoutSetEntity> = gymDao.previousSets(exerciseId)

    suspend fun addSet(workoutId: String, position: Int) = transactions.run {
        val sets = gymDao.sets(workoutId).filter { it.position == position }
        val last = sets.lastOrNull() ?: return@run
        if (sets.size >= MAX_SETS_PER_EXERCISE) return@run
        gymDao.upsertSets(listOf(last.copy(id = UUID.randomUUID().toString(), setIndex = last.setIndex + 1, done = false)))
    }

    /** Sets how long to rest between this exercise's sets; null turns the timer off for it. */
    suspend fun setRest(workoutId: String, position: Int, restSec: Int?) {
        gymDao.setExerciseRest(workoutId, position, RestRules.clamp(restSec))
    }

    /** Ticking a set done starts that exercise's rest; unticking one leaves a running rest alone. */
    suspend fun updateSet(set: WorkoutSetEntity, weightKg: Double, reps: Int, done: Boolean) = transactions.run {
        val nowDone = done && reps > 0
        if (nowDone && !set.done) startRest(set.workoutId, gymDao.sets(set.workoutId).firstOrNull { it.id == set.id }?.restSec ?: set.restSec)
        gymDao.upsertSets(
            listOf(
                set.copy(
                    weightKg = weightKg.coerceIn(0.0, TrainingMath.MAX_WEIGHT_KG),
                    reps = reps.coerceIn(0, TrainingMath.MAX_REPS),
                    done = nowDone,
                ),
            ),
        )
    }

    private suspend fun startRest(workoutId: String, restSec: Int?) {
        val rest = RestRules.clamp(restSec) ?: return
        val workout = gymDao.workout(workoutId) ?: return
        gymDao.upsertWorkout(workout.copy(restEndsAt = clock.now().plusSeconds(rest.toLong()), restTotalSec = rest))
    }

    /** Cancels the running rest (the hunter skipped it, or the session ended). */
    suspend fun skipRest(workoutId: String) {
        val workout = gymDao.workout(workoutId) ?: return
        if (workout.restEndsAt != null) gymDao.upsertWorkout(workout.copy(restEndsAt = null, restTotalSec = null))
    }

    /**
     * Clears a rest whose time is up and says whether it should sound. Called by the open screen and by the end-of-rest
     * job, in a transaction, so whichever runs first handles it and the other finds nothing to do.
     */
    suspend fun finishRestIfDue(workoutId: String): RestEnd? = transactions.run {
        val workout = gymDao.workout(workoutId) ?: return@run null
        val endsAt = workout.restEndsAt ?: return@run null
        val now = clock.now()
        if (now.isBefore(endsAt)) return@run null
        gymDao.upsertWorkout(workout.copy(restEndsAt = null, restTotalSec = null))
        RestEnd(chime = RestRules.shouldChime(endsAt, now))
    }

    suspend fun removeSet(setId: String) = gymDao.deleteSet(setId)

    suspend fun removeExercise(workoutId: String, position: Int) = gymDao.deleteExerciseSets(workoutId, position)

    /** Ends the session keeping only done sets; a session with none is discarded. */
    suspend fun finish(workoutId: String): FinishResult = transactions.run {
        val workout = gymDao.workout(workoutId) ?: return@run FinishResult.DiscardedEmpty
        val sets = gymDao.sets(workoutId)
        if (sets.none { it.done }) {
            discardInTransaction(workoutId)
            return@run FinishResult.DiscardedEmpty
        }
        sets.filterNot { it.done }.forEach { gymDao.deleteSet(it.id) }
        gymDao.upsertWorkout(workout.copy(endedAt = clock.now(), restEndsAt = null, restTotalSec = null))
        FinishResult.Finished(workoutId)
    }

    suspend fun discard(workoutId: String) = transactions.run { discardInTransaction(workoutId) }

    private suspend fun discardInTransaction(workoutId: String) {
        gymDao.deleteAllSets(workoutId)
        gymDao.deleteWorkoutRow(workoutId)
    }

    suspend fun deleteWorkout(workoutId: String) = discard(workoutId)

    // Routines

    /** Saves the exercises of [workoutId] (in order, with their set counts) as a routine. */
    suspend fun saveAsRoutine(workoutId: String, name: String): String? = transactions.run {
        val exercises = group(gymDao.sets(workoutId))
        if (exercises.isEmpty()) return@run null
        val routine = RoutineEntity(id = UUID.randomUUID().toString(), name = name.trim().take(MAX_NAME), updatedAt = clock.now())
        gymDao.upsertRoutine(routine)
        gymDao.insertRoutineExercises(
            exercises.mapIndexed { i, e -> RoutineExerciseEntity(routine.id, i, e.exerciseId, e.sets.size.coerceAtLeast(1), e.restSec) },
        )
        routine.id
    }

    suspend fun deleteRoutine(routineId: String) = transactions.run {
        gymDao.clearRoutineExercises(routineId)
        gymDao.deleteRoutineRow(routineId)
    }

    // Overview

    fun observeOverview(): Flow<GymOverview> = combine(
        gymDao.observeFinishedWorkouts(),
        gymDao.observeFinishedSets(),
        gymDao.observeRoutines(),
        gymDao.observeRoutineExercises(),
    ) { workouts, sets, routines, routineRows -> overview(today(), workouts, sets, routines, routineRows) }

    private suspend fun overview(
        today: LocalDate,
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        routines: List<RoutineEntity>,
        routineRows: List<RoutineExerciseEntity>,
    ): GymOverview {
        val weekStart = TrainingMath.weekStart(today)
        val weekEnd = weekStart.plusDays(6)
        val setsByWorkout = sets.groupBy { it.workoutId }
        val dateById = workouts.associate { it.id to it.gameDate }
        val weekIds = workouts.filter { it.gameDate in weekStart..weekEnd }.map { it.id }.toSet()
        val weekSets = sets.filter { it.workoutId in weekIds }
        val names = if (routineRows.isEmpty()) emptyMap() else exercises().associate { it.id to it.name }
        return GymOverview(
            weekStart = weekStart,
            weekWorkouts = weekIds.size,
            weekSets = weekSets.count { it.done },
            weekVolumeKg = TrainingMath.volume(weekSets.map { it.toLogged() }),
            weekMuscleLoad = TrainingMath.muscleLoad(weekSets.map { it.targets() to it.toLogged() }),
            weekMuscleVolume = TrainingMath.muscleVolume(weekSets.map { it.targets() to it.toLogged() }),
            history = workouts.map { w ->
                val own = setsByWorkout[w.id].orEmpty()
                WorkoutSummary(
                    workoutId = w.id,
                    name = w.name,
                    gameDate = w.gameDate,
                    duration = Duration.between(w.startedAt, w.endedAt ?: w.startedAt),
                    sets = own.count { it.done },
                    volumeKg = TrainingMath.volume(own.map { it.toLogged() }),
                    exerciseNames = own.map { it.exerciseName }.distinct(),
                )
            },
            records = records(sets, dateById),
            routines = routines.map { r ->
                val rows = routineRows.filter { it.routineId == r.id }
                RoutineSummary(r.id, r.name, rows.map { names[it.exerciseId] ?: it.exerciseId }, rows.sumOf { it.setCount })
            },
        )
    }

    private fun records(sets: List<WorkoutSetEntity>, dateById: Map<String, LocalDate>): List<PersonalRecord> =
        sets.groupBy { it.exerciseId }.mapNotNull { (exerciseId, own) ->
            val best = own.mapNotNull { s -> TrainingMath.estimatedOneRepMax(s.toLogged())?.let { s to it } }.maxByOrNull { it.second }
                ?: return@mapNotNull null
            val (set, estimate) = best
            PersonalRecord(exerciseId, set.exerciseName, set.weightKg, set.reps, estimate, dateById[set.workoutId] ?: LocalDate.MIN)
        }.sortedByDescending { it.gameDate }

    private fun group(sets: List<WorkoutSetEntity>): List<WorkoutExercise> =
        sets.groupBy { it.position }.toSortedMap().map { (position, rows) ->
            val ordered = rows.sortedBy { it.setIndex }
            WorkoutExercise(position, ordered.first().exerciseId, ordered.first().exerciseName, ordered)
        }

    companion object {
        const val SEARCH_LIMIT = 60
        // Input sanity (not game numbers).
        const val MAX_NAME = 60
        const val MAX_SETS_PER_EXERCISE = 20
    }
}

/** Minutes and seconds since [start], for the session timer. */
fun elapsedSince(start: Instant, now: Instant): Duration = Duration.between(start, now).coerceAtLeast(Duration.ZERO)
