package com.assismohanty.awaken.feature.protect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.ShieldStatus
import com.assismohanty.awaken.engine.ExitCheck
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The Lockdown screens (GD §19.2): no end button, a slide to exit that needs a shield, and a slide to start. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockdownScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val slots = listOf(LockdownSlotUi("com.notes", "Notes", null), LockdownSlotUi(null, null, null), LockdownSlotUi(null, null, null))

    private fun session(exit: ExitCheck = ExitCheck.ALLOWED, shields: Int = 2) =
        LockdownSessionUiState(18 * 60_000L + 42_000L, "11:06 AM", slots, shields, exit)

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.slideAcross() = performTouchInput {
        swipe(start = Offset(40f, centerY), end = Offset(width - 1f, centerY), durationMillis = 400)
    }

    @Test
    fun theSessionShowsTheClockTheEndTimeAndTheShields() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSessionScreen(session(), {}, {}, {}) } }
        composeRule.onNodeWithContentDescription("18:42").assertExists()
        composeRule.onNodeWithContentDescription("42").assertExists()
        composeRule.onNodeWithContentDescription("11:06 AM").assertExists()
        composeRule.onNodeWithContentDescription("2 Lockdown shields").assertExists()
    }

    @Test
    fun anHourOrMoreReadsWithHours() {
        assertThat(lockdownClockText(3_600_000L + 5 * 60_000L + 9_000L)).isEqualTo("1:05:09")
        assertThat(lockdownClockText(59_000L)).isEqualTo("00:59")
        assertThat(lockdownClockText(0)).isEqualTo("00:00")
    }

    @Test
    fun tappingAnAllowedAppOpensItAndAnEmptySlotDoesNothing() {
        var opened = mutableListOf<String>()
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSessionScreen(session(), {}, { opened += it }, {}) } }
        composeRule.onNodeWithContentDescription("Open Notes").performClick()
        composeRule.onAllNodesWithContentDescription("Empty slot")[0].performClick()
        assertThat(opened).containsExactly("com.notes")
    }

    @Test
    fun theCallButtonOpensThePhone() {
        var calls = 0
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSessionScreen(session(), {}, {}, { calls++ }) } }
        composeRule.onNodeWithContentDescription("Phone").performClick()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun slidingTheExitAllTheWayLeavesWithAShield() {
        var exits = 0
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSessionScreen(session(), { exits++ }, {}, {}) } }
        composeRule.onNodeWithContentDescription("Slide to leave the Lockdown and use one shield").slideAcross()
        composeRule.waitForIdle()
        assertThat(exits).isEqualTo(1)
    }

    @Test
    fun withNoShieldTheSlideDoesNothing() {
        var exits = 0
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSessionScreen(session(ExitCheck.NO_SHIELD, shields = 0), { exits++ }, {}, {}) } }
        composeRule.onNodeWithContentDescription("Slide to leave the Lockdown and use one shield").slideAcross()
        composeRule.waitForIdle()
        assertThat(exits).isEqualTo(0)
    }

    @Test
    fun afterUsingOneTodayTheSlideDoesNothing() {
        var exits = 0
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSessionScreen(session(ExitCheck.USED_TODAY, shields = 3), { exits++ }, {}, {}) } }
        composeRule.onNodeWithContentDescription("Slide to leave the Lockdown and use one shield").slideAcross()
        composeRule.waitForIdle()
        assertThat(exits).isEqualTo(0)
    }

    @Test
    fun theSessionTellsWhyThereIsNoWayOut() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSessionScreen(session(ExitCheck.NO_SHIELD, 0), {}, {}, {}) } }
        composeRule.onNodeWithText("No shield. You can't leave until the time is up.").assertExists()
    }

    @Test
    fun theSessionHasNoEndButton() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSessionScreen(session(), {}, {}, {}) } }
        composeRule.onNodeWithText("End").assertDoesNotExist()
        composeRule.onNodeWithText("Stop").assertDoesNotExist()
        composeRule.onNodeWithText("Give up").assertDoesNotExist()
    }

    @Test
    fun slidingTheStartAllTheWayStartsAndIsOffWithoutPermissions() {
        val events = mutableListOf<LockdownSetupEvent>()
        val ready = LockdownSetupUiState(usageAccess = true, appearOnTop = true, shield = ShieldStatus(1, false))
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSetupScreen(ready, { events += it }, {}, {}, {}, {}) } }
        composeRule.onNodeWithContentDescription("Slide to start the Lockdown").performScrollTo().slideAcross()
        composeRule.waitForIdle()
        assertThat(events).contains(LockdownSetupEvent.Start)
    }

    @Test
    fun theStartSlideIsOffUntilBothPermissionsAreOn() {
        val events = mutableListOf<LockdownSetupEvent>()
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSetupScreen(LockdownSetupUiState(), { events += it }, {}, {}, {}, {}) } }
        composeRule.onNodeWithContentDescription("Slide to start the Lockdown").performScrollTo().slideAcross()
        composeRule.waitForIdle()
        assertThat(events).doesNotContain(LockdownSetupEvent.Start)
    }

    @Test
    fun theQuickChoicesSetTheTime() {
        val events = mutableListOf<LockdownSetupEvent>()
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSetupScreen(LockdownSetupUiState(), { events += it }, {}, {}, {}, {}) } }
        composeRule.onNodeWithText("1 hour").performScrollTo().performClick()
        composeRule.onNodeWithText("15 min").performScrollTo().performClick()
        assertThat(events).containsAtLeast(LockdownSetupEvent.SetMinutes(60), LockdownSetupEvent.SetMinutes(15))
    }

    @Test
    fun aPlusSlotOpensTheAppPicker() {
        val events = mutableListOf<LockdownSetupEvent>()
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownSetupScreen(LockdownSetupUiState(), { events += it }, {}, {}, {}, {}) } }
        composeRule.onAllNodesWithContentDescription("Add an app")[0].performScrollTo().performClick()
        assertThat(events).contains(LockdownSetupEvent.OpenSlot(0))
    }

    @Test
    fun theWarningSaysThereIsNoWayOutAndShowsTheShield() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LockdownSetupScreen(LockdownSetupUiState(shield = ShieldStatus(3, false)), {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("You can't leave early").performScrollTo().assertExists()
        composeRule.onNodeWithText("You have 3 Lockdown shields. Once a day, one lets you leave early.").performScrollTo().assertExists()
    }

    @Test
    fun theBlockScreenOffersOnlyTheWayBackToTheSession() {
        var back = 0
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownBlockContent("Instagram", null, Duration.ofMinutes(18).plusSeconds(42), { back++ }) } }
        composeRule.onNodeWithText("Lockdown · 18:42 left").assertExists()
        composeRule.onNodeWithText("Instagram isn't available right now.").assertExists()
        composeRule.onNodeWithText("Back to session").performClick()
        assertThat(back).isEqualTo(1)
    }

    @Test
    fun theResultIsDismissedWithDone() {
        var done = 0
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LockdownResultScreen(LockdownResultUiState("1", completed = true, minutes = 30, reason = null), { done++ }) } }
        composeRule.onNodeWithText("Lockdown complete").assertExists()
        composeRule.onNodeWithText("Done").performClick()
        assertThat(done).isEqualTo(1)
    }
}
