package com.assismohanty.awaken.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

// Diet v2 (docs/diet/PLAN.md section 3.8, schema v32). Trackers only: none of these is a ledger table and nothing here
// pays XP, gold or items (GD 20.5, ENGINE_DECISIONS #95 and #186), so rows may be edited and deleted. No foreign keys:
// parents and children are written and deleted together in one transaction, which keeps export and import order-free.

/** Where a diary row came from. */
enum class EntrySource { SEARCH, BARCODE, PHOTO, QUICK, MEAL, RECIPE, PLAN }

enum class MeasureKind { WAIST, HIP, CHEST, ARM, THIGH }

enum class ScanKind { PHOTO, BARCODE }

/**
 * A meal of the day. The four built-in slots use the same ids as the old `Meal` enum names (so `food_log.meal` needs no
 * rewrite); the hunter's own meals get `custom:<uuid>`. A built-in with a null [name] uses the app's own word.
 */
@Entity(tableName = "meal_slot")
data class MealSlotEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val position: Int,
    @ColumnInfo(name = "built_in") val builtIn: Boolean,
)

@Entity(tableName = "favourite_food")
data class FavouriteFoodEntity(
    @PrimaryKey @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "added_at") val addedAt: Instant,
)

/** A group of foods the hunter re-adds in one tap. Items snapshot their nutrients like diary rows do. */
@Entity(tableName = "saved_meal")
data class SavedMealEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "saved_meal_item", indices = [Index("saved_meal_id")])
data class SavedMealItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "saved_meal_id") val savedMealId: String,
    val position: Int,
    @ColumnInfo(name = "food_id") val foodId: String,
    val name: String,
    val grams: Double,
    @ColumnInfo(name = "portion_label") val portionLabel: String?,
    @ColumnInfo(name = "nutrients_json") val nutrientsJson: String,
)

/** A recipe the hunter built. [id] is `recipe:<uuid>` so it can be logged like any food. */
@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val servings: Double,
    /** Weight after cooking; null = the raw total. */
    @ColumnInfo(name = "cooked_grams") val cookedGrams: Double?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "recipe_ingredient", indices = [Index("recipe_id")])
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val position: Int,
    @ColumnInfo(name = "food_id") val foodId: String,
    val name: String,
    val grams: Double,
    @ColumnInfo(name = "nutrients_json") val nutrientsJson: String,
)

@Entity(tableName = "water_log", indices = [Index("game_date")])
data class WaterLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    val ml: Int,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

@Entity(tableName = "weight_log", indices = [Index("game_date")])
data class WeightLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    val note: String?,
)

@Entity(tableName = "body_measure_log", indices = [Index("game_date")])
data class BodyMeasureLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    val kind: MeasureKind,
    @ColumnInfo(name = "value_cm") val valueCm: Double,
)

/** One fast. [endedAt] is null while it runs. */
@Entity(tableName = "fasting_session")
data class FastingSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "target_minutes") val targetMinutes: Int,
    @ColumnInfo(name = "ended_at") val endedAt: Instant?,
)

/** A food planned for a day. [meal] is a [MealSlotEntity] id. */
@Entity(tableName = "meal_plan_entry", indices = [Index("game_date")])
data class MealPlanEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    val meal: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    val name: String,
    val grams: Double,
    @ColumnInfo(name = "portion_label") val portionLabel: String?,
    @ColumnInfo(name = "nutrients_json") val nutrientsJson: String,
    val logged: Boolean,
)

@Entity(tableName = "diary_note")
data class DiaryNoteEntity(
    @PrimaryKey @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    val text: String,
)

/** A scanner guess and what the hunter picked. Stays on the phone (plan 5.1); no image is ever kept. */
@Entity(tableName = "scan_feedback")
data class ScanFeedbackEntity(
    @PrimaryKey val id: String,
    val at: Instant,
    val kind: ScanKind,
    @ColumnInfo(name = "shown_json") val shownJson: String,
    @ColumnInfo(name = "chosen_food_id") val chosenFoodId: String?,
)
