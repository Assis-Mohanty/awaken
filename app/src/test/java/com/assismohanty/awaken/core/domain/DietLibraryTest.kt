package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.db.entity.EntrySource
import com.assismohanty.awaken.core.data.db.entity.Meal
import com.assismohanty.awaken.engine.body.ActivityLevel
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import com.assismohanty.awaken.engine.body.Sex
import com.assismohanty.awaken.engine.body.WeightGoal
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Diet v2 repositories: ranked search, the food library, recipes and the body log (docs/diet/PLAN.md, Batch 3). */
@RunWith(AndroidJUnit4::class)
class DietLibraryTest {
    private val graph = TestGraph()
    private val search = graph.foodSearch
    private val library = graph.foodLibrary
    private val body = graph.bodyLog
    private val recipes = graph.recipeBook
    private val today = LocalDate.of(2026, 10, 18)

    @After
    fun tearDown() = graph.close()

    private fun food(id: String, name: String, kcal: Double = 100.0) =
        Food(id, name, FoodSource.USDA_SR, Nutrients(mapOf(Nutrient.KCAL to kcal)), listOf(Portion("1 piece", 50.0)))

    private suspend fun eat(food: Food, date: LocalDate = today, meal: String = Meal.LUNCH.name, grams: Double = 100.0) =
        graph.diet.log(date, meal, food, grams, null)

    // Search

    @Test
    fun searchRanksEverydayIndianFoodsFirstAndKeepsUsdaRows() = runTest {
        val names = search.search("paneer", today).map { it.name }

        assertThat(names.first()).isEqualTo("Paneer")
        assertThat(names).contains("Cheese, paneer")
        assertThat(search.search("dahl", today).first().name).isEqualTo("Dal tadka")
        assertThat(search.search("chappathi", today).first().name).isEqualTo("Chapati / roti")
    }

    @Test
    fun aFavouriteComesFirst() = runTest {
        val usda = search.search("paneer", today).first { it.name == "Cheese, paneer" }

        assertThat(library.toggleFavourite(usda.id)).isTrue()

        assertThat(search.search("paneer", today).first().id).isEqualTo(usda.id)
        assertThat(search.favourites().map { it.id }).containsExactly(usda.id)
        assertThat(library.toggleFavourite(usda.id)).isFalse()
        assertThat(search.search("paneer", today).first().name).isEqualTo("Paneer")
    }

    @Test
    fun whatYouLoggedInTheLastThirtyDaysComesBeforeTheRest() = runTest {
        val usda = search.search("paneer", today).first { it.name == "Cheese, paneer" }

        eat(usda, date = today.minusDays(40))
        assertThat(search.search("paneer", today).first().name).isEqualTo("Paneer")

        eat(usda, date = today.minusDays(3))
        assertThat(search.search("paneer", today).first().id).isEqualTo(usda.id)
    }

    @Test
    fun yourOwnFoodsAndRecipesAreSearchedWithTheCatalog() = runTest {
        val bar = library.saveCustomFood(CustomFoodDraft(name = "Paneer wrap", per100 = Nutrients(mapOf(Nutrient.KCAL to 220.0)))) as CustomFoodResult.Saved
        recipes.save(RecipeDraft(name = "Paneer bowl", servings = 2.0, ingredients = listOf(IngredientDraft("usda:1", "Paneer", 200.0, Nutrients(mapOf(Nutrient.KCAL to 300.0))))))

        assertThat(search.search("paneer wrap", today).first().id).isEqualTo(bar.food.id)
        val bowl = search.search("paneer bowl", today).first()
        assertThat(bowl.source).isEqualTo(FoodSource.RECIPE)
        assertThat(bowl.portions).containsExactly(Portion("1 serving", 100.0))
    }

    @Test
    fun aBlankSearchShowsRecentFoodsAndKeepsOneThatWasDeleted() = runTest {
        val mine = (library.saveCustomFood(CustomFoodDraft(name = "Gone soon", per100 = Nutrients(mapOf(Nutrient.KCAL to 90.0)))) as CustomFoodResult.Saved).food
        eat(food("usda:7", "Rice"))
        graph.clock.advance(Duration.ofMinutes(1))
        eat(mine)
        graph.diet.deleteCustomFood(mine.id)

        val recent = search.search("  ", today)

        assertThat(recent.map { it.name }).containsExactly("Gone soon", "Rice").inOrder()
        assertThat(recent.first().per100[Nutrient.KCAL]).isEqualTo(90.0)
    }

