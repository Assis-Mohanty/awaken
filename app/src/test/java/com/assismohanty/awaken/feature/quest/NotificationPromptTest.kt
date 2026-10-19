package com.assismohanty.awaken.feature.quest

import android.Manifest
import android.app.Application
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** ARCH §3.5: the reminders pre-prompt appears once, after the first completed quest, and never again. */
@RunWith(AndroidJUnit4::class)
class NotificationPromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph(notificationsPrompted = false)

    @Before
    fun setUp() {
        // Robolectric grants declared permissions by default; Android 13+ starts without POST_NOTIFICATIONS.
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = graph.close()

    private fun show() {
        val viewModel = RewardHostViewModel(graph.completionCenter, graph.notices, graph.narrative, graph.notificationAccess, graph.uiPrefs)
        composeRule.setContent {
            AwakenTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                SnackbarHost(snackbarHostState)
                RewardHost(snackbarHostState = snackbarHostState, viewModel = viewModel)
            }
        }
    }

    private fun completeAndDismissRewardSheet(name: String) {
        val quest = runBlocking { graph.fixtureQuest(name) }
        runBlocking { graph.completionCenter.complete(quest.id) }
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Done").performClick()
    }

    private fun promptVisible() = composeRule.onAllNodesWithText("REMINDERS").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theFirstCompletionAsksOnceAndRemembersTheAnswer() {
        runBlocking { graph.seedHunter() }
        show()

        completeAndDismissRewardSheet("first")
        composeRule.waitUntil(timeoutMillis = 5_000) { promptVisible() }
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { !promptVisible() }
        composeRule.waitUntil(timeoutMillis = 5_000) { runBlocking { graph.uiPrefs.prefs.first().notificationsPrompted } }

        completeAndDismissRewardSheet("second")
        composeRule.waitForIdle()
        assertThat(promptVisible()).isFalse()
    }

    @Test
    fun nothingIsAskedWhenTheHunterAlreadyAnswered() {
        runBlocking {
            graph.seedHunter()
            graph.uiPrefs.setNotificationsPrompted()
        }
        show()

        completeAndDismissRewardSheet("first")
        composeRule.waitForIdle()

        assertThat(promptVisible()).isFalse()
    }

    @Test
    fun nothingIsAskedWhenNotificationsAreAlreadyAllowed() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { graph.seedHunter() }
        show()

        completeAndDismissRewardSheet("first")
        composeRule.waitForIdle()

        assertThat(promptVisible()).isFalse()
    }
}
