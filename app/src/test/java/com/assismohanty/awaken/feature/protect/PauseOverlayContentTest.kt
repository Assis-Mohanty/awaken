package com.assismohanty.awaken.feature.protect

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.PauseRules
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The pause screen's buttons and countdown (GD §19.1): Not now always works, and Continue waits for the countdown. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PauseOverlayContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun state(seconds: Int, requireTap: Boolean = false, askIntention: Boolean = false) =
        PauseOverlayUiState("Instagram", null, seconds, "What did you come here for?", 2, requireTap, askIntention)

    @Test
    fun notNowIsThereFromTheFirstSecond() {
        var notNow = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { PauseOverlayContent(state(seconds = 30), { notNow++ }, {}, {}) }
        }
        composeRule.onNodeWithText("Not now").performClick()
        assertThat(notNow).isEqualTo(1)
    }

    @Test
    fun theAppOpensByItselfWhenTheCountdownEnds() {
        var continued = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { PauseOverlayContent(state(seconds = 3), {}, { continued++ }, {}) }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        assertThat(continued).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(1_000)
        assertThat(continued).isEqualTo(1)
    }

    @Test
    fun withTapToContinueNothingOpensUntilTheButtonIsTapped() {
        var continued = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { PauseOverlayContent(state(seconds = 2, requireTap = true), {}, { continued++ }, {}) }
        }
        // Run the two-second countdown out; with tap-to-continue the app must still not open by itself.
        composeRule.mainClock.advanceTimeBy(3_000)
        assertThat(continued).isEqualTo(0)
        composeRule.onNodeWithText("Continue to Instagram").performScrollTo().assertIsEnabled().performClick()
        assertThat(continued).isEqualTo(1)
    }

    @Test
    fun theReasonChipsReportTheChoice() {
        var chosen: PauseRules.Intention? = null
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { PauseOverlayContent(state(seconds = 30, askIntention = true), {}, {}, { chosen = it }) }
        }
        composeRule.onNodeWithText("Take a break").performScrollTo().performClick()
        assertThat(chosen).isEqualTo(PauseRules.Intention.BREAK)
    }

    @Test
    fun theOpenCountIsShown() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { PauseOverlayContent(state(seconds = 30), {}, {}, {}) } }
        composeRule.onNodeWithText("Opened 2 times today").assertExists()
    }
}
