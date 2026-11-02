package com.assismohanty.awaken.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.components.LumiView
import com.assismohanty.awaken.core.designsystem.components.ReelBubble
import com.assismohanty.awaken.core.designsystem.components.lumiDrawable
import com.assismohanty.awaken.core.designsystem.components.lumiStatusRes
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.reelcounter.core.lumi.LumiState
import com.assismohanty.awaken.reelcounter.core.lumi.lumiStage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Lumi's spoken description, and that a stage change swaps the picture at once under reduced motion. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LumiViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun descriptionNamesLumiTheStatusAndTheCount() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LumiView(LumiState(4, 0.2f), 48.dp, reelsToday = 42) } }
        composeRule.onNodeWithContentDescription("Lumi, Getting tired, 42 reels today").assertExists()
    }

    @Test
    fun oneReelIsSingular() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { LumiView(LumiState.Rested, 48.dp, reelsToday = 1) } }
        composeRule.onNodeWithContentDescription("Lumi, Fully charged, 1 reel today").assertExists()
    }

    @Test
    fun theStatusFollowsTheStageWithNoDelayWhenMotionIsReduced() {
        var reels by mutableIntStateOf(0)
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { LumiView(lumiStage(reels, 50), 48.dp, reelsToday = reels) }
        }
        composeRule.onNodeWithContentDescription("Lumi, Fully charged, 0 reels today").assertExists()
        reels = 50
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Lumi, Switched off, 50 reels today").assertExists()
    }

    @Test
    fun theBubbleSpeaksThroughLumiAndDoesNotRepeatTheNumber() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { ReelBubble(count = 23, lumi = lumiStage(23, 50), over = false) } }
        composeRule.onNodeWithContentDescription("Lumi, Starting to fade, 23 reels today").assertExists()
    }

    @Test
    fun everyStageHasItsOwnPictureAndStatusLine() {
        val pictures = (1..6).map { lumiDrawable(it) }
        val statuses = (1..6).map { lumiStatusRes(it) }
        assertThat(pictures.toSet()).hasSize(6)
        assertThat(statuses.toSet()).hasSize(6)
    }
}
