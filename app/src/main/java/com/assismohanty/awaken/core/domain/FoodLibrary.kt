package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodCatalog
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.BodyLogDao
import com.assismohanty.awaken.core.data.db.dao.DietDao
import com.assismohanty.awaken.core.data.db.dao.FoodLibraryDao
import com.assismohanty.awaken.core.data.db.entity.CustomFoodEntity
import com.assismohanty.awaken.core.data.db.entity.EntrySource
import com.assismohanty.awaken.core.data.db.entity.FavouriteFoodEntity
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.Meal
import com.assismohanty.awaken.core.data.db.entity.MealSlotEntity
import com.assismohanty.awaken.core.data.db.entity.SavedMealEntity
import com.assismohanty.awaken.core.data.db.entity.SavedMealItemEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.body.Barcode
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** A meal of the day: the four built-in ones (a null [name] means the app's own word) and the hunter's own. */
data class MealSlot(val id: String, val name: String?, val position: Int, val builtIn: Boolean)

/** A custom food as the form gives it. [barcode] is whatever was typed or scanned; it is validated and stored as 13 digits. */
data class CustomFoodDraft(
    val id: String? = null,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val per100: Nutrients,
    val portions: List<Portion> = emptyList(),
    val servingGrams: Double? = null,
)

sealed interface CustomFoodResult {
    data class Saved(val food: Food) : CustomFoodResult
    data class Invalid(val reason: Reason) : CustomFoodResult
    enum class Reason { NAME, ENERGY, BARCODE }
}

data class SavedMealSummary(val id: String, val name: String, val items: Int, val kcal: Double)

/**
 * The hunter's own food library (Diet v2): meals, favourites, custom foods, saved meals, and copying what was eaten. Not a
 * ledger (ENGINE_DECISIONS #95, #186): nothing here pays XP or touches the ledger.
 */
@Singleton
class FoodLibrary @Inject constructor(
    private val dao: FoodLibraryDao,
    private val dietDao: DietDao,
    private val bodyLogDao: BodyLogDao,
    private val catalog: FoodCatalog,
    private val recipes: RecipeBook,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    // Meals

    /** Creates the four built-in slots when they are missing (a fresh install has none; a migrated one has all four). */
    suspend fun ensureBuiltInMeals() {
        val have = dao.mealSlots().map { it.id }.toSet()
        BUILT_IN_ORDER.forEachIndexed { position, meal ->
            if (meal.name !in have) dao.upsertMealSlot(MealSlotEntity(meal.name, null, position, builtIn = true))
        }
    }

    fun observeMealSlots(): Flow<List<MealSlot>> = flow {
        ensureBuiltInMeals()
        emitAll(dao.observeMealSlots().map { rows -> rows.map { MealSlot(it.id, it.name, it.position, it.builtIn) } })
    }

    /** Adds a meal of the hunter's own; null when the name is blank or there are already [MAX_MEALS]. */
    suspend fun addMeal(name: String): MealSlot? = transactions.run {
        ensureBuiltInMeals()
        val clean = name.trim().take(MAX_MEAL_NAME)
        val slots = dao.mealSlots()
        if (clean.isEmpty() || slots.size >= MAX_MEALS) return@run null
        val slot = MealSlotEntity("$CUSTOM_MEAL_PREFIX${UUID.randomUUID()}", clean, (slots.maxOfOrNull { it.position } ?: -1) + 1, builtIn = false)
        dao.upsertMealSlot(slot)
        MealSlot(slot.id, slot.name, slot.position, slot.builtIn)
    }

    suspend fun renameMeal(id: String, name: String) {
        val clean = name.trim().take(MAX_MEAL_NAME)
        if (clean.isEmpty()) return
        dao.mealSlots().firstOrNull { it.id == id }?.let { dao.upsertMealSlot(it.copy(name = clean)) }
    }

    /** Deletes one of the hunter's own meals; the built-in four stay. Its diary and plan rows move to Snack so none is lost. True when something was deleted. */
    suspend fun deleteMeal(id: String): Boolean = transactions.run {
        val deleted = dao.deleteCustomMealSlot(id) > 0
        if (deleted) {
            dietDao.moveMeal(id, Meal.SNACK.name)
            bodyLogDao.movePlannedMeal(id, Meal.SNACK.name)
        }
        deleted
    }

    // Favourites

    fun observeFavouriteIds(): Flow<Set<String>> = dao.observeFavouriteIds().map { it.toSet() }

    /** Flips the star and returns whether the food is now a favourite. */
    suspend fun toggleFavourite(foodId: String): Boolean = transactions.run {
        if (dao.addFavourite(FavouriteFoodEntity(foodId, clock.now())) == INSERT_IGNORED) {
            dao.removeFavourite(foodId)
            false
        } else {
            true
        }
    }

    // Foods

    /** Any food by id: catalog, the hunter's own, or a recipe. */
    suspend fun foodById(id: String): Food? = when {
        id.startsWith(RecipeBook.PREFIX) -> recipes.food(id)
        id.startsWith(DietTracker.CUSTOM_PREFIX) -> dietDao.customFoods().firstOrNull { it.id == id }?.toFood()
        else -> catalog.byId(id)
    }

    suspend fun saveCustomFood(draft: CustomFoodDraft): CustomFoodResult {
        val name = draft.name.trim().take(DietTracker.MAX_NAME)
        if (name.isEmpty()) return CustomFoodResult.Invalid(CustomFoodResult.Reason.NAME)
        if (draft.per100[Nutrient.KCAL] < 0.0 || Nutrient.KCAL !in draft.per100.values) return CustomFoodResult.Invalid(CustomFoodResult.Reason.ENERGY)
        val barcode = draft.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let { Barcode.canonical(it) ?: return CustomFoodResult.Invalid(CustomFoodResult.Reason.BARCODE) }
        val id = draft.id ?: "${DietTracker.CUSTOM_PREFIX}${UUID.randomUUID()}"
        val now = clock.now()
        val entity = CustomFoodEntity(
            id = id,
            name = name,
            nutrientsJson = FoodCatalog.nutrientsToJson(draft.per100),
            portionsJson = FoodCatalog.portionsToJson(draft.portions.filter { it.grams > 0.0 && it.label.isNotBlank() }),
            createdAt = dietDao.customFoods().firstOrNull { it.id == id }?.createdAt ?: now,
            brand = draft.brand?.trim()?.takeIf { it.isNotEmpty() },
            barcode = barcode,
            servingGrams = draft.servingGrams?.takeIf { it > 0.0 },
            updatedAt = now,
        )
        dietDao.upsertCustomFood(entity)
        return CustomFoodResult.Saved(entity.toFood())
    }

    /** The hunter's own food for a scanned code, or null. [upcE]: the scanner reported a UPC-E (see `Barcode.canonical`). */
    suspend fun customFoodByBarcode(raw: String, upcE: Boolean = false): Food? =
        Barcode.canonical(raw, upcE)?.let { dietDao.customFoodByBarcode(it)?.toFood() }

    // Saved meals

    fun observeSavedMeals(): Flow<List<SavedMealSummary>> = combine(dao.observeSavedMeals(), dao.observeAllSavedMealItems()) { meals, items ->
        val byMeal = items.groupBy { it.savedMealId }
        meals.map { meal ->
            val rows = byMeal[meal.id].orEmpty()
            SavedMealSummary(meal.id, meal.name, rows.size, rows.sumOf { FoodCatalog.nutrientsFromJson(it.nutrientsJson).forGrams(it.grams)[Nutrient.KCAL] })
        }
    }

    /** Saves [rows] (usually one meal of a day) under [name]; null when the name is blank or there is nothing to save. */
    suspend fun saveMeal(name: String, rows: List<FoodLogEntity>): String? {
        val clean = name.trim().take(MAX_MEAL_NAME)
        if (clean.isEmpty() || rows.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        val now = clock.now()
        transactions.run {
            dao.upsertSavedMeal(SavedMealEntity(id, clean, now, now))
            dao.upsertSavedMealItems(rows.mapIndexed { i, r -> SavedMealItemEntity("$id#$i", id, i, r.foodId, r.name, r.grams, r.portionLabel, r.nutrientsJson) })
        }
        return id
    }

    suspend fun deleteSavedMeal(id: String) = transactions.run {
        dao.deleteSavedMealItems(id)
        dao.deleteSavedMeal(id)
    }

    /** Adds every item of a saved meal to [meal] on [date]; returns how many rows were added. */
    suspend fun addSavedMealToDiary(id: String, date: LocalDate, meal: String): Int {
        val items = dao.savedMealItems(id)
        // A saved item keeps no Estimate tag; it is a property of the catalog food, so it is read back from there.
        val catalogFoods = catalog.byIds(items.map { it.foodId }.distinct()).associateBy { it.id }
        val now = clock.now()
        dietDao.upsertLogs(
            items.mapIndexed { i, it ->
                val food = catalogFoods[it.foodId]
                FoodLogEntity(
                    id = UUID.randomUUID().toString(), gameDate = date, meal = meal, foodId = it.foodId, name = it.name, grams = it.grams,
                    portionLabel = it.portionLabel, nutrientsJson = it.nutrientsJson, loggedAt = now.plusMillis(i.toLong()), entrySource = EntrySource.MEAL,
                    estimated = food?.estimated == true, kcalLowPer100 = food?.kcalRange?.start, kcalHighPer100 = food?.kcalRange?.endInclusive,
                )
            },
        )
        return items.size
    }

    // Copying what was eaten

    /** Copies the rows of [fromMeal] (all meals when null) on [from] to [to], into [toMeal] (their own meal when null). Returns the count. */
    suspend fun copyEntries(from: LocalDate, fromMeal: String?, to: LocalDate, toMeal: String? = fromMeal): Int {
        val rows = if (fromMeal == null) dietDao.entriesOn(from) else dietDao.entriesOn(from, fromMeal)
        val now = clock.now()
        dietDao.upsertLogs(rows.mapIndexed { i, r -> r.copy(id = UUID.randomUUID().toString(), gameDate = to, meal = toMeal ?: r.meal, loggedAt = now.plusMillis(i.toLong())) })
        return rows.size
    }

    companion object {
        const val MAX_MEALS = 8
        const val MAX_MEAL_NAME = 24
        const val CUSTOM_MEAL_PREFIX = "custom:"
        private const val INSERT_IGNORED = -1L

        /** Breakfast, lunch, snack, dinner: the order the diary shows them. */
        val BUILT_IN_ORDER = listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.SNACK, Meal.DINNER)
    }
}
