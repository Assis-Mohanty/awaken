package com.assismohanty.awaken.feature.gate

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.quest.RewardHost
import com.assismohanty.awaken.feature.quest.RewardHostViewModel
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/** DESIGN_SYSTEM §4.4: pick a portal → set up → start → the dial; a collapse shows the ember window. */
@RunWith(AndroidJUnit4::class)
class GatesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private val tiles = GateRank.entries.map { GateTile(it, it.durationMinutes, available = true) }

    @Test
    fun pickingAPortalThenStartingShowsTheDial() {
        val events = mutableListOf<GatesEvent>()
        composeRule.setContent {
            var state by remember { mutableStateOf(GatesUiState(loading = false, tiles = tiles)) }
            AwakenTheme {
                GatesScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state = when (event) {
                            is GatesEvent.Pick -> state.copy(setup = GateSetup(event.rank))
                            GatesEvent.Enter -> state.copy(
                                setup = null,
                                active = ActiveGate(
                                    rank = GateRank.D, stat = Stat.INT, label = null, remaining = Duration.ofMinutes(25),
                                    total = Duration.ofMinutes(25), breachUsed = Duration.ZERO, paused = false,
                                    pauseLeft = Duration.ZERO, canPause = true,
                                ),
                            )
                            else -> state
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("D-Gate, 25 min").performClick()
        composeRule.onNodeWithText("Start gate").performScrollTo().performClick()

        assertThat(events).containsExactly(GatesEvent.Pick(GateRank.D), GatesEvent.Enter).inOrder()
        composeRule.onNodeWithContentDescription("25:00 remaining", substring = true).assertExists()
        composeRule.onNodeWithText("Pause (5:00)").assertExists()
    }

    @Test
    fun aCollapseShowsTheEmberWindowWithTheBackgroundTime() {
        graph.completionCenter.gateCollapsed(GateCollapseReason.BREACH, backgroundMs = 102_000)
        val viewModel = RewardHostViewModel(graph.completionCenter, graph.notices)
        composeRule.setContent {
            AwakenTheme { RewardHost(snackbarHostState = remember { SnackbarHostState() }, viewModel = viewModel) }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            composeRule.onAllNodesWithText("Background time 1:42 exceeded the 1:30 limit.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            graph.completionCenter.events.value.isEmpty()
        }
    }
}
