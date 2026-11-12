package com.assismohanty.awaken.feature.diet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renaming a meal is a small pencil beside its name, not a button of its own (owner feedback, 2026-11-12). */
@RunWith(AndroidJUnit4::class)
class DietTappableTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<DietEvent>()

    @Test
    fun theMealNameHasAPencilThatOpensTheRenamePromptAndNoRenameButton() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { DietScreen(previewDietState(), onEvent = { events += it }) } }
        composeRule.onAllNodesWithText("Rename meal").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Rename Lunch").performScrollTo().performClick()
        val open = events.single() as DietEvent.OpenPrompt
        assertThat((open.prompt as Prompt.RenameMeal).slotId).isEqualTo("LUNCH")
    }
}
