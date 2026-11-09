package com.assismohanty.awaken.feature.gym

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** Start → add an exercise → log a set in the coached flow → finish, through the real tracker and Room (GD §18.2). */
@RunWith(AndroidJUnit4::class)
class GymScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aSetLoggedInTheCoachedFlowIsSavedWithTheWorkout() {
        val viewModel = GymViewModel(graph.gym, graph.clock, NoRestScheduler, SilentChimePlayer, graph.uiPrefs, graph.planner)
        val benchId = runBlocking { graph.gym.searchExercises("barbell bench press medium grip", setOf(Equipment.BARBELL), Muscle.CHEST).first().id }
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                val state by viewModel.uiState.collectAsState()
                GymScreen(state = state, onEvent = viewModel::onEvent)
            }
        }

        waitForText("Start workout")
        // The empty workout sits below the week, plans and routines.
        composeRule.onNodeWithText("Start workout").performScrollTo().performClick()
        waitForText("Add exercise")
        viewModel.onEvent(GymEvent.AddExercise(benchId))
        waitForText("Complete set")
        waitForText("Set 1 of 1")

        // One exercise at a time: the set's weight and reps are written field by field, then completed.
        val set = checkNotNull(viewModel.uiState.value.active).exercises.single().sets.single()
        viewModel.onEvent(GymEvent.SetWeight(set.id, 60.0))
        viewModel.onEvent(GymEvent.SetReps(set.id, 10))
        // waitForIdle lets the view model's pending work reach the screen before the state is read.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.waitForIdle()
            viewModel.uiState.value.active?.exercises?.single()?.sets?.single()?.reps == 10
        }
        composeRule.onNodeWithText("Complete set").performScrollTo().performClick()
        waitForText("Finish workout")
        composeRule.onNodeWithText("Finish workout").performScrollTo().performClick()

        waitForText("Session complete")
        val history = runBlocking { graph.gym.observeOverview().first().history }
        assertThat(history.single().sets).isEqualTo(1)
        assertThat(history.single().volumeKg).isEqualTo(600.0)
    }
}

internal object NoRestScheduler : com.assismohanty.awaken.core.work.RestScheduler {
    override fun sync(workout: com.assismohanty.awaken.core.data.db.entity.WorkoutEntity?) = Unit
}

internal object SilentChimePlayer : com.assismohanty.awaken.core.audio.ChimePlayer {
    override fun play(choice: com.assismohanty.awaken.core.audio.ChimeChoice) = Unit
}
