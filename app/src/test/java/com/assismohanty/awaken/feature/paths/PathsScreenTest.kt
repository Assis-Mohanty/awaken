package com.assismohanty.awaken.feature.paths

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.components.LadderNode
import com.assismohanty.awaken.core.designsystem.components.OracleUiState
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.CreateBlock
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.ProgressionSignal
import com.assismohanty.awaken.engine.path.SlotOutcome
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** DESIGN_SYSTEM §4.8: the Paths tab states, and the events its buttons send. */
@RunWith(AndroidJUnit4::class)
class PathsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun card(
        oracle: OracleUiState? = OracleUiState.CHARTED,
        canRetry: Boolean = false,
        advanceTo: String? = null,
        recalibration: ProgressionSignal? = null,
    ) = PathCardUi(
        id = "p1",
        goal = "Run a 10K",
        primaryStat = Stat.VIT,
        secondaryStat = null,
        status = PathStatus.ACTIVE,
        ladder = listOf(LadderNode("Base", "Build a base.", "Run 5K without stopping.")),
        currentIndex = 0,
        weekDone = 1,
        weekTotal = 2,
        quests = listOf(
            PathQuestLineUi("s1", "Easy 3K run", SlotOutcome.COMPLETED, "Mon", null, false),
            PathQuestLineUi("s2", "Intervals", SlotOutcome.PENDING, "Thu", null, false),
        ),
        oracle = oracle,
        canRetry = canRetry,
        retryLimitReached = false,
        recalibration = recalibration,
        advanceTo = advanceTo,
    )

    private fun show(state: PathsUiState, events: MutableList<PathsEvent> = mutableListOf()) {
        composeRule.setContent {
            AwakenTheme { PathsScreen(state = state, onEvent = { events += it }, onOpenPath = {}) }
        }
    }

    @Test
    fun emptyStateOffersToCreateAPath() {
        val events = mutableListOf<PathsEvent>()
        show(PathsUiState(loading = false), events)

        composeRule.onNodeWithText("Name what you're leveling toward. The System will chart the way.").assertExists()
        composeRule.onNodeWithText("Create path").performClick()

        assertThat(events).containsExactly(PathsEvent.Create)
    }

    @Test
    fun aPathWindowShowsItsGoalQuestsAndOracleState() {
        show(PathsUiState(loading = false, consentGiven = true, paths = listOf(card(recalibration = ProgressionSignal.UP))))

        composeRule.onNodeWithText("RUN A 10K").assertExists()
        composeRule.onNodeWithText("Easy 3K run").assertExists()
        composeRule.onNodeWithText("Cleared").assertExists()
        composeRule.onNodeWithText("Week charted").assertExists()
        composeRule.onNodeWithText("The System has raised the bar.").assertExists()
    }

    @Test
    fun aFailedWeekCanBeRetried() {
        val events = mutableListOf<PathsEvent>()
        show(PathsUiState(loading = false, consentGiven = true, paths = listOf(card(OracleUiState.FAILED, canRetry = true))), events)

        composeRule.onNodeWithText("Retry").performScrollTo().performClick()

        assertThat(events).containsExactly(PathsEvent.Retry("p1"))
    }

    @Test
    fun aMilestoneAdvanceOfferSendsAdvanceOrStay() {
        val events = mutableListOf<PathsEvent>()
        show(PathsUiState(loading = false, consentGiven = true, paths = listOf(card(advanceTo = "Speed"))), events)

        composeRule.onNodeWithText("The System judges you ready for: Speed").assertExists()
        composeRule.onNodeWithText("Advance").performScrollTo().performClick()
        composeRule.onNodeWithText("Stay").performScrollTo().performClick()

        assertThat(events).containsExactly(PathsEvent.Advance("p1"), PathsEvent.Stay("p1")).inOrder()
    }

    @Test
    fun atTheLimitCreateIsDisabledWithAReason() {
        val events = mutableListOf<PathsEvent>()
        show(PathsUiState(loading = false, consentGiven = true, createBlock = CreateBlock.LIMIT, paths = listOf(card())), events)

        composeRule.onNodeWithText("Three Paths are active. Pause or archive one to chart another.").performScrollTo().assertExists()
        composeRule.onNodeWithText("Create path").performScrollTo().performClick()

        assertThat(events).isEmpty()
    }

    @Test
    fun decliningConsentSendsDeclineAndNothingElse() {
        val events = mutableListOf<PathsEvent>()
        show(PathsUiState(loading = false, showConsent = true), events)

        composeRule.onNodeWithText("Not now").performClick()

        assertThat(events).containsExactly(PathsEvent.DeclineConsent)
    }
}
