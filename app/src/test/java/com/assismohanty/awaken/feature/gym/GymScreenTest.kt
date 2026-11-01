package com.assismohanty.awaken.feature.gym

import com.assismohanty.awaken.core.designsystem.lexicon.PresentationMode
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Start → add an exercise → log a set → finish, through the real tracker and Room (GD §13.2). */
@RunWith(AndroidJUnit4::class)
class GymScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = 5_000) {
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aLoggedSetIsSavedWithTheWorkout() {
        val viewModel = GymViewModel(graph.gym, graph.clock, NoRestScheduler, SilentChimePlayer, graph.uiPrefs)
        val benchId = runBlocking { graph.gym.searchExercises("barbell bench press medium grip", setOf(Equipment.BARBELL), Muscle.CHEST).first().id }
        composeRule.setContent {
            AwakenTheme(reduceMotion = true, presentation = PresentationMode.SYSTEM) {
                val state by viewModel.uiState.collectAsState()
                GymScreen(state = state, onEvent = viewModel::onEvent)
            }
        }

        waitForText("Start workout")
        composeRule.onNodeWithText("Start workout").performClick()
        waitForText("Add exercise")
        viewModel.onEvent(GymEvent.AddExercise(benchId))
        waitForText("Barbell Bench Press - Medium Grip")

        composeRule.onNodeWithContentDescription("Set 1 weight in kilograms").performTextReplacement("60")
        composeRule.onNodeWithContentDescription("Set 1 reps").performTextReplacement("10")
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Set 1 done").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { graph.gym.observeActive().first()?.exercises.orEmpty().flatMap { it.sets }.any { it.done } }
        }
        composeRule.onNodeWithContentDescription("Set 1 done").assertIsOn()
        composeRule.onNodeWithText("Finish").performScrollTo().performClick()

        waitForText("Session complete")
        val history = runBlocking { graph.gym.observeOverview().first().history }
        assertThat(history.single().sets).isEqualTo(1)
        assertThat(history.single().volumeKg).isEqualTo(600.0)
    }
}

private object NoRestScheduler : com.assismohanty.awaken.core.work.RestScheduler {
    override fun sync(workout: com.assismohanty.awaken.core.data.db.entity.WorkoutEntity?) = Unit
}

private object SilentChimePlayer : com.assismohanty.awaken.core.audio.ChimePlayer {
    override fun play(choice: com.assismohanty.awaken.core.audio.ChimeChoice) = Unit
}
