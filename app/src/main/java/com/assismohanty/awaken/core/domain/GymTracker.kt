package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.CatalogSearch
import com.assismohanty.awaken.core.data.catalog.Exercise
import com.assismohanty.awaken.core.data.catalog.ExerciseCatalog
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.GymDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.CustomExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.ExercisePrefEntity
import com.assismohanty.awaken.core.data.db.entity.GymSettingsEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.TrainingDays
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.body.CoachedFlow
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.FlowRow
import com.assismohanty.awaken.engine.body.LoggedSet
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.MuscleTargets
import com.assismohanty.awaken.engine.body.RestRules
import com.assismohanty.awaken.engine.body.TrainingMath
import com.assismohanty.awaken.engine.body.ExerciseSession
import com.assismohanty.awaken.engine.body.PlateMath
import com.assismohanty.awaken.engine.body.PlateSetup
import com.assismohanty.awaken.engine.body.PlateUnit
import com.assismohanty.awaken.engine.body.ProgressionHint
import com.assismohanty.awaken.engine.body.ProgressionRules
import com.assismohanty.awaken.engine.body.RecordEvent
import com.assismohanty.awaken.engine.body.RecordRules
import com.assismohanty.awaken.engine.body.RepRange
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
    /** Today is one of the training days chosen in onboarding or Settings. */
    val trainingDay: Boolean = false,
)

/** The rest-end settings: [sound] plays the chime, [vibrate] buzzes. */
data class RestAlerts(val sound: Boolean, val vibrate: Boolean)

/** A rest timer that ran out; [chime] is false when it ended long ago (the phone was away). */
data class RestEnd(val chime: Boolean)

/** The records one exercise broke in a finished session (GD §16.7); [events] is never empty. */
data class ExerciseRecords(val exerciseId: String, val name: String, val events: List<RecordEvent>)

/** The Gym settings the calculator and hints read (GD §16.6, §16.8). Defaults when nothing was saved. */
data class GymPrefs(
    val unit: PlateUnit = PlateUnit.KG,
    val kg: PlateSetup = PlateMath.defaults(PlateUnit.KG),
    val lb: PlateSetup = PlateMath.defaults(PlateUnit.LB),
    val repRange: RepRange = ProgressionRules.DEFAULT_RANGE,
) {
    fun setup(unit: PlateUnit): PlateSetup = if (unit == PlateUnit.KG) kg else lb
}

/** An exercise's hint overrides and what they resolve to. */
data class ExerciseProgression(val range: RepRange, val incrementKg: Double, val customRange: Boolean, val customIncrement: Boolean)

/** What finishing a workout paid (GD §18.4): STR XP, how the sets counted, and whether a daily cap held some back. */
data class GymXpResult(val xp: Int, val fullSets: Int, val lightSets: Int, val unconfirmedSets: Int, val capped: Boolean) {
    companion object {
        val NONE = GymXpResult(0, 0, 0, 0, false)
    }
}

sealed interface FinishResult {
    data class Finished(
        val workoutId: String,
        val records: List<ExerciseRecords> = emptyList(),
        val xp: GymXpResult = GymXpResult.NONE,
    ) : FinishResult
    /** Nothing was ticked done, so the session was dropped rather than saved empty. */
    data object DiscardedEmpty : FinishResult
}

/** What completing a set did (GD §18.2). */
sealed interface CompleteResult {
    data object Completed : CompleteResult

    /** The next set of the same exercise has to wait [secondsLeft] more seconds. */
    data class TooSoon(val secondsLeft: Int) : CompleteResult

    /** No reps (or no hold time) logged yet. */
    data object NothingLogged : CompleteResult
    data object NotFound : CompleteResult
}

internal fun WorkoutSetEntity.toLogged() = LoggedSet(weightKg, reps, done)