    // Library

    @Test
    fun theFourBuiltInMealsExistInOrderAndYouCanAddEightAtMost() = runTest {
        assertThat(library.observeMealSlots().first().map { it.id }).containsExactly("BREAKFAST", "LUNCH", "SNACK", "DINNER").inOrder()

        assertThat(library.addMeal("   ")).isNull()
        repeat(4) { assertThat(library.addMeal("Meal $it")).isNotNull() }
        assertThat(library.addMeal("One too many")).isNull()
        val slots = library.observeMealSlots().first()
        assertThat(slots).hasSize(8)
        assertThat(slots.map { it.position }).isInStrictOrder()
    }

    @Test
    fun removingAMealMovesItsRowsToSnackSoNothingIsLost() = runTest {
        library.ensureBuiltInMeals()
        val custom = checkNotNull(library.addMeal("Pre-workout"))
        eat(food("usda:9", "Banana", 90.0), meal = custom.id)

        assertThat(library.deleteMeal(custom.id)).isTrue()

        val rows = graph.db.dietDao().entriesOn(today)
        assertThat(rows.map { it.meal }).containsExactly(Meal.SNACK.name)
    }

    @Test
    fun builtInMealsCanBeRenamedButNotDeleted() = runTest {
        library.ensureBuiltInMeals()
        val custom = checkNotNull(library.addMeal("Pre-workout"))

        library.renameMeal("DINNER", "Supper")
        assertThat(library.deleteMeal("DINNER")).isFalse()
        assertThat(library.deleteMeal(custom.id)).isTrue()

        val slots = library.observeMealSlots().first()
        assertThat(slots.single { it.id == "DINNER" }.name).isEqualTo("Supper")
        assertThat(slots.map { it.id }).containsExactly("BREAKFAST", "LUNCH", "SNACK", "DINNER").inOrder()
    }

    @Test
    fun customFoodsKeepABarcodeInTheCanonicalForm() = runTest {
        val per100 = Nutrients(mapOf(Nutrient.KCAL to 380.0))
        val saved = library.saveCustomFood(CustomFoodDraft(name = " Protein bar ", brand = "Acme", barcode = "036000291452", per100 = per100, servingGrams = 60.0)) as CustomFoodResult.Saved

        assertThat(saved.food.name).isEqualTo("Protein bar")
        assertThat(library.customFoodByBarcode("0036000291452")?.id).isEqualTo(saved.food.id)
        assertThat(library.customFoodByBarcode("036000291452")?.id).isEqualTo(saved.food.id)
        assertThat(library.customFoodByBarcode("036000291453")).isNull()
        assertThat(library.saveCustomFood(CustomFoodDraft(name = "Bad code", barcode = "123", per100 = per100))).isEqualTo(CustomFoodResult.Invalid(CustomFoodResult.Reason.BARCODE))
        assertThat(library.saveCustomFood(CustomFoodDraft(name = " ", per100 = per100))).isEqualTo(CustomFoodResult.Invalid(CustomFoodResult.Reason.NAME))
        assertThat(library.saveCustomFood(CustomFoodDraft(name = "No energy", per100 = Nutrients()))).isEqualTo(CustomFoodResult.Invalid(CustomFoodResult.Reason.ENERGY))
    }

    @Test
    fun editingACustomFoodKeepsItsIdAndCreationTime() = runTest {
        val per100 = Nutrients(mapOf(Nutrient.KCAL to 100.0))
        val first = (library.saveCustomFood(CustomFoodDraft(name = "Soup", per100 = per100)) as CustomFoodResult.Saved).food
        graph.clock.advance(Duration.ofHours(1))

        library.saveCustomFood(CustomFoodDraft(id = first.id, name = "Soup v2", per100 = per100))

        val row = graph.db.dietDao().customFoods().single()
        assertThat(row.name).isEqualTo("Soup v2")
        assertThat(row.updatedAt.toEpochMilli() - row.createdAt.toEpochMilli()).isEqualTo(Duration.ofHours(1).toMillis())
    }

