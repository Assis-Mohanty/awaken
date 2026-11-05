package com.assismohanty.awaken.feature.quest

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetCounterBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun stateWith(sets: List<Int>, rest: Long? = null, full: Boolean = false) = QuestDetailUiState(
        loading = false,
        title = "Do push-ups",
        target = 25,
        unit = "reps",
        canComplete = true,
        setCounter = SetCounterUiState(
            sets = sets,
            total = sets.sum(),
            suggestedReps = (25 - sets.sum()).coerceAtLeast(1),
            restSeconds = rest,
            full = full,
        ),
    )

    private fun show(state: QuestDetailUiState, events: MutableList<QuestDetailEvent> = mutableListOf()) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { QuestDetailScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }
    }

    @Test
    fun showsProgressTowardTheTargetAndEachSet() {
        show(stateWith(listOf(10, 8)))

        composeRule.onNodeWithText("18 / 25 reps").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Set 1: 10 reps").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Set 2: 8 reps").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loggingASetSendsTheSuggestedRepsAndNeverCompletes() {
        val events = mutableListOf<QuestDetailEvent>()
        show(stateWith(listOf(10)), events)

        composeRule.onNodeWithText("Log set").performScrollTo().performClick()

        assertThat(events).containsExactly(QuestDetailEvent.AddSet(15))
    }

    @Test
    fun theStepperChangesTheRepsBeforeLogging() {
        val events = mutableListOf<QuestDetailEvent>()
        show(stateWith(emptyList()), events)

        composeRule.onNodeWithContentDescription("Fewer reps").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Fewer reps").performClick()
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()

        assertThat(events).containsExactly(QuestDetailEvent.AddSet(23))
    }

    @Test
    fun undoOnlyAppearsOnceASetExists() {
        show(stateWith(emptyList()))
        composeRule.onNodeWithText("Undo last set").assertDoesNotExist()
    }

    @Test
    fun undoReportsTheEvent() {
        val events = mutableListOf<QuestDetailEvent>()
        show(stateWith(listOf(10)), events)

        composeRule.onNodeWithText("Undo last set").performScrollTo().performClick()

        assertThat(events).containsExactly(QuestDetailEvent.UndoSet)
    }

    @Test
    fun theRestTimerCountsFromTheLastSet() {
        show(stateWith(listOf(10), rest = 65))
        composeRule.onNodeWithText("Rest 01:05").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFullCounterCannotLogAnotherSet() {
        val events = mutableListOf<QuestDetailEvent>()
        show(stateWith(List(30) { 1 }, full = true), events)

        composeRule.onNodeWithText("Log set").performScrollTo().performClick()

        assertThat(events).isEmpty()
        composeRule.onNodeWithText("Set limit reached.").performScrollTo().assertIsDisplayed()
    }
}
