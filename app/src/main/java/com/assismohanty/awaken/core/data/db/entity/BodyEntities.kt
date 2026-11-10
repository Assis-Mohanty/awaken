package com.assismohanty.awaken.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.assismohanty.awaken.engine.body.ActivityLevel
import com.assismohanty.awaken.engine.body.LossPace
import com.assismohanty.awaken.engine.body.MacroPreset
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
    /** Order in My routines (GD §18.1); lower first. */
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    /** The plan this routine was copied from, if any. The copy never changes when the plan does. */
    @ColumnInfo(name = "source_plan_id") val sourcePlanId: String? = null,
)

@Entity(tableName = "routine_exercise", primaryKeys = ["routine_id", "position"])
data class RoutineExerciseEntity(
    @ColumnInfo(name = "routine_id") val routineId: String,
    val position: Int,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "set_count") val setCount: Int,
    /** Rest between this exercise's sets in seconds; null = no rest timer (GD §13.2). */
    @ColumnInfo(name = "rest_sec") val restSec: Int? = null,
    /** Target reps (or seconds, when [measure] is `TIME`) as a range; null = no target (GD §18.1). */
    @ColumnInfo(name = "rep_min") val repMin: Int? = null,
    @ColumnInfo(name = "rep_max") val repMax: Int? = null,
    /** Exercises of one routine sharing a letter are done back to back. */
    val superset: String? = null,
    /** Left and right are two steps of one set. */
    @ColumnInfo(defaultValue = "0") val unilateral: Boolean = false,
    /** `WORK` or `WARMUP`. */
    @ColumnInfo(defaultValue = "WORK") val section: String = SECTION_WORK,
    /** `REPS` or `TIME`. */
    @ColumnInfo(defaultValue = "REPS") val measure: String = MEASURE_REPS,
) {
    companion object {
        const val SECTION_WORK = "WORK"
        const val SECTION_WARMUP = "WARMUP"
        const val MEASURE_REPS = "REPS"
        const val MEASURE_TIME = "TIME"
    }
}

/** One weekday's routine; it repeats every week until changed (GD §18.1). [weekday] is ISO 1 = Monday … 7 = Sunday. */
@Entity(tableName = "week_slot")
data class WeekSlotEntity(
    @PrimaryKey val weekday: Int,
    @ColumnInfo(name = "routine_id") val routineId: String,
)

/** A one-off change for one game-date: another routine, or null for rest ("Swap with today"). */
@Entity(tableName = "day_override")
data class DayOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    @ColumnInfo(name = "routine_id") val routineId: String? = null,
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
    /** The routine this session was started from, if any. */
    @ColumnInfo(name = "routine_id") val routineId: String? = null,
    /** How it is logged: `LIST` (every set visible) or `COACHED` (one exercise at a time). Sessions before v29 are `LIST`. */
    @ColumnInfo(defaultValue = "LIST") val mode: String = MODE_LIST,
) {
    companion object {
        const val MODE_LIST = "LIST"
        const val MODE_COACHED = "COACHED"
    }
}

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
    /** When the set was completed; null for sets logged before v29 (they never pay XP, GD §18.4). */
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    /** `WORK` or `WARMUP`. Warm-ups pay no XP. */
    @ColumnInfo(defaultValue = "WORK") val section: String = RoutineExerciseEntity.SECTION_WORK,
    /** Seconds held, for a timed exercise such as a plank; null for a reps exercise. */
    @ColumnInfo(name = "duration_sec") val durationSec: Int? = null,
    /** `LEFT` or `RIGHT` for the two steps of a one-sided set; null otherwise. */
    val side: String? = null,
    val superset: String? = null,
    /** The planned range for this set, for "Set 2 of 3 · 8-10". */
    @ColumnInfo(name = "rep_min") val repMin: Int? = null,
    @ColumnInfo(name = "rep_max") val repMax: Int? = null,
)

