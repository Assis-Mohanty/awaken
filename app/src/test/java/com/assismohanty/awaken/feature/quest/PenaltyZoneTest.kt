package com.assismohanty.awaken.feature.quest

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.repo.Notice
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/** GD §7.3: the SYSTEM WARNING shows once for a failed day, and acknowledging it clears the notice. */
@RunWith(AndroidJUnit4::class)
class PenaltyZoneTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    @Test
    fun penaltyWarningShowsOnceAndIsAcknowledged() {
        runBlocking { graph.notices.add(listOf(Notice.PenaltyWarning("2026-10-18"))) }
        val viewModel = RewardHostViewModel(graph.completionCenter, graph.notices, graph.narrative, graph.notificationAccess, graph.uiPrefs)
        composeRule.setContent {
            AwakenTheme { RewardHost(snackbarHostState = remember { SnackbarHostState() }, viewModel = viewModel) }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            composeRule.onAllNodesWithText("SYSTEM WARNING").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Face it").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            composeRule.onAllNodesWithText("SYSTEM WARNING").fetchSemanticsNodes().isEmpty()
        }
        assertThat(runBlocking { graph.notices.pending.first() }).isEmpty()
        assertThat(graph.completionCenter.events.value).isEmpty()
    }
}
