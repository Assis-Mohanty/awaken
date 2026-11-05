package com.assismohanty.awaken.feature.onboarding

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.repo.OnboardingDraftStore
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.onboarding.IllustrationSet
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.Obstacle
import com.assismohanty.awaken.engine.onboarding.OnboardingStep
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The quiz flow's first nine screens, through the real screen and ViewModel (docs/onboarding/PLAN.md §4). */
@RunWith(AndroidJUnit4::class)
class OnboardingQuizFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun newViewModel(): OnboardingFlowViewModel {
        val store = OnboardingDraftStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(Files.createTempDirectory("onboarding-quiz").toFile(), "draft.preferences_pb")
            },
        )
        return OnboardingFlowViewModel(store, "IN") { "hunter-1" }
    }

    private fun show(vm: OnboardingFlowViewModel) {
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            AwakenTheme(reduceMotion = true) { OnboardingFlowScreen(state = state, onEvent = vm::onEvent) }
        }
        composeRule.waitUntil(5_000) { vm.uiState.value.loaded }
        composeRule.waitForIdle()
    }

    private fun tap(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theFirstNineScreensCanBeWalkedAndEveryAnswerIsRecorded() {
        val vm = newViewModel()
        show(vm)

        composeRule.onNodeWithText("Let's set up your plan").assertExists()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithText("Skip").assertDoesNotExist()
        tap("Continue")

        // Name: Continue waits for a valid name and there is no Skip.
        composeRule.onNodeWithText("What should we call you?").assertExists()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
        composeRule.onNodeWithText("Skip").assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).performTextInput("Sam")
        composeRule.onNodeWithText("Continue").assertIsEnabled()
        tap("Continue")

        // Gender: pick one.
        composeRule.onNodeWithText("What's your gender?").assertExists()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
        tap("Prefer not to say")
        composeRule.onNodeWithText("Continue").assertIsEnabled()
        tap("Continue")

        // Age, height, weight and body shape take their starting values when passed untouched.
        composeRule.onNodeWithText("How old are you?").assertExists()
        tap("Continue")
        composeRule.onNodeWithText("How tall are you?").assertExists()
        tap("Continue")
        composeRule.onNodeWithText("What do you weigh?").assertExists()
        composeRule.onNodeWithText("Current BMI").assertExists()
        tap("Continue")
        composeRule.onNodeWithText("Which looks closest to you?").assertExists()
        composeRule.onNodeWithText("Stage 3 of 5").assertExists()
        tap("Continue")

        // Improve: at least one.
        composeRule.onNodeWithText("What do you want to improve?").assertExists()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
        tap("Sleep")
        tap("Focus")
        tap("Continue")

        // Obstacles: None stands alone.
        composeRule.onNodeWithText("What gets in your way?").assertExists()
        tap("Poor sleep")
        // The last card is below the fold on a small screen: scroll the grid to it.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("None of these"))
        tap("None of these")
        tap("Continue")

        composeRule.onNodeWithText("More questions are coming").assertExists()

        val answers = vm.uiState.value.answers
        assertThat(answers.name).isEqualTo("Sam")
        assertThat(answers.illustrationSet).isEqualTo(IllustrationSet.NEUTRAL)
        assertThat(answers.age).isEqualTo(25)
        assertThat(answers.heightCm).isEqualTo(170.0)
        assertThat(answers.weightKg).isEqualTo(70.0)
        assertThat(answers.bodyStage).isEqualTo(3)
        assertThat(answers.improve).containsExactly(ImproveGoal.SLEEP, ImproveGoal.FOCUS).inOrder()
        assertThat(answers.obstacles).containsExactly(Obstacle.NONE)
        assertThat(vm.uiState.value.step).isEqualTo(OnboardingStep.SCREEN_TIME)
    }

    @Test
    fun skipMovesOnWithoutAnAnswerAndBackComesBack() {
        val vm = newViewModel()
        show(vm)
        tap("Continue")
        composeRule.onNode(hasSetTextAction()).performTextInput("Sam")
        tap("Continue")
        composeRule.onNodeWithText("Skip").assertExists()
        tap("Skip")
        composeRule.onNodeWithText("How old are you?").assertExists()
        assertThat(vm.uiState.value.answers.illustrationSet).isNull()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What's your gender?").assertExists()
        composeRule.onAllNodesWithText("How old are you?").assertCountEquals(0)
    }

    @Test
    fun theBmiCardNeedsAHeight() {
        val vm = newViewModel()
        show(vm)
        tap("Continue")
        composeRule.onNode(hasSetTextAction()).performTextInput("Sam")
        tap("Continue")
        tap("Skip")
        tap("Continue")
        // Height skipped: the weight screen has no honest BMI to show.
        tap("Skip")
        composeRule.onNodeWithText("What do you weigh?").assertExists()
        composeRule.onAllNodesWithText("Current BMI").assertCountEquals(0)
    }
}
