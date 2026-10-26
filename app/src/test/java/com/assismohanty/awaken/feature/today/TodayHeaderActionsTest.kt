package com.assismohanty.awaken.feature.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Gates and Vault left the bottom bar for Gym and Diet; Today's top-right icons open them. */
@RunWith(AndroidJUnit4::class)
class TodayHeaderActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theTopRightIconsOpenGatesAndVault() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            AwakenTheme {
                TodayScreen(
                    state = TodayUiState(),
                    onEvent = {},
                    onOpenQuest = {},
                    onOpenTrial = {},
                    onHabitsEvent = {},
                    onDayPlanEvent = {},
                    onOpenGates = { opened += "gates" },
                    onOpenVault = { opened += "vault" },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open Gates").performClick()
        composeRule.onNodeWithContentDescription("Open Vault").performClick()

        assertThat(opened).containsExactly("gates", "vault").inOrder()
    }
}
