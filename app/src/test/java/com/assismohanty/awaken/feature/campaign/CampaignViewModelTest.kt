package com.assismohanty.awaken.feature.campaign

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.campaign.CampaignScan
import com.assismohanty.awaken.engine.campaign.CampaignStatus
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The whole start flow through the ViewModel: scan → forge → result → oath → a running campaign. */
@RunWith(AndroidJUnit4::class)
class CampaignViewModelTest {
    private val graph = TestGraph()

    // viewModelScope runs on Main; a test Main keeps runBlocking from starving the Robolectric main looper.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        graph.close()
    }

    private fun viewModel() = CampaignViewModel(
        graph.observeCampaign, graph.dayRefresher, graph.db.hunterDao(), graph.previewCampaign, graph.startCampaign, graph.abandonCampaign,
    )

    private suspend fun CampaignViewModel.awaitState(predicate: (CampaignUiState) -> Boolean): CampaignUiState =
        withTimeout(5_000) { uiState.first { !it.loading && predicate(it) } }

    @Test
    fun scanForgeResultOathThenProgress() = runBlocking {
        graph.seedHunter()
        graph.rollover()
        val vm = viewModel()
        vm.awaitState { it.step == CampaignStep.INTRO }

        vm.onEvent(CampaignEvent.Begin)
        repeat(CampaignScan.QUESTIONS.size) { vm.onEvent(CampaignEvent.Answer(it % 4)) }
        val forged = vm.awaitState { it.step == CampaignStep.FORGE && it.preview != null }
        assertThat(forged.preview?.problem).isNull()

        vm.onEvent(CampaignEvent.ForgeFinished)
        vm.awaitState { it.step == CampaignStep.RESULT }
        vm.onEvent(CampaignEvent.ToOath)
        vm.awaitState { it.step == CampaignStep.OATH }
        vm.onEvent(CampaignEvent.Seal)

        val running = vm.awaitState { it.step == CampaignStep.PROGRESS && it.campaign?.campaign?.status == CampaignStatus.ACTIVE }
        assertThat(running.campaign?.dayIndex).isEqualTo(1)
        assertThat(running.campaign?.habits).isNotEmpty()
    }

    @Test
    fun abandoningReturnsToTheIntro() = runBlocking {
        graph.seedHunter()
        graph.rollover()
        graph.startCampaign(CampaignScan.QUESTIONS.associate { it.first to 1 })
        val vm = viewModel()
        vm.awaitState { it.step == CampaignStep.PROGRESS }

        vm.onEvent(CampaignEvent.AskAbandon)
        vm.awaitState { it.confirmAbandon }
        vm.onEvent(CampaignEvent.ConfirmAbandon)

        vm.awaitState { it.step == CampaignStep.INTRO }
        assertThat(graph.db.campaignDao().active()).isNull()
    }
}