/** A food the hunter added. [nutrientsJson] is `{"kcal":250,…}` per 100 g; [portionsJson] is `[["1 bowl",180],…]`. */
@Entity(tableName = "custom_food", indices = [Index("barcode")])
data class CustomFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "nutrients_json") val nutrientsJson: String,
    @ColumnInfo(name = "portions_json") val portionsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    val brand: String? = null,
    /** A normalised 13-digit barcode (engine `Barcode.canonical`), so a scan finds the hunter's own food first. */
    val barcode: String? = null,
    /** One serving in grams, when the label gives one. */
    @ColumnInfo(name = "serving_grams") val servingGrams: Double? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Instant = Instant.EPOCH,
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
    val note: String? = null,
    /** How many servings the hunter picked ("2" katori); null when they typed grams. [grams] is always the truth. */
    @ColumnInfo(name = "portion_count") val portionCount: Double? = null,
    @ColumnInfo(name = "entry_source", defaultValue = "SEARCH") val entrySource: EntrySource = EntrySource.SEARCH,
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
    @ColumnInfo(name = "loss_pace", defaultValue = "STEADY") val lossPace: LossPace = LossPace.STEADY,
    @ColumnInfo(name = "macro_preset", defaultValue = "BALANCED") val macroPreset: MacroPreset = MacroPreset.BALANCED,
    @ColumnInfo(name = "protein_per_kg") val proteinPerKg: Double? = null,
    /** A custom split in whole percent; all three or none. */
    @ColumnInfo(name = "split_protein_pct") val splitProteinPct: Int? = null,
    @ColumnInfo(name = "split_carbs_pct") val splitCarbsPct: Int? = null,
    @ColumnInfo(name = "split_fat_pct") val splitFatPct: Int? = null,
    /** The weight today's targets were planned from; it moves only when the hunter accepts new targets (GD 20.2). Null = [weightKg]. */
    @ColumnInfo(name = "plan_weight_kg") val planWeightKg: Double? = null,
    @ColumnInfo(name = "trend_dismissed_on") val trendDismissedOn: LocalDate? = null,
    @ColumnInfo(name = "water_target_ml") val waterTargetMl: Int? = null,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}

/** Per-exercise overrides for progression hints (GD §16.6). A null column means "use the default". */
@Entity(tableName = "exercise_pref")
data class ExercisePrefEntity(
    @PrimaryKey @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "rep_min") val repMin: Int? = null,
    @ColumnInfo(name = "rep_max") val repMax: Int? = null,
    @ColumnInfo(name = "increment_kg") val incrementKg: Double? = null,
)

/**
 * Gym settings (GD §16.6, §16.8): the plate calculator's bars and owned plates in both units, its last unit, and the
 * default rep range. One row, id 1; no row means every default. Plates are comma-separated numbers.
 */
@Entity(tableName = "gym_settings")
data class GymSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "bar_kg") val barKg: Double,
    @ColumnInfo(name = "plates_kg") val platesKg: String,
    @ColumnInfo(name = "bar_lb") val barLb: Double,
    @ColumnInfo(name = "plates_lb") val platesLb: String,
    val unit: String,
    @ColumnInfo(name = "rep_min") val repMin: Int,
    @ColumnInfo(name = "rep_max") val repMax: Int,
    // v29 (GD §18.1): the answers onboarding asked, editable in Gym. Null = not set. Lists are comma-separated.
    /** `BEGINNER`, `INTERMEDIATE` or `ADVANCED`. */
    @ColumnInfo(name = "fitness_level") val fitnessLevel: String? = null,
    /** `strength`, `muscle` or `general`. */
    val goal: String? = null,
    /** `Gear.key` values. */
    val equipment: String? = null,
    /** `InjuryRegion` names. */
    val injuries: String? = null,
    /** `M` or `F`; used only to compare lifts with published data. */
    val sex: String? = null,
    @ColumnInfo(name = "rest_compound_sec", defaultValue = "120") val restCompoundSec: Int = 120,
    @ColumnInfo(name = "rest_isolation_sec", defaultValue = "60") val restIsolationSec: Int = 60,
    @ColumnInfo(name = "rest_sound", defaultValue = "1") val restSound: Boolean = true,
    @ColumnInfo(name = "rest_vibrate", defaultValue = "1") val restVibrate: Boolean = true,
    /** Coached (one exercise at a time) is the default; false = every set visible. */
    @ColumnInfo(defaultValue = "1") val coached: Boolean = true,
)
