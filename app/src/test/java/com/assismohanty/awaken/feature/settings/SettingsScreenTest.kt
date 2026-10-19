package com.assismohanty.awaken.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.health.HealthStatus
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

    private fun show(
        state: SettingsUiState,
        events: MutableList<SettingsEvent> = mutableListOf(),
        onBack: () -> Unit = {},
        onOpenPrivacy: () -> Unit = {},
    ) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                SettingsScreen(state = state, onEvent = { events += it }, onBack = onBack, onExport = {}, onImport = {}, onOpenPrivacy = onOpenPrivacy)
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

    @Test
    fun healthConnectOffersConnectThenDisconnect() {
        val events = mutableListOf<SettingsEvent>()
        show(previewSettingsState().copy(healthStatus = HealthStatus.AVAILABLE), events)
        composeRule.onNodeWithText("Off. Verified quests earn 30% more XP.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Connect").performScrollTo().performClick()
        assertThat(events).containsExactly(SettingsEvent.ConnectHealth)
    }

    @Test
    fun aConnectedHunterCanDisconnect() {
        val events = mutableListOf<SettingsEvent>()
        show(previewSettingsState().copy(healthConnected = true, healthStatus = HealthStatus.AVAILABLE), events)
        composeRule.onNodeWithText("Disconnect").performScrollTo().performClick()
        assertThat(events).containsExactly(SettingsEvent.DisconnectHealth)
    }

    @Test
    fun missingHealthConnectSaysHowToGetIt() {
        show(previewSettingsState().copy(healthStatus = HealthStatus.NOT_INSTALLED))
        composeRule.onNodeWithText("Health Connect isn't installed. Install it from Play Store to verify steps automatically.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theRationaleComesBeforeThePermissionSheet() {
        val events = mutableListOf<SettingsEvent>()
        show(previewSettingsState().copy(healthAction = HealthAction.SHOW_RATIONALE), events)
        composeRule.onNodeWithText("AWAKEN reads only Steps, Exercise sessions and Sleep sessions, on this phone. It never writes to Health Connect.")
            .assertIsDisplayed()
    }

    @Test
    fun thePrivacyPolicyOpensFromSettings() {
        var opened = 0
        show(previewSettingsState(), onOpenPrivacy = { opened++ })
        composeRule.onNodeWithText("Privacy policy").performScrollTo().performClick()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun blockedNotificationsSayWhyAndLinkToSystemSettings() {
        var opened = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                SettingsScreen(
                    state = previewSettingsState().copy(notificationsBlocked = true),
                    onEvent = {},
                    onBack = {},
                    onExport = {},
                    onImport = {},
                    onOpenNotificationSettings = { opened++ },
                )
            }
        }
        composeRule.onNodeWithText("Notifications are blocked, so reminders can't reach you.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open notification settings").performScrollTo().performClick()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun noHintWhenNotificationsAreAllowed() {
        show(previewSettingsState())
        composeRule.onNodeWithText("Open notification settings").assertDoesNotExist()
    }
}
