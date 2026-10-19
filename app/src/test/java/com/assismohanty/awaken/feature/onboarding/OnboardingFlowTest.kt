package com.assismohanty.awaken.feature.onboarding

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.CompletionEvent
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/** Onboarding happy path (ARCH §6): every Awakening step through to the first directive. */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private fun click(text: String, substring: Boolean = false) =
        composeRule.onNodeWithText(text, substring = substring).performScrollTo().performClick()

    @Test
    fun awakeningCreatesTheHunterAndTheFirstDirective() {
        val viewModel = OnboardingViewModel(graph.completeOnboarding, graph.completionCenter, graph.health)
        composeRule.setContent { AwakenTheme { OnboardingRoute(viewModel = viewModel) } }

        click("Accept")
        composeRule.onNode(hasSetTextAction()).performTextInput("Jinwoo")
        click("Continue")
        click("Strength", substring = true)
        click("Intelligence", substring = true)
        click("Continue")
        click("Sometimes")
        click("1–3")
        click("7h+")
        click("Continue")
        click("Not now")
        click("Continue")
        click("Enter")

        // Onboarding posts its unlocks once the hunter and first directive exist. The ViewModel resumes on the paused
        // Robolectric main looper, so pump it while polling.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            graph.completionCenter.events.value.isNotEmpty()
        }

        runBlocking {
            val profile = requireNotNull(graph.db.hunterDao().getProfile())
            assertThat(profile.name).isEqualTo("Jinwoo")
            assertThat(profile.focusStats).containsExactly(Stat.STR, Stat.INT).inOrder()
            assertThat(profile.reminderTime).isEqualTo("07:00")
            assertThat(graph.db.hunterDao().getTiers().associate { it.stat to it.tier }).containsExactly(
                Stat.STR, Difficulty.NORMAL,
                Stat.INT, Difficulty.NORMAL,
                Stat.WIL, Difficulty.EASY,
                Stat.VIT, Difficulty.HARD,
                Stat.CHA, Difficulty.EASY,
            )

            val quests = graph.db.questDao().getForDate(graph.today())
            assertThat(quests.count { it.type == QuestType.MAIN }).isEqualTo(1)
            assertThat(quests.count { it.type == QuestType.SIDE }).isEqualTo(3)
            assertThat(quests.any { it.type == QuestType.SIDE && it.difficulty == Difficulty.EASY && it.stat in profile.focusStats }).isTrue()
        }
        val unlocks = graph.completionCenter.events.value.filterIsInstance<CompletionEvent.Achievements>()
        assertThat(unlocks.flatMap { it.achievementIds }).contains("awakened")
    }

    @Test
    fun nameIsValidatedBeforeMovingOn() {
        val viewModel = OnboardingViewModel(graph.completeOnboarding, graph.completionCenter, graph.health)
        composeRule.setContent { AwakenTheme { OnboardingRoute(viewModel = viewModel) } }

        click("Accept")
        composeRule.onNode(hasSetTextAction()).performTextInput("Al")
        click("Continue")

        composeRule.onNodeWithText("Use 3 to 20 characters.").assertExists()
        assertThat(viewModel.uiState.value.step).isEqualTo(OnboardingStep.NAME)
    }
}
