package com.assismohanty.awaken.feature.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.components.HealthTileModel
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTileModel
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.BedtimeToday
import com.assismohanty.awaken.core.domain.LastNight
import com.assismohanty.awaken.core.domain.TonightNight
import com.assismohanty.awaken.engine.BedtimeWindow
import com.assismohanty.awaken.engine.MissLine
import com.assismohanty.awaken.engine.model.TimeOfDay
import com.assismohanty.awaken.feature.focus.FocusCardState
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** DECLUTTER_PLAN §4: Today is a date title and at most five sections, each opening its detail. */
@RunWith(AndroidJUnit4::class)
class TodayScreenSectionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val summary = TodaySummaryUiState(
        screenTime = ScreenTimeTileModel.NotTracked,
        health = HealthTileModel.NoData,
    )
    private val window = BedtimeWindow(Instant.parse("2026-11-02T23:00:00Z"), Instant.parse("2026-11-03T06:30:00Z"))
    private val tonight = TonightNight(window, xpAtStake = 78, windDownFrom = window.start.minusSeconds(1_800))
    private val now = Instant.parse("2026-11-02T22:10:00Z")

    private fun show(
        state: TodayUiState = previewTodayState,
        summary: TodaySummaryUiState = this.summary,
        bedtime: BedtimeToday? = null,
        onOpenProgress: () -> Unit = {},
        onOpenGates: () -> Unit = {},
        onOpenQuit: () -> Unit = {},
        habits: MutableList<HabitsEvent> = mutableListOf(),
    ) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                TodayScreen(
                    state = state,
                    onEvent = {},
                    onOpenQuest = {},
                    onOpenTrial = {},
                    onHabitsEvent = { habits += it },
                    onDayPlanEvent = {},
                    onOpenProgress = onOpenProgress,
                    onOpenGates = onOpenGates,
                    onOpenQuit = onOpenQuit,
                    focus = FocusCardState(todayMinutes = 42, goal = 60, streak = 3),
                    summary = summary,
                    bedtime = bedtime,
                )
            }
        }
    }

    @Test
    fun theTitleIsTheDateAndThereIsNoGreeting() {
        show()
        composeRule.onNodeWithText("Tuesday 27 October").assertIsDisplayed()
        composeRule.onAllNodesWithText("Good", substring = true).assertCountEquals(0)
    }

    @Test
    fun theSectionsAppearInOrder() {
        show()
        val titles = listOf("Today's plan · 2 of 6 done", "Start a session", "Health and screen")
        val tree = composeRule.onRoot().printToString(Int.MAX_VALUE)
        val indexes = titles.map { tree.indexOf(it) }
        assertThat(indexes).doesNotContain(-1)
        assertThat(indexes).isInOrder()
    }

    @Test
    fun tappingTheHeaderOpensProgress() {
        var opened = 0
        show(onOpenProgress = { opened++ })
        composeRule.onNodeWithContentDescription("Level 24, rank C", substring = true).performClick()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun startASessionOpensFocus() {
        var opened = 0
        show(onOpenGates = { opened++ })
        composeRule.onNodeWithText("Start a session").performScrollTo().performClick()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun thePlanActionsReportEvents() {
        val habits = mutableListOf<HabitsEvent>()
        show(habits = habits)
        composeRule.onNodeWithText("Habits").performScrollTo().performClick()
        assertThat(habits).containsExactly(HabitsEvent.OpenList)
    }

    @Test
    fun quitCountersAreHiddenWhenThereAreNone() {
        show()
        composeRule.onAllNodesWithText("Quit counters").assertCountEquals(0)
    }

    @Test
    fun aQuitCounterShowsItsDaysAndOpensTheCounters() {
        var opened = 0
        show(summary = summary.copy(quit = listOf(QuitSummaryLine("q", "Smoking", 12))), onOpenQuit = { opened++ })
        composeRule.onNodeWithText("Smoking · 12 days clean").performScrollTo().performClick()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun theComebackRowShowsTheNeverMissTwiceLine() {
        show(previewTodayState.copy(missLine = MissLine.MISSED_YESTERDAY))
        composeRule.onNodeWithText("Comeback").assertIsDisplayed()
        composeRule.onNodeWithText("Missed yesterday. Don't miss twice.").assertIsDisplayed()
    }

    @Test
    fun noComebackRowOnAnOrdinaryDay() {
        show()
        composeRule.onAllNodesWithText("Comeback").assertCountEquals(0)
    }

    @Test
    fun theLimitsRowSitsInAnytime() {
        show()
        composeRule.onNodeWithText("App limits").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theEveningPhoneDownRowShowsTonightsWindow() {
        show(previewTodayState.copy(currentBlock = TimeOfDay.EVENING), bedtime = BedtimeToday(false, null, tonight, now))
        composeRule.onNodeWithText("Phone-down night").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bedtimeRowsLandInEveningThenMorning() {
        assertThat(bedtimeBlock(BedtimeToday(false, null, tonight, now), TimeOfDay.EVENING)).isEqualTo(TimeOfDay.EVENING)
        assertThat(bedtimeBlock(BedtimeToday(false, LastNight.Kept(78), tonight, now), TimeOfDay.DAY)).isEqualTo(TimeOfDay.MORNING)
        assertThat(bedtimeBlock(BedtimeToday(true, null, tonight, now), TimeOfDay.DAY)).isEqualTo(TimeOfDay.EVENING)
        assertThat(bedtimeBlock(BedtimeToday(false, null, null, now), TimeOfDay.DAY)).isNull()
    }
}
