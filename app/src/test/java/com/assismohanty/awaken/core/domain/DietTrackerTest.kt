package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.db.entity.Meal
import com.assismohanty.awaken.engine.body.ActivityLevel
import com.assismohanty.awaken.engine.body.GoalSettings
import com.assismohanty.awaken.engine.body.LossPace
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.engine.body.Nutrients
import com.assismohanty.awaken.engine.body.Sex
import com.assismohanty.awaken.engine.body.WeightGoal
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The food diary: setup → engine plan, grams → totals, snapshots that outlive the food (GD §13.1). */
@RunWith(AndroidJUnit4::class)
class DietTrackerTest {
    private val graph = TestGraph()
    private val diet = graph.diet
    private val today = LocalDate.of(2026, 10, 18)

    // GD §13 vector 46: 25 y on the test clock's 2026-10-18.
    private val setup = DietSetup(Sex.MALE, birthYear = 2001, heightCm = 175.0, weightKg = 80.0, ActivityLevel.MODERATE, WeightGoal.MAINTAIN)

    @After
    fun tearDown() = graph.close()

    @Test
    fun setupProducesTheEnginePlanAndOverridesSurviveEdits() = runTest {
        assertThat(diet.observePlan().first()).isNull()

        diet.saveSetup(setup)
        assertThat(diet.observePlan().first()?.plan?.kcal).isEqualTo(2_749)

        diet.saveOverrides(mapOf(Nutrient.KCAL to 2_000.0))
        diet.saveSetup(setup.copy(weightKg = 82.0))

        val plan = checkNotNull(diet.observePlan().first())
        assertThat(plan.plan.kcal).isEqualTo(2_000)
        assertThat(plan.setup.weightKg).isEqualTo(82.0)
        assertThat(plan.overrides).containsExactly(Nutrient.KCAL, 2_000.0)
    }

    @Test
    fun paceChangesTheLossTargetAndAFastPaceIsCappedAtAQuarterOfMaintenance() = runTest {
        // Maintenance is 2,749 kcal (vector 46); a quarter of it rounds down to 687.
        diet.saveSetup(setup.copy(goal = WeightGoal.LOSE, settings = GoalSettings(lossPace = LossPace.GENTLE)))
        assertThat(diet.observePlan().first()?.plan?.kcal).isEqualTo(2_499)

        diet.saveSetup(setup.copy(goal = WeightGoal.LOSE, settings = GoalSettings(lossPace = LossPace.FASTER)))
        val plan = checkNotNull(diet.observePlan().first())
        assertThat(plan.plan.kcal).isEqualTo(2_062)
        assertThat(plan.goal?.capped).isTrue()
    }

    @Test
    fun theGuardHidesTargetsForAYoungOrUnderweightProfileButKeepsThePlan() = runTest {
        diet.saveSetup(setup)
        assertThat(diet.observePlan().first()?.targetsVisible).isTrue()

        diet.saveSetup(setup.copy(weightKg = 50.0)) // BMI 16.3
        assertThat(diet.observePlan().first()?.targetsVisible).isFalse()

        diet.saveSetup(setup.copy(birthYear = 2010))
        assertThat(diet.observePlan().first()?.targetsVisible).isFalse()
    }

    @Test
    fun anEstimatedFoodKeepsItsFlagAndCalorieRangeOnTheDiaryRow() = runTest {
        val dal = Food("cofid:dal", "Dal", FoodSource.AWAKEN, Nutrients(mapOf(Nutrient.KCAL to 120.0)), kcalRange = 100.0..150.0, estimated = true)
        diet.log(today, Meal.LUNCH.name, dal, grams = 200.0, portionLabel = null)

        val row = diet.observeDay(today).first().meals.getValue(Meal.LUNCH.name).single().row
        assertThat(row.estimated).isTrue()
        assertThat(row.kcalLowPer100).isEqualTo(100.0)
        assertThat(row.kcalHighPer100).isEqualTo(150.0)

        val rebuilt = diet.foodOf(row)
        assertThat(rebuilt.estimated).isTrue()
        assertThat(rebuilt.kcalRange).isEqualTo(100.0..150.0)
    }

    @Test
    fun loggingGramsAddsUpTheDay() = runTest {
        val paneer = diet.search("paneer").first { it.name == "Cheese, paneer" }
        val kcalPer100 = paneer.per100[Nutrient.KCAL]

        diet.log(today, Meal.LUNCH.name, paneer, grams = 150.0, portionLabel = null)
        val day = diet.observeDay(today).first()

        assertThat(day.meals.keys).containsExactly(Meal.LUNCH.name)
        assertThat(day.totals[Nutrient.KCAL]).isWithin(1e-9).of(kcalPer100 * 1.5)

        val entry = day.meals.getValue(Meal.LUNCH.name).single()
        diet.updateLog(entry.row.id, grams = 100.0, meal = Meal.DINNER.name, portionLabel = null)
        val edited = diet.observeDay(today).first()
        assertThat(edited.meals.keys).containsExactly(Meal.DINNER.name)
        assertThat(edited.totals[Nutrient.KCAL]).isWithin(1e-9).of(kcalPer100)

        diet.deleteLog(entry.row.id)
        assertThat(diet.observeDay(today).first().totals[Nutrient.KCAL]).isEqualTo(0.0)
        assertThat(graph.db.ledgerDao().all()).isEmpty()
    }

    @Test
    fun aBlankSearchListsRecentFoods() = runTest {
        val idli = diet.search("idli").first()
        diet.log(today, Meal.BREAKFAST.name, idli, grams = 76.0, portionLabel = "2 × 1 item")

        assertThat(diet.search("").map { it.id }).containsExactly(idli.id)
    }

    @Test
    fun customFoodsComeFirstAndTheDiaryKeepsTheirSnapshot() = runTest {
        val shake = diet.createCustomFood(
            "Paneer protein shake",
            Nutrients(mapOf(Nutrient.KCAL to 120.0, Nutrient.PROTEIN to 15.0)),
            listOf(Portion("1 glass", 300.0)),
        )

        assertThat(diet.search("paneer").first().id).isEqualTo(shake.id)
        assertThat(diet.portionsOf(shake.id)).containsExactly(Portion("1 glass", 300.0))

        diet.log(today, Meal.SNACK.name, shake, grams = 300.0, portionLabel = "1 glass")
        diet.deleteCustomFood(shake.id)

        assertThat(diet.observeDay(today).first().totals[Nutrient.PROTEIN]).isWithin(1e-9).of(45.0)
    }
}