    @Test
    fun aSavedMealIsReAddedInOneStepAndCopiesKeepTheNumbers() = runTest {
        eat(food("usda:1", "Egg", 155.0), grams = 100.0, meal = Meal.BREAKFAST.name)
        eat(food("usda:2", "Toast", 265.0), grams = 60.0, meal = Meal.BREAKFAST.name)
        val rows = graph.db.dietDao().entriesOn(today, Meal.BREAKFAST.name)

        val id = checkNotNull(library.saveMeal("Usual breakfast", rows))
        val tomorrow = today.plusDays(1)
        assertThat(library.addSavedMealToDiary(id, tomorrow, Meal.BREAKFAST.name)).isEqualTo(2)

        val added = graph.db.dietDao().entriesOn(tomorrow)
        assertThat(added.map { it.name }).containsExactly("Egg", "Toast").inOrder()
        assertThat(added.map { it.entrySource }.toSet()).containsExactly(EntrySource.MEAL)
        assertThat(added.map { it.grams }).containsExactly(100.0, 60.0).inOrder()
        assertThat(library.observeSavedMeals().first().single().items).isEqualTo(2)

        val copied = library.copyEntries(today, null, today.plusDays(2), Meal.SNACK.name)
        assertThat(copied).isEqualTo(2)
        assertThat(graph.db.dietDao().entriesOn(today.plusDays(2), Meal.SNACK.name).map { it.grams }).containsExactly(100.0, 60.0).inOrder()
        assertThat(graph.db.dietDao().entriesOn(today).map { it.id }.toSet()).hasSize(2) // the source day is untouched

        library.deleteSavedMeal(id)
        assertThat(library.observeSavedMeals().first()).isEmpty()
        assertThat(library.saveMeal("Nothing", emptyList())).isNull()
    }

    // Recipes

    @Test
    fun aRecipeIsCountedByTheEngineAndEditsReplaceItsIngredients() = runTest {
        val oil = IngredientDraft("usda:oil", "Oil", 50.0, Nutrients(mapOf(Nutrient.KCAL to 900.0)))
        val base = IngredientDraft("usda:base", "Base", 200.0, Nutrients(mapOf(Nutrient.KCAL to 130.0)))
        val saved = recipes.save(RecipeDraft(name = "Stew", servings = 4.0, cookedGrams = 500.0, ingredients = listOf(base, oil))) as RecipeSaveResult.Saved

        val detail = checkNotNull(recipes.detail(saved.id))
        assertThat(detail.perServing[Nutrient.KCAL]).isWithin(1e-9).of(177.5)
        assertThat(detail.per100[Nutrient.KCAL]).isWithin(1e-9).of(142.0)
        assertThat(recipes.food(saved.id)?.portions).containsExactly(Portion("1 serving", 125.0))

        graph.clock.advance(Duration.ofMinutes(5))
        recipes.save(RecipeDraft(id = saved.id, name = "Stew", servings = 4.0, cookedGrams = 500.0, ingredients = listOf(base)))
        assertThat(checkNotNull(recipes.detail(saved.id)).ingredientNames).containsExactly("Base")
        assertThat(graph.db.foodLibraryDao().recipes().single().createdAt).isNotEqualTo(graph.db.foodLibraryDao().recipes().single().updatedAt)

        recipes.delete(saved.id)
        assertThat(recipes.detail(saved.id)).isNull()
        assertThat(graph.db.foodLibraryDao().recipeIngredients(saved.id)).isEmpty()
    }

    @Test
    fun badRecipesAreRefused() = runTest {
        val i = IngredientDraft("usda:1", "X", 10.0, Nutrients(mapOf(Nutrient.KCAL to 10.0)))

        assertThat(recipes.save(RecipeDraft(name = " ", servings = 1.0, ingredients = listOf(i)))).isEqualTo(RecipeSaveResult.NeedsName)
        assertThat(recipes.save(RecipeDraft(name = "A", servings = 1.0, ingredients = emptyList()))).isEqualTo(RecipeSaveResult.NeedsIngredients)
        assertThat(recipes.save(RecipeDraft(name = "A", servings = 1.0, ingredients = listOf(i.copy(grams = 0.0))))).isEqualTo(RecipeSaveResult.NeedsIngredients)
        assertThat(recipes.save(RecipeDraft(name = "A", servings = 0.0, ingredients = listOf(i)))).isEqualTo(RecipeSaveResult.BadServings)
        assertThat(recipes.save(RecipeDraft(name = "A", servings = 1.0, cookedGrams = -1.0, ingredients = listOf(i)))).isEqualTo(RecipeSaveResult.BadServings)
    }

