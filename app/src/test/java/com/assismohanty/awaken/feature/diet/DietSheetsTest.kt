package com.assismohanty.awaken.feature.diet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.MealPlanEntryEntity
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.CustomFoodResult
import com.assismohanty.awaken.engine.body.FastState
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.WeightUnit
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Diet sheets and panels, one interaction each: what the hunter sees and which event a tap sends. */
@RunWith(AndroidJUnit4::class)
class DietSheetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<DietEvent>()
    private val slots = previewDietState().slots
    private val today = LocalDate.of(2026, 10, 27)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { Column(Modifier.verticalScroll(rememberScrollState())) { content() } } }
    }

    @Test
    fun quickAddNeedsCaloriesBeforeItCanBeSaved() {
        show { PromptContent(Prompt.QuickAdd("LUNCH"), null) { events += it } }
        composeRule.onNodeWithText("Add").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Calories, in kcal").performScrollTo().performTextInput("250")
        composeRule.onNodeWithText("Add").assertIsEnabled().performClick()

        val add = events.single() as DietEvent.QuickAdd
        assertThat(add.slotId).isEqualTo("LUNCH")
        assertThat(add.nutrients[Nutrient.KCAL]).isEqualTo(250.0)
    }

    @Test
    fun renamingAMealStartsFromItsCurrentName() {
        show { PromptContent(Prompt.RenameMeal("DINNER", "Supper"), null) { events += it } }
        composeRule.onNodeWithText("Supper").assertExists()
        composeRule.onNodeWithText("Save").performClick()
        assertThat(events.single()).isEqualTo(DietEvent.RenameMeal("DINNER", "Supper"))
    }

    @Test
    fun anEmptyRecipeShowsItsReasonAndTheEditorSendsItsEvents() {
        show { RecipeEditorContent(RecipeForm(name = "Soup", error = com.assismohanty.awaken.core.domain.RecipeSaveResult.NeedsIngredients)) { events += it } }
        composeRule.onNodeWithText("Add the first ingredient.").assertExists()
        composeRule.onNodeWithText("Add at least one ingredient with an amount.").assertExists()

        composeRule.onNodeWithText("Add an ingredient").performScrollTo().performClick()
        composeRule.onNodeWithText("Save recipe").performScrollTo().performClick()
        assertThat(events).containsExactly(DietEvent.AddIngredient, DietEvent.SaveRecipe).inOrder()
    }

    @Test
    fun switchingAMealReminderOnUsesItsDefaultTime() {
        val sets = mutableListOf<Pair<String, Int?>>()
        show { RemindersContent(slots, emptyMap()) { id, minutes -> sets += id to minutes } }
        composeRule.onNodeWithContentDescription("Lunch reminder").performClick()
        assertThat(sets.single()).isEqualTo("LUNCH" to 13 * 60)
    }

    @Test
    fun theFifthReminderCannotBeSwitchedOn() {
        val full = slots.take(4).associate { it.id to 9 * 60 }
        show { RemindersContent(slots + com.assismohanty.awaken.core.domain.MealSlot("custom:1", "Late", 9, false), full) { _, _ -> } }
        composeRule.onNodeWithText("Four meals already have a reminder. Switch one off to add another.").assertExists()
        composeRule.onNodeWithContentDescription("Late reminder").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Lunch reminder").assertIsEnabled()
    }

    @Test
    fun weightInPoundsIsStoredInKilograms() {
        show { WeightContent(emptyList(), null, WeightUnit.LB, com.assismohanty.awaken.engine.body.HeightUnit.CM) { events += it } }
        composeRule.onNodeWithContentDescription("Weight").performScrollTo().performTextInput("176.37")
        composeRule.onNodeWithText("Log weight").performScrollTo().performClick()

        val log = events.single() as DietEvent.LogWeight
        assertThat(log.kg).isWithin(0.01).of(80.0)
    }

    @Test
    fun aMeasurementInInchesIsStoredInCentimetres() {
        show { WeightContent(emptyList(), null, WeightUnit.KG, com.assismohanty.awaken.engine.body.HeightUnit.FT_IN) { events += it } }
        composeRule.onNodeWithContentDescription("Waist").performScrollTo().performTextInput("31.5")
        composeRule.onNodeWithText("Log measurement").performScrollTo().performClick()

        val log = events.single() as DietEvent.LogMeasure
        assertThat(log.cm).isWithin(0.01).of(80.01)
    }

    @Test
    fun fastingStartsAtTheChosenPreset() {
        show { FastingContent(FastView()) { events += it } }
        composeRule.onNodeWithText("16 hours").performClick()
        composeRule.onNodeWithText("Start").performClick()
        assertThat(events.single()).isEqualTo(DietEvent.StartFast(16 * 60))
    }

    @Test
    fun aReachedFastKeepsCountingAndEndsOnRequest() {
        show { FastingContent(FastView(state = FastState.Reached(Duration.ofHours(13)))) { events += it } }
        composeRule.onNodeWithText("13 h 00 min so far. Your target is reached.").assertExists()
        composeRule.onNodeWithText("End fast").performClick()
        assertThat(events.single()).isEqualTo(DietEvent.EndFast)
    }

    @Test
    fun aNutrientWithPatchyDataSaysSoAndShowsNoBar() {
        val state = previewDietState()
        show { NutrientsContent(checkNotNull(state.plan), state.day!!.totals, mapOf(Nutrient.IRON to 0.5, Nutrient.CALCIUM to 1.0)) }
        composeRule.onNodeWithText("Partial data").assertExists()
        composeRule.onNodeWithText("Some foods you logged have no value for this, so the total is not the whole day.").assertExists()
    }

    @Test
    fun aPlannedFoodOpensForEditingAndALoggedOneCannotBeLoggedAgain() {
        val open = MealPlanEntryEntity("1", today, "LUNCH", "usda:1", "Dal tadka", 150.0, null, """{"kcal":120.0}""", logged = false)
        val done = MealPlanEntryEntity("2", today, "BREAKFAST", "usda:2", "Idli", 120.0, null, """{"kcal":128.0}""", logged = true)
        show { PlanContent(today, today, listOf(open, done), slots) { events += it } }

        composeRule.onNodeWithText("Logged").assertExists()
        composeRule.onNodeWithText("Dal tadka").performClick()
        composeRule.onNodeWithText("Log it").performClick()
        assertThat(events).containsExactly(DietEvent.EditPlanned(open), DietEvent.LogPlanned("1", today)).inOrder()
        assertThat(composeRule.onAllNodesWithText("Log it").fetchSemanticsNodes()).hasSize(1)
    }

    @Test
    fun aRefusedCustomFoodExplainsWhy() {
        show { FoodSearchContent(FoodSearch("LUNCH", showCustomForm = true, customError = CustomFoodResult.Reason.BARCODE), slots, emptyList()) { events += it } }
        composeRule.onNodeWithText("That barcode is not valid. Check the digits.").assertExists()
    }
}
