package com.assismohanty.awaken.feature

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.feature.onboarding.OnboardingStep
import com.assismohanty.awaken.feature.onboarding.OnboardingUiState
import com.assismohanty.awaken.feature.settings.SettingsScreen
import com.assismohanty.awaken.feature.settings.previewSettingsState
import com.assismohanty.awaken.feature.status.StatusScreen
import com.assismohanty.awaken.feature.status.previewStatusState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The release build has no AI Paths: its entry points are hidden, not just gated by consent. */
@RunWith(AndroidJUnit4::class)
class AiPathsHiddenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusHasNoPathsButtonWithoutAiPaths() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                StatusScreen(state = previewStatusState(), onEvent = {}, onOpenPaths = null)
            }
        }
        composeRule.onNode(hasContentDescription("Open paths")).assertDoesNotExist()
    }

    @Test
    fun settingsHasNoAiPathsWindowWithoutAiPaths() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                SettingsScreen(
                    state = previewSettingsState().copy(showAiConsent = true),
                    onEvent = {},
                    onBack = {},
                    onExport = {},
                    onImport = {},
                    showAiPaths = false,
                )
            }
        }
        composeRule.onNodeWithText("AI PATHS").assertDoesNotExist()
        composeRule.onNodeWithText("The System needs to consult the Oracle.").assertDoesNotExist()
    }

    @Test
    fun onboardingSkipsThePathStepWithoutAiPaths() {
        val off = OnboardingUiState(step = OnboardingStep.HEALTH, pathStepEnabled = false)
        assertThat(off.steps).doesNotContain(OnboardingStep.PATH)
        assertThat(off.steps).hasSize(OnboardingStep.entries.size - 1)
        assertThat(off.nextStep).isEqualTo(OnboardingStep.REMINDER)
        assertThat(off.copy(step = OnboardingStep.REMINDER).previousStep).isEqualTo(OnboardingStep.HEALTH)

        val on = OnboardingUiState(step = OnboardingStep.HEALTH)
        assertThat(on.nextStep).isEqualTo(OnboardingStep.PATH)
        assertThat(on.steps).hasSize(OnboardingStep.entries.size)
    }
}
