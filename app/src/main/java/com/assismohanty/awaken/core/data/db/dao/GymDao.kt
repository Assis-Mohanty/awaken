package com.assismohanty.awaken.core.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.CustomExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.ExercisePrefEntity
import com.assismohanty.awaken.core.data.db.entity.DayOverrideEntity
import com.assismohanty.awaken.core.data.db.entity.GymSettingsEntity
import com.assismohanty.awaken.core.data.db.entity.WeekSlotEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Gym tracker (GD §13.2). Not a ledger: rows are edited and deleted freely. */
@Dao
interface GymDao {
    @Query("SELECT * FROM custom_exercise ORDER BY name")
    fun observeCustomExercises(): Flow<List<CustomExerciseEntity>>

    @Query("SELECT * FROM custom_exercise")
    suspend fun customExercises(): List<CustomExerciseEntity>

    @Upsert
    suspend fun upsertCustomExercise(exercise: CustomExerciseEntity)

    @Query("DELETE FROM custom_exercise WHERE id = :id")
    suspend fun deleteCustomExercise(id: String)

    // Routines

    @Query("SELECT * FROM routine ORDER BY sort_order, updated_at DESC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine ORDER BY sort_order, updated_at DESC")
    suspend fun routines(): List<RoutineEntity>

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun routine(id: String): RoutineEntity?