    // Body log

    private val setup = DietSetup(Sex.MALE, birthYear = 2001, heightCm = 175.0, weightKg = 80.0, ActivityLevel.MODERATE, WeightGoal.LOSE)

    @Test
    fun theNewestWeighInIsTheProfileWeightButNotThePlanWeight() = runTest {
        graph.seedHunter()
        graph.diet.saveSetup(setup)
        graph.db.dietDao().profile()?.let { graph.db.dietDao().upsertProfile(it.copy(planWeightKg = 80.0)) }

        val first = checkNotNull(body.logWeight(today.minusDays(1), 79.0))
        val second = checkNotNull(body.logWeight(today, 78.5, " morning "))

        assertThat(graph.db.hunterDao().getProfile()?.weightKg).isEqualTo(78.5)
        val profile = checkNotNull(graph.db.dietDao().profile())
        assertThat(profile.weightKg).isEqualTo(78.5)
        assertThat(profile.planWeightKg).isEqualTo(80.0)
        assertThat(graph.db.bodyLogDao().latestWeight()?.note).isEqualTo("morning")

        body.deleteWeight(second)
        assertThat(graph.db.dietDao().profile()?.weightKg).isEqualTo(79.0)
        assertThat(first).isNotEmpty()
        assertThat(body.logWeight(today, 20.0)).isNull()
        assertThat(body.logWeight(today, 400.0)).isNull()
    }

    @Test
    fun theWeekCountsEntriesPerDayAndTheWaterTargetIsKeptInRange() = runTest {
        graph.seedHunter()
        graph.diet.saveSetup(setup)
        eat(food("usda:1", "Egg", 155.0), date = today, grams = 100.0)
        eat(food("usda:2", "Toast", 265.0), date = today.minusDays(2), grams = 100.0)

        val week = graph.diet.week(today)
        assertThat(week).hasSize(7)
        assertThat(week.last().date).isEqualTo(today)
        assertThat(week.map { it.entries }).containsExactly(0, 0, 0, 0, 1, 0, 1).inOrder()
        assertThat(week.last().kcal).isWithin(1e-9).of(155.0)

        body.setWaterTarget(100)
        assertThat(graph.db.dietDao().profile()?.waterTargetMl).isEqualTo(500)
        body.setWaterTarget(null)
        assertThat(graph.db.dietDao().profile()?.waterTargetMl).isNull()
    }

    @Test
    fun onlyPresetFastsStartAndOnlyOneRunsAtATime() = runTest {
        assertThat(body.startFast(18 * 60)).isNull()
        val first = checkNotNull(body.startFast(16 * 60))
        assertThat(checkNotNull(body.startFast(12 * 60)).id).isEqualTo(first.id)
        body.endFast()
        assertThat(body.observeActiveFast().first()).isNull()
    }

    @Test
    fun mealRemindersAreOffUntilSetAndStopAtFour() = runTest {
        val store = graph.mealReminders
        assertThat(store.reminders.first()).isEmpty()

        assertThat(store.set("BREAKFAST", 8 * 60)).isTrue()
        assertThat(store.set("LUNCH", 13 * 60)).isTrue()
        assertThat(store.set("SNACK", 16 * 60)).isTrue()
        assertThat(store.set("DINNER", 20 * 60)).isTrue()
        assertThat(store.set("custom:1", 9 * 60)).isFalse()
        assertThat(store.set("LUNCH", 12 * 60 + 30)).isTrue()
        assertThat(store.set("LUNCH", 24 * 60)).isFalse()
        assertThat(store.reminders.first()).containsEntry("LUNCH", 12 * 60 + 30)

        assertThat(store.set("BREAKFAST", null)).isTrue()
        assertThat(store.reminders.first().keys).containsExactly("LUNCH", "SNACK", "DINNER")
    }

