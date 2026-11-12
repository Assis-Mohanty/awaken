package com.assismohanty.awaken.feature.protect

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** With the phone's animations switched off (animator scale 0) the pause still lasts its full countdown (A6). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PauseOverlayCountdownTest {
    private val noAnimations = object : MotionDurationScale {
        override val scaleFactor: Float = 0f
    }

    @get:Rule
    val composeRule = createComposeRule(effectContext = noAnimations)

    @Test
    fun theCountdownIgnoresTheAnimationScale() {
        var continued = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AwakenTheme(reduceMotion = false) {
                PauseOverlayContent(PauseOverlayUiState("Instagram", null, 3, null, 1, requireTap = false, askIntention = false), {}, { continued++ }, {})
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        assertThat(continued).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(1_000)
        assertThat(continued).isEqualTo(1)
    }
}
