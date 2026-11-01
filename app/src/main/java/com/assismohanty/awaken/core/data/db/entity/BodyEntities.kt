package com.assismohanty.awaken.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.assismohanty.awaken.engine.body.ActivityLevel
import com.assismohanty.awaken.engine.body.Sex
import com.assismohanty.awaken.engine.body.WeightGoal
import java.time.Instant
import java.time.LocalDate

// Gym and Diet (GD §13). Trackers only: none of these tables is a ledger table and nothing here mints rewards, so rows
// may be edited and deleted (ENGINE_DECISIONS #95).

/** An exercise the hunter added. Muscles are catalog keys ("lower back"); equipment is an engine `Equipment` key. */
@Entity(tableName = "custom_exercise")
data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val equipment: String,
    @ColumnInfo(name = "primary_muscles") val primaryMuscles: Set<String>,
    @ColumnInfo(name = "secondary_muscles") val secondaryMuscles: Set<String>,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/** A saved workout plan: an ordered list of exercises with a set count each. */
@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "routine_exercise", primaryKeys = ["routine_id", "position"])
data class RoutineExerciseEntity(
    @ColumnInfo(name = "routine_id") val routineId: String,
    val position: Int,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "set_count") val setCount: Int,
    /** Rest between this exercise's sets in seconds; null = no rest timer (GD §13.2). */
    @ColumnInfo(name = "rest_sec") val restSec: Int? = null,
)

/** One session. [endedAt] null = still in progress (at most one at a time). */
@Entity(tableName = "workout", indices = [Index("game_date")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant? = null,
    /** The running rest timer: when it ends, and how long it was. Stored so it survives the app being killed. */
    @ColumnInfo(name = "rest_ends_at") val restEndsAt: Instant? = null,
    @ColumnInfo(name = "rest_total_sec") val restTotalSec: Int? = null,
)

/**
 * One set row. The exercise's name and muscles are snapshotted so history and muscle load stay put if a custom
 * exercise is renamed or deleted. [position] orders exercises within the workout, [setIndex] sets within one.
 */
@Entity(tableName = "workout_set", indices = [Index("workout_id"), Index("exercise_id")])
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    @ColumnInfo(name = "primary_muscles") val primaryMuscles: Set<String>,
    @ColumnInfo(name = "secondary_muscles") val secondaryMuscles: Set<String>,
    val position: Int,
    @ColumnInfo(name = "set_index") val setIndex: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
    val done: Boolean,
    /** The exercise's rest, repeated on each of its sets so history keeps what was used. */
    @ColumnInfo(name = "rest_sec") val restSec: Int? = null,
)

/** A food the hunter added. [nutrientsJson] is `{"kcal":250,…}` per 100 g; [portionsJson] is `[["1 bowl",180],…]`. */
@Entity(tableName = "custom_food")
data class CustomFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "nutrients_json") val nutrientsJson: String,
    @ColumnInfo(name = "portions_json") val portionsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * One diary row (GD §13.1): grams of a food on a game-day, with the food's name and per-100 g nutrients snapshotted so
 * the day never changes when the catalog or a custom food does.
 */
@Entity(tableName = "food_log", indices = [Index("game_date"), Index("food_id")])
data class FoodLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    val meal: Meal,
    @ColumnInfo(name = "food_id") val foodId: String,
    val name: String,
    val grams: Double,
    @ColumnInfo(name = "portion_label") val portionLabel: String?,
    @ColumnInfo(name = "nutrients_json") val nutrientsJson: String,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

/** The single Diet setup row (id 1). [overridesJson] maps nutrient keys to the hunter's own targets. */
@Entity(tableName = "nutrition_profile")
data class NutritionProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val sex: Sex,
    @ColumnInfo(name = "birth_year") val birthYear: Int,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val activity: ActivityLevel,
    val goal: WeightGoal,
    @ColumnInfo(name = "overrides_json") val overridesJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}
