package com.assismohanty.awaken.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** DESIGN_SYSTEM §4.7: every group is present, edits report events, dialogs guard the destructive import. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: SettingsUiState, events: MutableList<SettingsEvent> = mutableListOf(), onBack: () -> Unit = {}) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                SettingsScreen(state = state, onEvent = { events += it }, onBack = onBack, onExport = {}, onImport = {})
            }
        }
    }

    @Test
    fun showsEveryGroup() {
        show(previewSettingsState())
        listOf("PROFILE", "DAY", "TRAINING", "COMFORT", "YOUR DATA").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun savingTheNameNeedsAChange() {
        show(previewSettingsState())
        composeRule.onNodeWithText("Save name").performScrollTo().assertIsNotEnabled()

    }

    @Test
    fun aChangedNameCanBeSavedAndAShortOneShowsTheRule() {
        val events = mutableListOf<SettingsEvent>()
        show(previewSettingsState().copy(nameDraft = "Kael"), events)
        composeRule.onNodeWithText("Save name").performScrollTo().assertIsEnabled().performClick()
        assertThat(events).containsExactly(SettingsEvent.SaveName)
    }

    @Test
    fun aTooShortNameShowsTheRule() {
        show(previewSettingsState().copy(nameDraft = "Al", showNameError = true))
        composeRule.onNodeWithText("Use 3 to 20 characters.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun focusTogglesAndSavesOnlyWithTwoDifferentStats() {
        val events = mutableListOf<SettingsEvent>()
        show(previewSettingsState().copy(focusDraft = listOf(Stat.STR, Stat.WIL)), events)
        composeRule.onNodeWithText("Save focus").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Vitality").performScrollTo().performClick()
        assertThat(events).containsExactly(SettingsEvent.ToggleFocus(Stat.VIT))
    }

    @Test
    fun dayStartOffersMidnightToSixOnly() {
        val events = mutableListOf<SettingsEvent>()
        show(previewSettingsState(), events)
        composeRule.onNodeWithText("06:00").performScrollTo().performClick()
        assertThat(events).containsExactly(SettingsEvent.SetDayStart(6))
        composeRule.onNodeWithText("07:00").assertDoesNotExist()
    }

    @Test
    fun injuryTagsAppearOnlyWhileInjuryModeIsOn() {
        show(previewSettingsState())
        composeRule.onNodeWithText("Upper body").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun injuryTagsAreHiddenWhenTheModeIsOff() {
        show(previewSettingsState().copy(injuryMode = false))
        composeRule.onNodeWithText("Upper body").assertDoesNotExist()
    }

    @Test
    fun theSwitchesReportTheirNewValue() {
        val events = mutableListOf<SettingsEvent>()
        show(previewSettingsState(), events)
        composeRule.onNodeWithText("Haptics").performScrollTo().performClick()
        composeRule.onNodeWithText("Reduce motion").performScrollTo().performClick()
        assertThat(events).containsExactly(SettingsEvent.SetHaptics(false), SettingsEvent.SetReduceMotion(true)).inOrder()
    }

    @Test
    fun importAsksBeforeReplacingEverything() {
        val events = mutableListOf<SettingsEvent>()
        show(previewSettingsState().copy(importPending = true), events)
        composeRule.onNodeWithText("Importing replaces your hunter, ledger and every setting on this device. This can't be undone.").assertIsDisplayed()
        composeRule.onNodeWithText("Replace my data").performClick()
        assertThat(events).containsExactly(SettingsEvent.ConfirmImport)
    }

    @Test
    fun backReturns() {
        var backs = 0
        show(previewSettingsState(), onBack = { backs++ })
        composeRule.onNodeWithText("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }
}