    @Query("SELECT * FROM routine_exercise ORDER BY routine_id, position")
    fun observeRoutineExercises(): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routine_exercise WHERE routine_id = :routineId ORDER BY position")
    suspend fun routineExercises(routineId: String): List<RoutineExerciseEntity>

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Insert
    suspend fun insertRoutineExercises(rows: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercise WHERE routine_id = :routineId")
    suspend fun clearRoutineExercises(routineId: String)

    @Query("DELETE FROM routine WHERE id = :routineId")
    suspend fun deleteRoutineRow(routineId: String)

    // Weekly schedule and one-off day overrides (GD §18.1)

    @Query("SELECT * FROM week_slot ORDER BY weekday")
    fun observeWeekSlots(): Flow<List<WeekSlotEntity>>

    @Query("SELECT * FROM week_slot ORDER BY weekday")
    suspend fun weekSlots(): List<WeekSlotEntity>

    @Upsert
    suspend fun upsertWeekSlot(slot: WeekSlotEntity)

    @Query("DELETE FROM week_slot WHERE weekday = :weekday")
    suspend fun deleteWeekSlot(weekday: Int)

    @Query("DELETE FROM week_slot WHERE routine_id = :routineId")
    suspend fun deleteSlotsOfRoutine(routineId: String)

    @Query("SELECT * FROM day_override WHERE game_date >= :from AND game_date <= :to ORDER BY game_date")
    fun observeDayOverrides(from: LocalDate, to: LocalDate): Flow<List<DayOverrideEntity>>

    @Query("SELECT * FROM day_override WHERE game_date >= :from AND game_date <= :to ORDER BY game_date")
    suspend fun dayOverrides(from: LocalDate, to: LocalDate): List<DayOverrideEntity>

    @Upsert
    suspend fun upsertDayOverrides(rows: List<DayOverrideEntity>)

    @Query("DELETE FROM day_override WHERE routine_id = :routineId")
    suspend fun deleteOverridesOfRoutine(routineId: String)

    /** Overrides older than the cutoff no longer matter; trimmed so the table stays small. */
    @Query("DELETE FROM day_override WHERE game_date < :before")
    suspend fun deleteOverridesBefore(before: LocalDate)

    // Workouts

    @Query("SELECT * FROM workout WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    fun observeActiveWorkout(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun activeWorkout(): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun workout(id: String): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE ended_at IS NOT NULL ORDER BY started_at DESC")
    fun observeFinishedWorkouts(): Flow<List<WorkoutEntity>>

    @Upsert
    suspend fun upsertWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkoutRow(id: String)

    // Sets

    @Query("SELECT * FROM workout_set WHERE workout_id = :workoutId ORDER BY position, set_index, side")
    fun observeSets(workoutId: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE workout_id = :workoutId ORDER BY position, set_index, side")
    suspend fun sets(workoutId: String): List<WorkoutSetEntity>

    /** Every set of every finished workout: history, PRs and muscle load are derived from these. */
    @Query(
        "SELECT s.* FROM workout_set s JOIN workout w ON w.id = s.workout_id " +
            "WHERE w.ended_at IS NOT NULL ORDER BY w.started_at, s.position, s.set_index",
    )
    fun observeFinishedSets(): Flow<List<WorkoutSetEntity>>

    /**
     * The done work sets of [exerciseId] in the most recent finished workout that has any: "Last time". Warm-ups and
     * sets that were never done are not history, so a workout with only those is skipped rather than shown as empty.
     */
    @Query(
        "SELECT s.* FROM workout_set s WHERE s.exercise_id = :exerciseId AND s.done = 1 AND s.section = 'WORK' AND s.workout_id = (" +
            "SELECT w.id FROM workout w JOIN workout_set x ON x.workout_id = w.id " +
            "WHERE x.exercise_id = :exerciseId AND x.done = 1 AND x.section = 'WORK' AND w.ended_at IS NOT NULL " +
            "ORDER BY w.started_at DESC LIMIT 1" +
            ") ORDER BY s.set_index, s.side",
    )
    suspend fun previousSets(exerciseId: String): List<WorkoutSetEntity>

    @Query("SELECT COUNT(*) FROM workout WHERE ended_at IS NOT NULL AND game_date >= :from AND game_date <= :to")
    suspend fun finishedWorkoutCount(from: LocalDate, to: LocalDate): Int

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun set(id: String): WorkoutSetEntity?

    @Upsert
    suspend fun upsertSets(sets: List<WorkoutSetEntity>)

    /** Moves every exercise after [position] one place down, to make room for a new one (swap with). */
    @Query("UPDATE workout_set SET position = position + 1 WHERE workout_id = :workoutId AND position > :position")
    suspend fun shiftPositionsAfter(workoutId: String, position: Int)

    @Query("UPDATE workout SET mode = :mode WHERE id = :workoutId")
    suspend fun setWorkoutMode(workoutId: String, mode: String)

    /** Sets an exercise's rest on every one of its set rows. */
    @Query("UPDATE workout_set SET rest_sec = :restSec WHERE workout_id = :workoutId AND position = :position")
    suspend fun setExerciseRest(workoutId: String, position: Int, restSec: Int?)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query("DELETE FROM workout_set WHERE workout_id = :workoutId AND position = :position")
    suspend fun deleteExerciseSets(workoutId: String, position: Int)

    @Query("DELETE FROM workout_set WHERE workout_id = :workoutId")
    suspend fun deleteAllSets(workoutId: String)

    // Progression, records and history (GD §16.6-16.9). Derived from finished workouts; nothing here is a ledger.

    /** Done set rows of finished workouts for one exercise, oldest first. */
    @Query(
        "SELECT s.workout_id AS workoutId, s.exercise_id AS exerciseId, w.game_date AS gameDate, s.set_index AS setIndex, " +
            "s.weight_kg AS weightKg, s.reps AS reps, s.done AS done FROM workout_set s JOIN workout w ON w.id = s.workout_id " +
            "WHERE w.ended_at IS NOT NULL AND s.exercise_id = :exerciseId ORDER BY w.started_at, s.set_index",
    )
    suspend fun exerciseSets(exerciseId: String): List<ExerciseSetRow>

    /** Every set row of every finished workout, oldest first: replayed to count personal-record events. */
    @Query(
        "SELECT s.workout_id AS workoutId, s.exercise_id AS exerciseId, w.game_date AS gameDate, s.set_index AS setIndex, " +
            "s.weight_kg AS weightKg, s.reps AS reps, s.done AS done FROM workout_set s JOIN workout w ON w.id = s.workout_id " +
            "WHERE w.ended_at IS NOT NULL ORDER BY w.started_at, s.exercise_id, s.set_index",
    )
    suspend fun allFinishedSetRows(): List<ExerciseSetRow>

    @Query("SELECT * FROM exercise_pref WHERE exercise_id = :exerciseId")
    suspend fun exercisePref(exerciseId: String): ExercisePrefEntity?

    @Upsert
    suspend fun upsertExercisePref(pref: ExercisePrefEntity)

    @Query("DELETE FROM exercise_pref WHERE exercise_id = :exerciseId")
    suspend fun deleteExercisePref(exerciseId: String)

    @Query("SELECT * FROM gym_settings WHERE id = 1")
    suspend fun gymSettings(): GymSettingsEntity?

    @Query("SELECT * FROM gym_settings WHERE id = 1")
    fun observeGymSettings(): Flow<GymSettingsEntity?>

    @Upsert
    suspend fun upsertGymSettings(settings: GymSettingsEntity)
}

/** A set row joined with its workout's game-day. */
data class ExerciseSetRow(
    val workoutId: String,
    val exerciseId: String,
    val gameDate: LocalDate,
    val setIndex: Int,
    @ColumnInfo(name = "weightKg") val weightKg: Double,
    val reps: Int,
    val done: Boolean,
)
