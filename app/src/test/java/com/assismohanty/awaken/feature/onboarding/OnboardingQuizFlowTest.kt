package com.assismohanty.awaken.feature.onboarding

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.onboarding.EquipmentChoice
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.IllustrationSet
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import com.assismohanty.awaken.engine.onboarding.Pace
import com.assismohanty.awaken.engine.onboarding.ScreenTimeBand
import com.assismohanty.awaken.testing.FakeOnboardingPermissions
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.Obstacle
import com.assismohanty.awaken.engine.onboarding.OnboardingStep
import com.assismohanty.awaken.engine.body.WeightGoal
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The quiz flow's questions and permission screens (steps 1 to 17), through the real screen and ViewModel (docs/onboarding/PLAN.md §4). */
@RunWith(AndroidJUnit4::class)
class OnboardingQuizFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private fun newViewModel(): OnboardingFlowViewModel {
        return OnboardingFlowViewModel(
            drafts = graph.onboardingDrafts,
            country = "IN",
            newId = { "hunter-1" },
            permissions = FakeOnboardingPermissions(),
            today = { graph.today() },
            previewPlan = { answers, grants -> graph.previewOnboarding(answers, grants) },
            completeOnboarding = { answers, grants, asked -> graph.completeOnboarding(answers, grants, asked) },
        )
    }

    private fun show(vm: OnboardingFlowViewModel) {
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            AwakenTheme(reduceMotion = true) { OnboardingFlowScreen(state = state, onEvent = vm::onEvent) }
        }
        composeRule.waitUntil(30_000) { vm.uiState.value.loaded }
        composeRule.waitForIdle()
    }

    private fun tap(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theWholeQuestionFlowCanBeWalkedAndEveryAnswerIsRecorded() {
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

        // Screen time: a card, then Continue.
        composeRule.onNodeWithText("How long are you on your phone each day?").assertExists()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
        tap("2 to 4 hours")
        tap("Continue")

        // Sleep: the wheels start on the usual window, so Continue takes it as it is.
        composeRule.onNodeWithText("When do you sleep?").assertExists()
        tap("Continue")

        // Fitness, equipment and injuries each need a pick.
        composeRule.onNodeWithText("What's your fitness level?").assertExists()
        tap("Beginner")
        tap("Continue")
        composeRule.onNodeWithText("What can you train with?").assertExists()
        tap("Dumbbells")
        tap("Continue")
        composeRule.onNodeWithText("Any injuries?").assertExists()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
        // The injury cards sit below the fold on a small screen: scroll to the one to tap.
        composeRule.onNodeWithText("Knee").performScrollTo().performClick()
        composeRule.waitForIdle()
        tap("Continue")

        // Training days: a day chip makes the reminder switch appear.
        composeRule.onNodeWithText("Which days do you train?").assertExists()
        composeRule.onAllNodesWithText("Remind me on training days").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Monday").performClick()
        // The test graph's clock decides which day is today.
        val todayName = graph.today().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        composeRule.onNodeWithContentDescription("$todayName, today").assertExists()
        tap("Remind me on training days")
        tap("Continue")

        // Pace: the chosen card opens its explanation.
        composeRule.onNodeWithText("What pace suits you?").assertExists()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
        tap("Balanced")
        composeRule.onNodeWithText("A mix of easier and tougher days.").assertExists()
        tap("Continue")

        // Permissions: each is explained first, and "Not now" always moves on.
        composeRule.onNodeWithText("Count your steps and sleep for you").assertExists()
        composeRule.onNodeWithText("Connect").assertExists()
        tap("Not now")
        composeRule.onNodeWithText("See your phone time").assertExists()
        composeRule.onNodeWithText("Grant usage access").assertExists()
        tap("Not now")
        composeRule.onNodeWithText("A nudge when it counts").assertExists()
        tap("Not now")

        // Building: the ring runs, then the plan appears once the quests are really worked out.
        composeRule.onNodeWithText("Building your plan").assertExists()
        composeRule.waitUntil(30_000) {
            // Room answers on its own thread; let the main looper take the answer.
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            vm.uiState.value.step == OnboardingStep.PLAN
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Your plan").assertExists()
        // The answers have become the plan: focus areas, goals and the first quests.
        composeRule.onNodeWithText("Vitality and Willpower").assertExists()
        composeRule.onNodeWithText("6,000 steps a day").assertExists()
        composeRule.onNodeWithText("Mon").assertExists()
        // Screen time was answered but usage access was put off, so the plan says what it needs.
        composeRule.onAllNodesWithText("Needs usage access to work.").assertCountEquals(1)

        val answers = vm.uiState.value.answers
        assertThat(answers.name).isEqualTo("Sam")
        assertThat(answers.illustrationSet).isEqualTo(IllustrationSet.NEUTRAL)
        assertThat(answers.age).isEqualTo(25)
        assertThat(answers.heightCm).isEqualTo(170.0)
        assertThat(answers.weightKg).isEqualTo(70.0)
        assertThat(answers.bodyStage).isEqualTo(3)
        assertThat(answers.improve).containsExactly(ImproveGoal.SLEEP, ImproveGoal.FOCUS).inOrder()
        assertThat(answers.obstacles).containsExactly(Obstacle.NONE)
        assertThat(answers.screenTime).isEqualTo(ScreenTimeBand.H2_TO_4)
        assertThat(answers.fitness).isEqualTo(FitnessLevel.BEGINNER)
        assertThat(answers.equipment).isEqualTo(EquipmentChoice.DUMBBELLS)
        assertThat(answers.injuries).containsExactly(InjuryRegion.KNEE)
        assertThat(answers.trainingDays).containsExactly(1)
        assertThat(answers.trainingReminder).isTrue()
        assertThat(answers.pace).isEqualTo(Pace.BALANCED)
        assertThat(answers.healthConnected).isFalse()

        // The quests the plan screen lists are the quests Today will hold.
        val previewed = (vm.uiState.value.build as PlanBuild.Ready).quests.map { it.title }
        assertThat(previewed).isNotEmpty()

        // Start saves everything.
        tap("Start")
        composeRule.waitUntil(30_000) { runBlocking { graph.db.hunterDao().getProfile() } != null }
        val profile = runBlocking { graph.db.hunterDao().getProfile() }!!
        assertThat(profile.name).isEqualTo("Sam")
        assertThat(profile.hunterId).isEqualTo("hunter-1")
        assertThat(profile.focusStats).containsExactly(Stat.VIT, Stat.WIL).inOrder()
        assertThat(profile.ownedEquipment).containsExactly("dumbbells")
        assertThat(profile.injuryMode).isTrue()
        assertThat(profile.injuryTags).containsExactly("legs", "impact")
        assertThat(profile.stepGoal).isEqualTo(6_000)
        assertThat(profile.reminderTime).isEqualTo("07:00")
        assertThat(profile.healthEnabled).isFalse()
        assertThat(profile.screenTimeFrom).isNull()
        assertThat(profile.trainingDays).isEqualTo(1)
        assertThat(profile.trainingReminder).isTrue()
        assertThat(profile.birthYear).isEqualTo(graph.today().year - 25)
        assertThat(profile.heightCm).isEqualTo(170.0)
        assertThat(profile.weightKg).isEqualTo(70.0)
        assertThat(profile.dietGoalHint).isEqualTo(WeightGoal.MAINTAIN)
        val gym = runBlocking { graph.db.gymDao().gymSettings() }
        assertThat(gym?.repMin).isEqualTo(10)
        assertThat(gym?.repMax).isEqualTo(15)
        // The Gym answers are kept so they can be edited inside Gym (GD §18.1).
        assertThat(gym?.fitnessLevel).isEqualTo("BEGINNER")
        assertThat(gym?.equipment).isEqualTo("dumbbells,bench")
        assertThat(gym?.injuries).isEqualTo("KNEE")
        assertThat(gym?.goal).isNull()
        // The first plan exists and is the one that was previewed.
        composeRule.waitUntil(30_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            runBlocking { graph.db.questDao().getForDate(graph.today()) }.isNotEmpty()
        }
        val real = runBlocking { graph.db.questDao().getForDate(graph.today()) }
        assertThat(real.map { it.title }).containsAtLeastElementsIn(previewed)
        // Answering paid nothing and the draft is gone.
        assertThat(runBlocking { graph.db.ledgerDao().all() }).isEmpty()
        composeRule.waitUntil(30_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            runBlocking { graph.onboardingDrafts.load() } == null
        }
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