internal fun WorkoutSetEntity.toFlow() = FlowRow(position, setIndex, side, done, superset)

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
 * The Gym tracker (GD §13.2): exercise library, the session in progress, routines and the derived overview. It writes no
 * ledger row of its own: STR XP for a finished workout is paid by [FinishWorkoutUseCase] (GD §18.4).
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

    suspend fun workout(id: String): WorkoutEntity? = gymDao.workout(id)

    /** Whether the end of a rest should sound and vibrate (Gym settings, GD §18.1); both on until set otherwise. */
    suspend fun restAlerts(): RestAlerts = gymDao.gymSettings().let { RestAlerts(sound = it?.restSound ?: true, vibrate = it?.restVibrate ?: true) }

    /** Starts a session (or returns the one already running), optionally filled from [routineId]. */
    suspend fun start(name: String, routineId: String? = null): String = transactions.run {
        gymDao.activeWorkout()?.let { return@run it.id }
        val coached = gymDao.gymSettings()?.coached ?: true
        val workout = WorkoutEntity(
            id = UUID.randomUUID().toString(), name = name.trim().take(MAX_NAME), gameDate = today(), startedAt = clock.now(),
            routineId = routineId, mode = if (coached) WorkoutEntity.MODE_COACHED else WorkoutEntity.MODE_LIST,
        )
        gymDao.upsertWorkout(workout)
        if (routineId != null) {
            val byId = exercises().associateBy { it.id }
            gymDao.routineExercises(routineId).forEach { row ->
                byId[row.exerciseId]?.let { addExerciseRows(workout.id, it, row.position, row.setCount, row.restSec, row) }
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
    private suspend fun addExerciseRows(
        workoutId: String,
        exercise: Exercise,
        position: Int,
        setCount: Int?,
        restSec: Int? = null,
        /** The routine row this exercise comes from: its section, superset and target range are copied onto each set. */
        planned: RoutineExerciseEntity? = null,
    ) {
        val previous = gymDao.previousSets(exercise.id)
        val previousBySet = previous.groupBy { it.setIndex }
        val hint = hint(exercise.id) as? ProgressionHint.Suggest
        // The hint is for the working sets (the heaviest of last time); warm-up ramps and back-offs repeat as they were.
        val heaviest = previous.maxOfOrNull { it.weightKg } ?: 0.0
        val working = previous.filter { it.weightKg == heaviest }
        val count = (setCount ?: previousBySet.size).coerceIn(1, MAX_SETS_PER_EXERCISE)
        val sides = if (planned?.unilateral == true) listOf("LEFT", "RIGHT") else listOf(null)
        val timed = planned?.measure == RoutineExerciseEntity.MEASURE_TIME
        val rows = (0 until count).flatMap { i ->
            val template = previousBySet[i]?.first() ?: previous.lastOrNull()
            val workingIndex = if (template == null) -1 else working.indexOfFirst { it.id == template.id }
            sides.map { side ->
                WorkoutSetEntity(
                    id = UUID.randomUUID().toString(),
                    workoutId = workoutId,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    primaryMuscles = exercise.primary.map { it.key }.toSet(),
                    secondaryMuscles = exercise.secondary.map { it.key }.toSet(),
                    position = position,
                    setIndex = i,
                    // The last session, nudged by the progression hint on the working sets (it only suggests; the hunter can change it).
                    weightKg = if (workingIndex >= 0 && hint != null && hint.weightKg > 0.0) hint.weightKg else template?.weightKg ?: 0.0,
                    reps = if (timed) {
                        0
                    } else if (workingIndex >= 0 && hint != null) {
                        hint.reps.getOrNull(workingIndex) ?: hint.reps.lastOrNull() ?: template?.reps ?: 0
                    } else {
                        template?.reps ?: planned?.repMin ?: 0
                    },
                    done = false,
                    restSec = RestRules.clamp(restSec ?: previous.lastOrNull()?.restSec),
                    section = planned?.section ?: RoutineExerciseEntity.SECTION_WORK,
                    // A timed exercise (plank) logs seconds held: a set with a hold time is a timed set.
                    durationSec = if (timed) template?.durationSec ?: planned?.repMin ?: DEFAULT_HOLD_SEC else null,
                    side = side,
                    superset = planned?.superset,
                    repMin = planned?.repMin,
                    repMax = planned?.repMax,
                )
            }
        }
        gymDao.upsertSets(rows)
    }

    /** Last session's sets of [exerciseId], for the "previous" column. */
    suspend fun previousSets(exerciseId: String): List<WorkoutSetEntity> = gymDao.previousSets(exerciseId)

    /** One more set (both sides for a one-sided exercise), copied from the last one and not done. */
    suspend fun addSet(workoutId: String, position: Int) = transactions.run {
        val sets = gymDao.sets(workoutId).filter { it.position == position }
        val lastIndex = sets.maxOfOrNull { it.setIndex } ?: return@run
        if (lastIndex + 1 >= MAX_SETS_PER_EXERCISE) return@run
        gymDao.upsertSets(
            sets.filter { it.setIndex == lastIndex }.map {
                it.copy(id = UUID.randomUUID().toString(), setIndex = lastIndex + 1, done = false, completedAt = null)
            },
        )
    }

    /** Takes the last set away (both sides), unless it was done or it is the only one. */
    suspend fun removeLastSet(workoutId: String, position: Int) = transactions.run {
        val sets = gymDao.sets(workoutId).filter { it.position == position }
        val lastIndex = sets.maxOfOrNull { it.setIndex } ?: return@run
        if (sets.map { it.setIndex }.distinct().size < 2) return@run
        sets.filter { it.setIndex == lastIndex && !it.done }.forEach { gymDao.deleteSet(it.id) }
    }

    /** Sets how long to rest between this exercise's sets; null turns the timer off for it. */
    suspend fun setRest(workoutId: String, position: Int, restSec: Int?) {
        gymDao.setExerciseRest(workoutId, position, RestRules.clamp(restSec))
    }

    /**
     * A direct edit of a whole set, with no minimum-rest check: kept for restoring and for tests. The screens use
     * [completeSet] and the field-level setters, which read the row fresh and enforce the minimum rest.
     */
    suspend fun updateSet(set: WorkoutSetEntity, weightKg: Double, reps: Int, done: Boolean) = transactions.run {
        val nowDone = done && reps > 0
        if (nowDone && !set.done) startRest(set.workoutId, gymDao.sets(set.workoutId).firstOrNull { it.id == set.id }?.restSec ?: set.restSec)
        gymDao.upsertSets(
            listOf(
                set.copy(
                    weightKg = weightKg.coerceIn(0.0, TrainingMath.MAX_WEIGHT_KG),
                    reps = reps.coerceIn(0, TrainingMath.MAX_REPS),
                    done = nowDone,
                    completedAt = if (nowDone) set.completedAt ?: clock.now() else null,
                ),
            ),
        )
    }

    /**
     * Completes a set (GD §18.2): refused with [CompleteResult.TooSoon] until the minimum rest since the previous set of the
     * same exercise has passed, and with [CompleteResult.NothingLogged] while there is nothing to log. Stamps the
     * completion time, and starts the rest unless a superset partner or the other side is still to do. A write of one
     * field, read fresh, so a slow keystroke can never undo it.
     */
    suspend fun completeSet(setId: String): CompleteResult = transactions.run {
        val set = gymDao.set(setId) ?: return@run CompleteResult.NotFound
        if (set.done) return@run CompleteResult.Completed
        val logged = set.durationSec?.let { it > 0 } ?: (set.reps > 0)
        if (!logged) return@run CompleteResult.NothingLogged
        val rows = gymDao.sets(set.workoutId)
        val now = clock.now()
        val previousAt = rows
            .filter { it.exerciseId == set.exerciseId && it.done && it.setIndex != set.setIndex }
            .mapNotNull { it.completedAt }
            .maxOrNull()
        val left = RestRules.minRestLeft(previousAt, now, set.restSec)
        if (left > 0) return@run CompleteResult.TooSoon(left)
        val done = set.copy(done = true, completedAt = now)
        gymDao.upsertSets(listOf(done))
        val flow = rows.map { if (it.id == set.id) done else it }.map(WorkoutSetEntity::toFlow)
        if (CoachedFlow.restAfter(flow, done.toFlow())) startRest(set.workoutId, set.restSec)
        CompleteResult.Completed
    }

    /** Opens a done set again so it can be edited; it counts as not done and has no completion time. */
    suspend fun reopenSet(setId: String) = editSets(setId, alsoOtherSide = false) { it.copy(done = false, completedAt = null) }

    /** Sets the weight of a set; the other side of a one-sided set follows while it is not done. */
    suspend fun setWeight(setId: String, kg: Double) = editSets(setId) { it.copy(weightKg = kg.coerceIn(0.0, TrainingMath.MAX_WEIGHT_KG)) }

    suspend fun setReps(setId: String, reps: Int) = editSets(setId) { it.copy(reps = reps.coerceIn(0, TrainingMath.MAX_REPS)) }

    /** Seconds held, for a timed set. */
    suspend fun setHold(setId: String, seconds: Int) = editSets(setId) { it.copy(durationSec = seconds.coerceIn(0, MAX_HOLD_SEC)) }

    private suspend fun editSets(setId: String, alsoOtherSide: Boolean = true, change: (WorkoutSetEntity) -> WorkoutSetEntity) = transactions.run {
        val set = gymDao.set(setId) ?: return@run
        val targets = if (alsoOtherSide && set.side != null) {
            gymDao.sets(set.workoutId).filter { it.position == set.position && it.setIndex == set.setIndex && (it.id == set.id || !it.done) }
        } else {
            listOf(set)
        }
        gymDao.upsertSets(targets.map(change))
    }

    /** Lengthens or shortens the running rest by [deltaSec] (−15 and +15 on the rest screen); it never goes past 10 minutes. */
    suspend fun adjustRest(workoutId: String, deltaSec: Int) = transactions.run {
        val workout = gymDao.workout(workoutId) ?: return@run
        val ends = workout.restEndsAt ?: return@run
        val total = workout.restTotalSec ?: return@run
        val now = clock.now()
        val newEnds = ends.plusSeconds(deltaSec.toLong())
        val remaining = Duration.between(now, newEnds).seconds
        if (remaining > RestRules.MAX_SEC) return@run
        gymDao.upsertWorkout(
            workout.copy(restEndsAt = if (newEnds.isBefore(now)) now else newEnds, restTotalSec = (total + deltaSec).coerceIn(RestRules.STEP_SEC, RestRules.MAX_SEC)),
        )
    }

    /** Switches between coached (one exercise at a time) and the list of every set. */
    suspend fun setMode(workoutId: String, mode: String) = gymDao.setWorkoutMode(workoutId, mode)

    /**
     * "Swap with" (GD §18.2): the sets still to do become [newExerciseId], keeping their count, targets and rest. Sets already
     * done stay as they were, under the old exercise, so the log tells the truth.
     */
    suspend fun replaceExercise(workoutId: String, position: Int, newExerciseId: String) = transactions.run {
        val exercise = exercise(newExerciseId) ?: return@run
        val rows = gymDao.sets(workoutId).filter { it.position == position }
        val remaining = rows.filter { !it.done }
        if (remaining.isEmpty()) return@run
        val target = if (rows.any { it.done }) position + 1 else position
        if (target != position) gymDao.shiftPositionsAfter(workoutId, position)
        val previous = gymDao.previousSets(exercise.id)
        gymDao.upsertSets(
            remaining.map { row ->
                val template = previous.getOrNull(row.setIndex) ?: previous.lastOrNull()
                row.copy(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    primaryMuscles = exercise.primary.map { it.key }.toSet(),
                    secondaryMuscles = exercise.secondary.map { it.key }.toSet(),
                    position = target,
                    weightKg = template?.weightKg ?: 0.0,
                    reps = if (row.durationSec != null) row.reps else template?.reps ?: row.repMin ?: row.reps,
                )
            },
        )
    }

    /** Drops the sets of an exercise that are not done yet and moves on; done sets stay. */
    suspend fun skipExercise(workoutId: String, position: Int) = transactions.run {
        gymDao.sets(workoutId).filter { it.position == position && !it.done }.forEach { gymDao.deleteSet(it.id) }
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
        // Judged against finished sessions only: this one is still open, so it is not in its own history.
        val records = sets.filter { it.done }.groupBy { it.exerciseId }.mapNotNull { (exerciseId, own) ->
            val history = gymDao.exerciseSets(exerciseId).toSessions().map { it.sets }
            val events = RecordRules.events(history, own.sortedBy { it.setIndex }.map { it.toLogged() })
            if (events.isEmpty()) null else ExerciseRecords(exerciseId, own.first().exerciseName, events)
        }
        sets.filterNot { it.done }.forEach { gymDao.deleteSet(it.id) }
        val now = clock.now()
        gymDao.upsertWorkout(workout.copy(endedAt = now, restEndsAt = null, restTotalSec = null))
        FinishResult.Finished(workoutId, records)
    }

    // Progression hints (GD §16.6)

    /** The rep range and weight step for [exerciseId]: its own override, else the Gym default and the muscle-based step. */
    suspend fun progression(exerciseId: String, primary: Set<Muscle>): ExerciseProgression {
        val pref = gymDao.exercisePref(exerciseId)
        val default = prefs().repRange
        val range = if (pref?.repMin != null && pref.repMax != null) RepRange(pref.repMin, pref.repMax) else default
        val increment = pref?.incrementKg
        return ExerciseProgression(range, increment ?: ProgressionRules.defaultIncrement(primary), pref?.repMin != null, increment != null)
    }

    /** The hint for set 1 of [exerciseId], from its last finished session; null with no history. */
    suspend fun hint(exerciseId: String): ProgressionHint? {
        val last = gymDao.previousSets(exerciseId)
        val first = last.firstOrNull() ?: return null
        val settings = progression(exerciseId, first.primaryMuscles.mapNotNull(Muscle::fromKey).toSet())
        return ProgressionRules.suggest(last.map { it.toLogged() }, settings.range, settings.incrementKg)
    }

    /** Saves one exercise's overrides; a null argument goes back to the default. Invalid input is ignored. */
    suspend fun setExercisePref(exerciseId: String, range: RepRange?, incrementKg: Double?) {
        val validRange = range?.takeIf { it.isValid }
        val validStep = incrementKg?.takeIf { ProgressionRules.isValidIncrement(it) }
        if (validRange == null && validStep == null) {
            gymDao.deleteExercisePref(exerciseId)
        } else {
            gymDao.upsertExercisePref(ExercisePrefEntity(exerciseId, validRange?.min, validRange?.max, validStep))
        }
    }

    // History and records (GD §16.7, §16.9)

    /** Finished sessions of [exerciseId], oldest first. */
    suspend fun history(exerciseId: String): List<ExerciseSession> = gymDao.exerciseSets(exerciseId).toSessions()

    /** The done sets of each earlier finished session, oldest first: what a set is compared with for a record. */
    suspend fun recordHistory(exerciseId: String): List<List<LoggedSet>> = history(exerciseId).map { it.sets }

    // Gym settings

    suspend fun prefs(): GymPrefs = gymDao.gymSettings().toPrefs()

    fun observePrefs(): Flow<GymPrefs> = gymDao.observeGymSettings().map { it.toPrefs() }

    suspend fun savePrefs(prefs: GymPrefs) {
        val kg = prefs.kg.sanitised(PlateUnit.KG)
        val lb = prefs.lb.sanitised(PlateUnit.LB)
        val range = prefs.repRange.takeIf { it.isValid } ?: ProgressionRules.DEFAULT_RANGE
        // Copy the stored row so the answers and rest settings added in v29 (GD §18.1) survive a plate or unit change.
        val stored = gymDao.gymSettings()
        val changed = GymSettingsEntity(
            barKg = kg.barWeight, platesKg = kg.plates.joinToString(","), barLb = lb.barWeight, platesLb = lb.plates.joinToString(","),
            unit = prefs.unit.name, repMin = range.min, repMax = range.max,
        )
        gymDao.upsertGymSettings(
            stored?.copy(
                barKg = changed.barKg, platesKg = changed.platesKg, barLb = changed.barLb, platesLb = changed.platesLb,
                unit = changed.unit, repMin = changed.repMin, repMax = changed.repMax,
            ) ?: changed,
        )
    }

    /** Input sanity only: a bar weight within 0..1000 and positive plates; an empty plate list falls back to the defaults. */
    private fun PlateSetup.sanitised(unit: PlateUnit): PlateSetup {
        val bar = barWeight.takeIf { it > 0.0 && it <= TrainingMath.MAX_WEIGHT_KG } ?: PlateMath.defaults(unit).barWeight
        val plates = plates.filter { it > 0.0 && it <= TrainingMath.MAX_WEIGHT_KG }.distinct().sortedDescending()
        return PlateSetup(bar, plates.ifEmpty { PlateMath.defaults(unit).plates })
    }

    private fun GymSettingsEntity?.toPrefs(): GymPrefs {
        if (this == null) return GymPrefs()
        fun parse(csv: String) = csv.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        return GymPrefs(
            unit = PlateUnit.entries.firstOrNull { it.name == unit } ?: PlateUnit.KG,
            kg = PlateSetup(barKg, parse(platesKg)),
            lb = PlateSetup(barLb, parse(platesLb)),
            repRange = RepRange(repMin, repMax).takeIf { it.isValid } ?: ProgressionRules.DEFAULT_RANGE,
        )
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
        val routine = RoutineEntity(
            id = UUID.randomUUID().toString(), name = name.trim().take(MAX_NAME), updatedAt = clock.now(),
            sortOrder = (gymDao.routines().maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        gymDao.upsertRoutine(routine)
        gymDao.insertRoutineExercises(
            exercises.mapIndexed { i, e ->
                val first = e.sets.first()
                RoutineExerciseEntity(
                    routineId = routine.id, position = i, exerciseId = e.exerciseId,
                    // A one-sided exercise has two rows per set.
                    setCount = e.sets.map { it.setIndex }.distinct().size.coerceAtLeast(1), restSec = e.restSec,
                    repMin = first.repMin, repMax = first.repMax, superset = first.superset, unilateral = first.side != null,
                    section = first.section,
                    measure = if (first.durationSec != null) RoutineExerciseEntity.MEASURE_TIME else RoutineExerciseEntity.MEASURE_REPS,
                )
            },
        )
        routine.id
    }

    suspend fun deleteRoutine(routineId: String) = transactions.run {
        gymDao.clearRoutineExercises(routineId)
        // The weekly schedule and one-off overrides must not point at a routine that is gone.
        gymDao.deleteSlotsOfRoutine(routineId)
        gymDao.deleteOverridesOfRoutine(routineId)
        gymDao.deleteRoutineRow(routineId)
    }

    // Overview

    fun observeOverview(): Flow<GymOverview> = combine(
        gymDao.observeFinishedWorkouts(),
        gymDao.observeFinishedSets(),
        gymDao.observeRoutines(),
        gymDao.observeRoutineExercises(),
        hunterDao.observeProfile(),
    ) { workouts, sets, routines, routineRows, profile -> overview(today(), workouts, sets, routines, routineRows, profile?.trainingDays ?: TrainingDays.NONE) }

    private suspend fun overview(
        today: LocalDate,
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        routines: List<RoutineEntity>,
        routineRows: List<RoutineExerciseEntity>,
        trainingMask: Int,
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
            trainingDay = TrainingDays.isTrainingDay(trainingMask, today),
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
        const val MAX_HOLD_SEC = 3_600
        const val DEFAULT_HOLD_SEC = 30
    }
}

/** Minutes and seconds since [start], for the session timer. */
fun elapsedSince(start: Instant, now: Instant): Duration = Duration.between(start, now).coerceAtLeast(Duration.ZERO)
