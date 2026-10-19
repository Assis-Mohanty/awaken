package com.assismohanty.awaken.feature.status

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** DESIGN_SYSTEM §4.5: Status sections, locked achievements, equipping a title, chart descriptions. */
@RunWith(AndroidJUnit4::class)
class StatusScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: StatusUiState, events: MutableList<StatusEvent> = mutableListOf()) {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { StatusScreen(state = state, onEvent = { events += it }) } }
    }

    @Test
    fun sectionsRenderInOrderWithTheEquippedTitle() {
        show(previewStatusState())
        composeRule.onNodeWithText("Rising Hunter").assertIsDisplayed()
        listOf("STAT PENTAGON", "STATS", "ACTIVITY", "TITLES & ACHIEVEMENTS", "CHAPTERS", "WEEKLY REPORT").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun radarExposesStatLevelsToTalkBack() {
        show(previewStatusState())
        composeRule.onNode(hasContentDescription("Strength level", substring = true)).assertExists()
    }

    @Test
    fun lockedAchievementShowsItsCondition() {
        show(previewStatusState(locked = true))
        composeRule.onNodeWithText("Reach level 50.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingAnUnlockedTitleEquipsIt() {
        val events = mutableListOf<StatusEvent>()
        show(previewStatusState(locked = true), events)
        composeRule.onNodeWithText("Title: The Awakened").performScrollTo().performClick()
        assertThat(events).containsExactly(StatusEvent.EquipTitle("the_awakened"))
    }

    @Test
    fun tappingTheEquippedTitleUnequipsIt() {
        val events = mutableListOf<StatusEvent>()
        show(previewStatusState(), events)
        composeRule.onNodeWithText("Title: Rising Hunter").performScrollTo().performClick()
        assertThat(events).containsExactly(StatusEvent.EquipTitle(null))
    }

    @Test
    fun weeklyReportOffersShareWhenOpen() {
        val events = mutableListOf<StatusEvent>()
        show(previewStatusState(), events)
        composeRule.onNodeWithText("Share report").performScrollTo().performClick()
        assertThat(events).containsExactly(StatusEvent.Share)
    }

    @Test
    fun weeklyReportIsClosedBeforeSunday() {
        show(previewStatusState().copy(weeklyOpen = false))
        composeRule.onNodeWithText("The report opens on Sunday.").performScrollTo().assertIsDisplayed()
    }
}