    @Test
    fun theTrendOffersNewTargetsAtOneKiloAndStaysQuietOtherwise() = runTest {
        graph.seedHunter()
        graph.diet.saveSetup(setup)
        graph.db.dietDao().profile()?.let { graph.db.dietDao().upsertProfile(it.copy(planWeightKg = 80.0)) }
        body.logWeight(today.minusDays(2), 79.2)
        body.logWeight(today.minusDays(1), 79.0)
        assertThat(body.trendOffer(today)).isNull() // only two weigh-ins

        body.logWeight(today, 78.8)
        val offer = checkNotNull(body.trendOffer(today))
        assertThat(offer.trendKg).isWithin(1e-9).of(79.0)
        assertThat(offer.planWeightKg).isEqualTo(80.0)

        body.dismissTrend(today.minusDays(6)) // hidden until the 7th day after: 1 day from now
        assertThat(body.trendOffer(today)).isNull()
        body.dismissTrend(today.minusDays(7)) // seven days ago: the offer is back
        assertThat(body.trendOffer(today)).isNotNull()

        body.acceptTrend(offer)
        assertThat(graph.db.dietDao().profile()?.planWeightKg).isWithin(1e-9).of(79.0)
        assertThat(body.trendOffer(today)).isNull()
    }

    @Test
    fun noOfferWhileTargetsAreHidden() = runTest {
        graph.seedHunter()
        graph.diet.saveSetup(setup.copy(birthYear = 2010)) // 16 on the test clock
        graph.db.dietDao().profile()?.let { graph.db.dietDao().upsertProfile(it.copy(planWeightKg = 80.0)) }
        listOf(0L, 1L, 2L).forEach { body.logWeight(today.minusDays(it), 77.0) }

        assertThat(body.trendOffer(today)).isNull()
    }

    @Test
    fun waterNotesFastingAndMeasurementsKeepTheirLimits() = runTest {
        assertThat(body.addWater(today, 0)).isNull()
        assertThat(body.addWater(today, 5_001)).isNull()
        val glass = checkNotNull(body.addWater(today, 250))
        body.addWater(today, 500)
        assertThat(body.observeWaterMl(today).first()).isEqualTo(750)
        body.removeWater(glass)
        assertThat(body.observeWaterMl(today).first()).isEqualTo(500)

        body.setNote(today, "  Felt good  ")
        assertThat(body.observeNote(today).first()?.text).isEqualTo("Felt good")
        body.setNote(today, "   ")
        assertThat(body.observeNote(today).first()).isNull()

        assertThat(body.startFast(24 * 60)).isNull()
        val fast = checkNotNull(body.startFast(16 * 60))
        assertThat(body.startFast(12 * 60)?.id).isEqualTo(fast.id)
        body.endFast()
        assertThat(body.observeActiveFast().first()).isNull()

        assertThat(body.logMeasure(today, com.assismohanty.awaken.core.data.db.entity.MeasureKind.WAIST, 5.0)).isNull()
        assertThat(body.logMeasure(today, com.assismohanty.awaken.core.data.db.entity.MeasureKind.WAIST, 82.0)).isNotNull()
    }

    @Test
    fun aPlannedFoodIsLoggedWhenItsTimeComes() = runTest {
        val dal = food("usda:5", "Dal", 116.0)

        assertThat(body.plan(today, today.minusDays(1), Meal.LUNCH.name, dal, 150.0, null)).isNull()
        assertThat(body.plan(today, today.plusDays(15), Meal.LUNCH.name, dal, 150.0, null)).isNull()
        assertThat(body.plan(today, today.plusDays(1), Meal.LUNCH.name, dal, 0.0, null)).isNull()
        val id = checkNotNull(body.plan(today, today.plusDays(1), Meal.DINNER.name, dal, 150.0, "1 katori"))

        assertThat(body.logPlanned(id, today.plusDays(1))).isTrue()

        val row = graph.db.dietDao().entriesOn(today.plusDays(1)).single()
        assertThat(row.meal).isEqualTo(Meal.DINNER.name)
        assertThat(row.entrySource).isEqualTo(EntrySource.PLAN)
        assertThat(row.portionLabel).isEqualTo("1 katori")
        assertThat(body.observePlan(today.plusDays(1)).first().single().logged).isTrue()
        assertThat(body.logPlanned("missing", today)).isFalse()
    }
}
