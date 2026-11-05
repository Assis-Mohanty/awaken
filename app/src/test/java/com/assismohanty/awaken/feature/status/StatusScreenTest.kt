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

/** DESIGN_SYSTEM §4.5: Progress sections and chart descriptions. */
@RunWith(AndroidJUnit4::class)
class StatusScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: StatusUiState, events: MutableList<StatusEvent> = mutableListOf()) {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { StatusScreen(state = state, onEvent = { events += it }) } }
    }

    @Test
    fun sectionsRender() {
        show(previewStatusState())
        composeRule.onNodeWithText("Assis").assertIsDisplayed()
        listOf("Stat balance", "Stats", "Activity", "Weekly report").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun radarExposesStatLevelsToTalkBack() {
        show(previewStatusState())
        composeRule.onNode(hasContentDescription("Strength level", substring = true)).assertExists()
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
