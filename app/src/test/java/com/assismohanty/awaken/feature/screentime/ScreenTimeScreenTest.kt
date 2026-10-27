package com.assismohanty.awaken.feature.screentime

import com.assismohanty.awaken.core.designsystem.lexicon.PresentationMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.DisciplineStatus
import com.assismohanty.awaken.engine.AppCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** DESIGN_SYSTEM §4.9: Screen Time shows usage, the stake and per-app rules; every edit is labelled "from tomorrow". */
@RunWith(AndroidJUnit4::class)
class ScreenTimeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: ScreenTimeUiState = previewScreenTimeState(),
        events: MutableList<ScreenTimeEvent> = mutableListOf(),
        onOpenUsageAccess: () -> Unit = {},
    ) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true, presentation = PresentationMode.SYSTEM) {
                ScreenTimeScreen(state = state, onEvent = { events += it }, onBack = {}, onOpenUsageAccess = onOpenUsageAccess)
            }
        }
    }

    @Test
    fun showsTodayTheStakeAndTheApps() {
        show()
        composeRule.onNodeWithText("TODAY").assertIsDisplayed()
        composeRule.onNodeWithText("Every limit holds. Keep it until the day ends to claim +78 XP.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Instagram: 26 of 30 minutes used").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("APPS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("From tomorrow: Distracting, 60 min").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAccessItAsksForUsageAccess() {
        var opened = 0
        val state = previewScreenTimeState()
        show(
            state = state.copy(overview = state.overview?.copy(hasAccess = false, status = DisciplineStatus.OFF, apps = emptyList())),
            onOpenUsageAccess = { opened++ },
        )
        composeRule.onNodeWithText("Allow usage access").performClick()
        assertThat(opened).isEqualTo(1)
        composeRule.onNodeWithText("APPS").assertDoesNotExist()
    }

    @Test
    fun turningDisciplineOnIsOneTapAndSaysItStartsTomorrow() {
        val events = mutableListOf<ScreenTimeEvent>()
        val state = previewScreenTimeState()
        show(state = state.copy(overview = state.overview?.copy(status = DisciplineStatus.OFF)), events = events)
        composeRule.onNodeWithText("Starts tomorrow", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Turn on Screen Discipline").performScrollTo().performClick()
        assertThat(events).containsExactly(ScreenTimeEvent.SetDiscipline(true))
    }

    @Test
    fun aForfeitedDaySaysNothingEarnedIsTaken() {
        val state = previewScreenTimeState()
        show(state = state.copy(overview = state.overview?.copy(status = DisciplineStatus.FORFEITED)))
        composeRule.onNodeWithText("A limit was crossed. Today's stake is forfeited. Your earned XP is untouched. Tomorrow is a new day.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingAnAppOpensItsRule() {
        val events = mutableListOf<ScreenTimeEvent>()
        show(events = events)
        composeRule.onNodeWithText("Docs").performScrollTo().performClick()
        assertThat(events).containsExactly(ScreenTimeEvent.OpenApp("com.google.android.apps.docs"))
    }

    @Test
    fun unusedAppsAreFoldedAway() {
        val events = mutableListOf<ScreenTimeEvent>()
        show(events = events)
        composeRule.onNodeWithText("Calculator").assertDoesNotExist()
        composeRule.onNodeWithText("Show 1 more app").performScrollTo().performClick()
        assertThat(events).containsExactly(ScreenTimeEvent.ToggleUnused)
    }

    @Test
    fun theEditorStepsTheLimitAndSaysItAppliesTomorrow() {
        val events = mutableListOf<ScreenTimeEvent>()
        composeRule.setContent {
            AwakenTheme(reduceMotion = true, presentation = PresentationMode.SYSTEM) {
                RuleEditorContent(AppRuleEditor("pkg", "Instagram", AppCategory.DISTRACTING, 30), onEvent = { events += it })
            }
        }
        composeRule.onNodeWithText("Applies from tomorrow, so today's limits stay fair.").assertIsDisplayed()
        composeRule.onNodeWithText("+5 min").performClick()
        composeRule.onNodeWithText("Productive").performClick()
        composeRule.onNodeWithText("Save").performClick()
        assertThat(events).containsExactly(
            ScreenTimeEvent.StepLimit(5),
            ScreenTimeEvent.EditCategory(AppCategory.PRODUCTIVE),
            ScreenTimeEvent.SaveEditor,
        ).inOrder()
    }

    @Test
    fun aNonDistractingAppHasNoLimitControls() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true, presentation = PresentationMode.SYSTEM) {
                RuleEditorContent(AppRuleEditor("pkg", "Docs", AppCategory.PRODUCTIVE, null), onEvent = {})
            }
        }
        composeRule.onNodeWithText("Daily limit").assertDoesNotExist()
    }
}
