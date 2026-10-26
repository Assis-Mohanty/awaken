package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.CustomExerciseEntity
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

    @Query("SELECT * FROM routine ORDER BY updated_at DESC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

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

    @Query("SELECT * FROM workout_set WHERE workout_id = :workoutId ORDER BY position, set_index")
    fun observeSets(workoutId: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE workout_id = :workoutId ORDER BY position, set_index")
    suspend fun sets(workoutId: String): List<WorkoutSetEntity>

    /** Every set of every finished workout: history, PRs and muscle load are derived from these. */
    @Query(
        "SELECT s.* FROM workout_set s JOIN workout w ON w.id = s.workout_id " +
            "WHERE w.ended_at IS NOT NULL ORDER BY w.started_at, s.position, s.set_index",
    )
    fun observeFinishedSets(): Flow<List<WorkoutSetEntity>>

    /** Sets of [exerciseId] in the most recent finished workout that has it: the "previous" column. */
    @Query(
        "SELECT s.* FROM workout_set s WHERE s.exercise_id = :exerciseId AND s.workout_id = (" +
            "SELECT w.id FROM workout w JOIN workout_set x ON x.workout_id = w.id " +
            "WHERE x.exercise_id = :exerciseId AND w.ended_at IS NOT NULL ORDER BY w.started_at DESC LIMIT 1" +
            ") ORDER BY s.set_index",
    )
    suspend fun previousSets(exerciseId: String): List<WorkoutSetEntity>

    @Query("SELECT COUNT(*) FROM workout WHERE ended_at IS NOT NULL AND game_date >= :from AND game_date <= :to")
    suspend fun finishedWorkoutCount(from: LocalDate, to: LocalDate): Int

    @Upsert
    suspend fun upsertSets(sets: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query("DELETE FROM workout_set WHERE workout_id = :workoutId AND position = :position")
    suspend fun deleteExerciseSets(workoutId: String, position: Int)

    @Query("DELETE FROM workout_set WHERE workout_id = :workoutId")
    suspend fun deleteAllSets(workoutId: String)
}
