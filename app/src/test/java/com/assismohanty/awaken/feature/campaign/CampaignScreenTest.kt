package com.assismohanty.awaken.feature.campaign

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.CampaignProblem
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** DESIGN_SYSTEM §4.10: the campaign flow screen by screen. */
@RunWith(AndroidJUnit4::class)
class CampaignScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: CampaignUiState, events: MutableList<CampaignEvent> = mutableListOf()) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { CampaignScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }
    }

    @Test
    fun theIntroExplainsTheRulesAndBeginsTheScan() {
        val events = mutableListOf<CampaignEvent>()
        show(CampaignUiState(loading = false, step = CampaignStep.INTRO), events)
        composeRule.onNodeWithText("Clear 32 of the 45 days to finish it. Missed days never reset anything.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Begin the scan").performScrollTo().performClick()
        assertThat(events).containsExactly(CampaignEvent.Begin)
    }

    @Test
    fun aScanQuestionOffersFourAnswers() {
        val events = mutableListOf<CampaignEvent>()
        show(CampaignUiState(loading = false, step = CampaignStep.SCAN, question = 0), events)
        composeRule.onNodeWithText("1 / 10").assertIsDisplayed()
        composeRule.onNodeWithText("Often").performScrollTo().performClick()
        assertThat(events).containsExactly(CampaignEvent.Answer(2))
    }

    @Test
    fun theResultNamesTheArchetypeAndWeakestStat() {
        val events = mutableListOf<CampaignEvent>()
        show(CampaignUiState(loading = false, step = CampaignStep.RESULT, preview = previewCampaignPreview()), events)
        composeRule.onNodeWithText("The Rebuilder").assertIsDisplayed()
        composeRule.onNodeWithText("Strength · weakest. We start here.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Do push-ups").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Take the oath").performScrollTo().performClick()
        assertThat(events).containsExactly(CampaignEvent.ToOath)
    }

    @Test
    fun aProblemReplacesTheOathButton() {
        show(CampaignUiState(loading = false, step = CampaignStep.RESULT, preview = previewCampaignPreview(), error = CampaignProblem.NO_ROOM))
        composeRule.onNodeWithText("A campaign needs at least 3 free habit slots. Archive a habit and try again.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Take the oath").assertDoesNotExist()
    }

    @Test
    fun theOathCanBeSealedWithAnAccessibilityTap() {
        val events = mutableListOf<CampaignEvent>()
        show(CampaignUiState(loading = false, step = CampaignStep.OATH, preview = previewCampaignPreview(), hunterName = "Assis"), events)
        composeRule.onNodeWithText("I, Assis, commit to the 45-day campaign.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hold to seal the oath").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(CampaignEvent.Seal)
    }
}
