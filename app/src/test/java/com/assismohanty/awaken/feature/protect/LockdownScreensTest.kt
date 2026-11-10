package com.assismohanty.awaken.feature.protect

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The Lockdown screens' buttons (GD §19.2): there is no end button, and the exit only reports presses. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockdownScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val exitDescription = "Emergency exit. Double tap and hold for 3 seconds."
    private val apps = listOf(LockdownAppChip("com.notes", "Notes"), LockdownAppChip("com.music", "Spotify"))

    @Test
    fun theSessionShowsTheTimeLeftAndEachAllowedApp() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LockdownSessionScreen(LockdownSessionUiState(18 * 60_000L + 42_000L, apps, LockdownExitUi.Idle), {}, {})
            }
        }
        composeRule.onNodeWithText("18:42 left").assertExists()
        composeRule.onNodeWithText("Notes").assertExists()
        composeRule.onNodeWithText("Spotify").assertExists()
    }

    @Test
    fun tappingAnAllowedAppOpensIt() {
        var opened: String? = null
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LockdownSessionScreen(LockdownSessionUiState(60_000L, apps, LockdownExitUi.Idle), {}, { opened = it })
            }
        }
        composeRule.onNodeWithText("Spotify").performScrollTo().performClick()
        assertThat(opened).isEqualTo("com.music")
    }

    @Test
    fun holdingTheExitReportsPressedThenReleased() {
        val events = mutableListOf<LockdownEvent>()
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LockdownSessionScreen(LockdownSessionUiState(60_000L, apps, LockdownExitUi.Idle), { events += it }, {})
            }
        }
        composeRule.onNodeWithContentDescription(exitDescription).performScrollTo().performTouchInput {
            down(center)
            up()
        }
        assertThat(events).containsExactly(LockdownEvent.ExitPressed(true), LockdownEvent.ExitPressed(false)).inOrder()
    }

    @Test
    fun theSessionHasNoEndButtonOnlyTheEmergencyExit() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LockdownSessionScreen(LockdownSessionUiState(60_000L, apps, LockdownExitUi.Idle), {}, {})
            }
        }
        composeRule.onNodeWithText("End").assertDoesNotExist()
        composeRule.onNodeWithText("Stop").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(exitDescription).assertExists()
    }

    @Test
    fun theBlockScreenOffersOnlyTheWayBackToTheSession() {
        var back = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LockdownBlockContent("Instagram", null, Duration.ofMinutes(18).plusSeconds(42), { back++ })
            }
        }
        composeRule.onNodeWithText("Lockdown · 18:42 left").assertExists()
        composeRule.onNodeWithText("Instagram isn't available right now.").assertExists()
        composeRule.onNodeWithText("Back to session").performClick()
        assertThat(back).isEqualTo(1)
    }

    @Test
    fun theResultIsDismissedWithDone() {
        var done = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LockdownResultScreen(LockdownResultUiState("1", completed = true, minutes = 30, reason = null), { done++ })
            }
        }
        composeRule.onNodeWithText("Lockdown complete").assertExists()
        composeRule.onNodeWithText("Done").performClick()
        assertThat(done).isEqualTo(1)
    }
}
