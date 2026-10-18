package com.assismohanty.awaken.feature.today

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.feature.quest.RewardHost
import com.assismohanty.awaken.feature.quest.RewardHostViewModel
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Completing a quest from Today shows the reward sheet with the engine's XP (ARCH §6). */
@RunWith(AndroidJUnit4::class)
class QuestRewardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    @Test
    fun swipingAQuestShowsTheRewardSheetWithItsXp() {
        // Known fixture: SELF, Normal, no crit, no streak → 40 × 1.5 = 60 XP (GD §4.1).
        val quest = runBlocking {
            graph.seedHunter()
            graph.fixtureQuest("Iron Arms")
        }
        val todayViewModel = TodayViewModel(graph.observeToday, graph.dayRefresher, graph.completionCenter)
        val hostViewModel = RewardHostViewModel(graph.completionCenter)

        composeRule.setContent {
            AwakenTheme {
                val state by todayViewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                TodayScreen(state = state, onEvent = todayViewModel::onEvent, onOpenQuest = {}, onHabitsEvent = {})
                SnackbarHost(snackbarHostState)
                RewardHost(snackbarHostState = snackbarHostState, viewModel = hostViewModel)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Iron Arms").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Iron Arms").performTouchInput { swipeRight(startX = left, endX = right) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("+60 XP").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("+60 XP").assertExists()
        composeRule.onNodeWithText("= 60 XP", substring = true).assertExists()
        assertThat(runBlocking { graph.db.questDao().get(quest.id)?.status }).isEqualTo(QuestStatus.COMPLETED)
    }
}
