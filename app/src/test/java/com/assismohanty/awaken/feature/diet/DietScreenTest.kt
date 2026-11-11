package com.assismohanty.awaken.feature.diet

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** First open → budget setup → search a food → log 100 g, through the real tracker and Room (GD §13.1). */
@RunWith(AndroidJUnit4::class)
class DietScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = 5_000) {
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun setupThenLogAFood() {
        val viewModel = DietViewModel(graph.diet, graph.foodSearch, graph.foodLibrary, graph.bodyLog)
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                val state by viewModel.uiState.collectAsState()
                DietScreen(state = state, onEvent = viewModel::onEvent)
            }
        }

        // GD §13 vector 46 on the test clock (2026-10-18): 25 y, 175 cm, 80 kg, moderate, maintain → 2,749 kcal.
        waitForText("Step 1 of 3")
        composeRule.onNodeWithText("Male").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Birth year").performScrollTo().performTextInput("2001")
        composeRule.onNodeWithContentDescription("Height").performScrollTo().performTextInput("175")
        composeRule.onNodeWithContentDescription("Weight").performScrollTo().performTextInput("80")
        composeRule.onNodeWithText("Moderate", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithText("Next").performScrollTo().performClick()

        waitForText("Step 2 of 3")
        composeRule.onNodeWithText("Maintain weight").performScrollTo().performClick()
        composeRule.onNodeWithText("Next").performScrollTo().performClick()

        waitForText("2,749 kcal / day")
        composeRule.onNodeWithText("Save budget").performScrollTo().performClick()

        waitForText("kcal left")
        composeRule.onNodeWithContentDescription("Add food to Breakfast").performScrollTo().performClick()
        waitForText("Add to Breakfast")
        composeRule.onNodeWithText("Search foods", substring = true).performTextInput("idli")
        waitForText("Idli")
        composeRule.onAllNodesWithText("Idli").onFirst().performClick()
        waitForText("Grams")
        composeRule.onNodeWithText("Add").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { graph.diet.observeDay(LocalDate.of(2026, 10, 18)).first().totals[Nutrient.KCAL] > 0.0 }
        }
        val idli = runBlocking { graph.diet.search("idli").first() }
        val day = runBlocking { graph.diet.observeDay(LocalDate.of(2026, 10, 18)).first() }
        assertThat(day.totals[Nutrient.KCAL]).isWithin(1e-9).of(idli.per100[Nutrient.KCAL])
    }
}
