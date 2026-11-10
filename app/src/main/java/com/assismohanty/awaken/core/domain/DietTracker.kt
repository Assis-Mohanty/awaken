package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.CatalogSearch
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodCatalog
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.DietDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.CustomFoodEntity
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.Meal
import com.assismohanty.awaken.core.data.db.entity.NutritionProfileEntity
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.body.ActivityLevel
import com.assismohanty.awaken.engine.body.BodyProfile
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.NutritionMath
import com.assismohanty.awaken.engine.body.NutritionPlan
import com.assismohanty.awaken.engine.body.Nutrients
import com.assismohanty.awaken.engine.body.Sex
import com.assismohanty.awaken.engine.body.WeightGoal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The Diet setup form's values. */
data class DietSetup(
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
    val goal: WeightGoal,
)

/** Setup plus the engine's plan for it on a given day. */
data class DietPlan(val setup: DietSetup, val overrides: Map<Nutrient, Double>, val plan: NutritionPlan)

/** One diary row with its amounts for the logged grams. */
data class DiaryEntry(val row: FoodLogEntity, val amounts: Nutrients)

data class DiaryDay(val gameDate: LocalDate, val meals: Map<Meal, List<DiaryEntry>>, val totals: Nutrients)

private fun FoodLogEntity.toEntry() = DiaryEntry(this, FoodCatalog.nutrientsFromJson(nutrientsJson).forGrams(grams))

internal fun CustomFoodEntity.toFood() =
    Food(id, name, FoodSource.CUSTOM, FoodCatalog.nutrientsFromJson(nutrientsJson), FoodCatalog.portionsFromJson(portionsJson))

/**
 * The food diary (GD §13.1): setup and targets, search, and the day's rows. A tracker only: nothing here touches the
 * ledger, XP or caps (ENGINE_DECISIONS #95).
 */
