package com.assismohanty.awaken.feature.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Long-press a habit (on Today or in the list) → Archive or Delete, each confirmed first. */
@RunWith(AndroidJUnit4::class)
class HabitOptionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val read = HabitItem("habit-1", "Read", Stat.INT, Difficulty.NORMAL, DayOfWeek.entries.toSet(), 20, "pages")

    @Test
    fun longPressingAHabitOnTodayOpensItsOptions() {
        val events = mutableListOf<HabitsEvent>()
        val row = previewTodayState.habits.first()
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                TodayScreen(
                    state = previewTodayState.copy(habitIdByQuest = mapOf(row.id to "habit-1")),
                    onEvent = {},
                    onOpenQuest = {},
                    onOpenTrial = {},
                    onHabitsEvent = { events += it },
                    onDayPlanEvent = {},
                )
            }
        }

        composeRule.onNodeWithText(row.title).performScrollTo().performTouchInput { longClick() }

        assertThat(events).containsExactly(HabitsEvent.Options("habit-1"))
    }

    @Test
    fun theOptionsAskForArchiveOrDelete() {
        val events = mutableListOf<HabitsEvent>()
        composeRule.setContent { AwakenTheme(reduceMotion = true) { HabitOptions(read, onEvent = { events += it }) } }

        composeRule.onNodeWithText("Archive habit").performClick()
        composeRule.onNodeWithText("Delete habit").performClick()

        assertThat(events).containsExactly(
            HabitsEvent.RequestRemove(HabitRemoveAction.ARCHIVE),
            HabitsEvent.RequestRemove(HabitRemoveAction.DELETE),
        ).inOrder()
    }

    @Test
    fun campaignHabitsOfferNoRemoval() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { HabitOptions(read.copy(campaign = true), onEvent = {}) } }

        composeRule.onNodeWithText("Delete habit").assertDoesNotExist()
        composeRule.onNodeWithText("Archive habit").assertDoesNotExist()
    }

    @Test
    fun deleteIsConfirmedBeforeAnythingHappens() {
        val events = mutableListOf<HabitsEvent>()
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                HabitSheets(
                    state = HabitsUiState(habits = listOf(read), sheet = HabitSheet.Confirm(read, HabitRemoveAction.DELETE, fromList = false)),
                    onEvent = { events += it },
                )
            }
        }

        composeRule.onNodeWithText("Delete \"Read\"?").assertExists()
        composeRule.onNodeWithText("Delete habit").performClick()

        assertThat(events).containsExactly(HabitsEvent.ConfirmRemove)
    }
}
