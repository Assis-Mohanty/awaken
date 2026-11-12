package com.assismohanty.awaken.feature.gym

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A routine saved with no name still has a title and a Start button that make sense (owner feedback, 2026-11-12). */
@RunWith(AndroidJUnit4::class)
class GymRoutineNameTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aRoutineWithNoNameIsCalledUntitledRoutine() {
        val home = previewHome()
        val blank = home.routines.first().copy(name = "")
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { GymScreen(previewGymState(home = home.copy(routines = listOf(blank) + home.routines.drop(1))), onEvent = {}) }
        }
        composeRule.onNodeWithText("Untitled routine").performScrollTo().assertExists()
    }
}