@Singleton
class DietTracker @Inject constructor(
    private val dietDao: DietDao,
    private val hunterDao: HunterDao,
    private val catalog: FoodCatalog,
    private val clock: Clock,
) {
    suspend fun today(): LocalDate = clock.gameDate(hunterDao.getProfile()?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR)

    // Setup and targets

    /** Null until the hunter has done the Diet setup. */
    fun observePlan(): Flow<DietPlan?> = dietDao.observeProfile().map { it?.let { profile -> planFor(profile, today()) } }

    private fun planFor(profile: NutritionProfileEntity, today: LocalDate): DietPlan {
        val setup = DietSetup(profile.sex, profile.birthYear, profile.heightCm, profile.weightKg, profile.activity, profile.goal)
        val overrides = overridesFromJson(profile.overridesJson)
        return DietPlan(setup, overrides, NutritionMath.plan(setup.toBody(today), overrides))
    }

    /** Engine plan for an unsaved form, for the live preview while setting up. */
    fun preview(setup: DietSetup, today: LocalDate): NutritionPlan = NutritionMath.plan(setup.toBody(today))

    private fun DietSetup.toBody(today: LocalDate) = BodyProfile(
        sex = sex,
        age = NutritionMath.ageOn(birthYear, today).coerceIn(NutritionMath.AGE_RANGE),
        heightCm = heightCm.coerceIn(NutritionMath.HEIGHT_CM_RANGE),
        weightKg = weightKg.coerceIn(NutritionMath.WEIGHT_KG_RANGE),
        activity = activity,
        goal = goal,
    )

    /** Saves the setup, keeping any target overrides already set. */
    suspend fun saveSetup(setup: DietSetup) {
        val overrides = dietDao.profile()?.overridesJson ?: EMPTY_JSON_OBJECT
        dietDao.upsertProfile(
            NutritionProfileEntity(
                sex = setup.sex,
                birthYear = setup.birthYear,
                heightCm = setup.heightCm.coerceIn(NutritionMath.HEIGHT_CM_RANGE),
                weightKg = setup.weightKg.coerceIn(NutritionMath.WEIGHT_KG_RANGE),
                activity = setup.activity,
                goal = setup.goal,
                overridesJson = overrides,
                updatedAt = clock.now(),
            ),
        )
    }

    /** Replaces the target overrides; a nutrient left out goes back to its default. */
    suspend fun saveOverrides(overrides: Map<Nutrient, Double>) {
        val profile = dietDao.profile() ?: return
        val clean = overrides.filterValues { it >= 0.0 }
        dietDao.upsertProfile(profile.copy(overridesJson = overridesToJson(clean), updatedAt = clock.now()))
    }

    // Diary

    fun observeDay(gameDate: LocalDate): Flow<DiaryDay> = dietDao.observeDay(gameDate).map { rows ->
        val entries = rows.map { it.toEntry() }
        DiaryDay(gameDate, entries.groupBy { it.row.meal }, Nutrients.sum(entries.map { it.amounts }))
    }

    suspend fun log(gameDate: LocalDate, meal: Meal, food: Food, grams: Double, portionLabel: String?) {
        dietDao.upsertLog(
            FoodLogEntity(
                id = UUID.randomUUID().toString(),
                gameDate = gameDate,
                meal = meal,
                foodId = food.id,
                name = food.name,
                grams = grams.coerceIn(MIN_GRAMS, MAX_GRAMS),
                portionLabel = portionLabel,
                nutrientsJson = FoodCatalog.nutrientsToJson(food.per100),
                loggedAt = clock.now(),
            ),
        )
    }

    suspend fun updateLog(id: String, grams: Double, meal: Meal, portionLabel: String?) {
        val row = dietDao.logRow(id) ?: return
        dietDao.upsertLog(row.copy(grams = grams.coerceIn(MIN_GRAMS, MAX_GRAMS), meal = meal, portionLabel = portionLabel))
    }

    suspend fun deleteLog(id: String) = dietDao.deleteLog(id)

    /** The food behind a diary row, from its snapshot (the catalog entry may have changed since). */
    fun foodOf(row: FoodLogEntity): Food = Food(
        id = row.foodId,
        name = row.name,
        source = if (row.foodId.startsWith(CUSTOM_PREFIX)) FoodSource.CUSTOM else FoodSource.USDA_SR,
        per100 = FoodCatalog.nutrientsFromJson(row.nutrientsJson),
    )

    /** Portions for a logged food: from the catalog or custom food when it still exists. */
    suspend fun portionsOf(foodId: String): List<Portion> =
        catalog.byId(foodId)?.portions ?: dietDao.customFoods().firstOrNull { it.id == foodId }?.toFood()?.portions.orEmpty()

    // Search

    /** Blank query: recent foods. Otherwise the hunter's own foods that match, then the catalog. */
    suspend fun search(query: String): List<Food> {
        val custom = dietDao.customFoods().map { it.toFood() }
        if (query.isBlank()) {
            return dietDao.recentFoods(RECENT_LIMIT).map { row ->
                catalog.byId(row.foodId) ?: custom.firstOrNull { it.id == row.foodId } ?: foodOf(row)
            }
        }
        val mine = CatalogSearch.search(custom, custom.map { CatalogSearch.normalize(it.name) }, query, FoodCatalog.SEARCH_LIMIT)
        return mine + catalog.search(query)
    }

    suspend fun createCustomFood(name: String, per100: Nutrients, portions: List<Portion>): Food {
        val entity = CustomFoodEntity(
            id = "$CUSTOM_PREFIX${UUID.randomUUID()}",
            name = name.trim().take(MAX_NAME),
            nutrientsJson = FoodCatalog.nutrientsToJson(per100),
            portionsJson = FoodCatalog.portionsToJson(portions.filter { it.grams > 0.0 && it.label.isNotBlank() }),
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        dietDao.upsertCustomFood(entity)
        return entity.toFood()
    }

    suspend fun deleteCustomFood(id: String) = dietDao.deleteCustomFood(id)

    companion object {
        const val CUSTOM_PREFIX = "custom:"
        const val RECENT_LIMIT = 20
        // Input sanity (not game numbers).
        const val MIN_GRAMS = 0.1
        const val MAX_GRAMS = 5_000.0
        const val MAX_NAME = 80
        private const val EMPTY_JSON_OBJECT = "{}"

        // Same shape as a nutrient snapshot: {"kcal":2200,…}.
        fun overridesToJson(overrides: Map<Nutrient, Double>): String = FoodCatalog.nutrientsToJson(Nutrients(overrides))

        fun overridesFromJson(text: String): Map<Nutrient, Double> = FoodCatalog.nutrientsFromJson(text).values
    }
}
