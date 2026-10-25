package com.assismohanty.awaken.feature.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.domain.AbandonCampaignUseCase
import com.assismohanty.awaken.core.domain.CampaignModel
import com.assismohanty.awaken.core.domain.CampaignPreview
import com.assismohanty.awaken.core.domain.CampaignProblem
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveCampaignUseCase
import com.assismohanty.awaken.core.domain.PreviewCampaignUseCase
import com.assismohanty.awaken.core.domain.StartCampaignResult
import com.assismohanty.awaken.core.domain.StartCampaignUseCase
import com.assismohanty.awaken.engine.campaign.CampaignScan
import com.assismohanty.awaken.engine.campaign.CampaignStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Where the start flow is (DS §4.10). PROGRESS shows the running (or last finished) campaign. */
enum class CampaignStep { INTRO, SCAN, FORGE, RESULT, OATH, PROGRESS }

data class CampaignFlow(
    val step: CampaignStep? = null,
    /** Question index on the SCAN step. */
    val question: Int = 0,
    val answers: Map<String, Int> = emptyMap(),
    val preview: CampaignPreview? = null,
    val sealing: Boolean = false,
    val confirmAbandon: Boolean = false,
    val error: CampaignProblem? = null,
    /** The finished campaign has been acknowledged: the intro shows instead of its result. */
    val startingAnew: Boolean = false,
)

data class CampaignUiState(
    val loading: Boolean = true,
    val step: CampaignStep = CampaignStep.INTRO,
    val question: Int = 0,
    val answers: Map<String, Int> = emptyMap(),
    val preview: CampaignPreview? = null,
    val campaign: CampaignModel? = null,
    val hunterName: String = "",
    val sealing: Boolean = false,
    val confirmAbandon: Boolean = false,
    val error: CampaignProblem? = null,
) {
    val questionCount: Int get() = CampaignScan.QUESTIONS.size
}

sealed interface CampaignEvent {
    data object Begin : CampaignEvent
    data class Answer(val value: Int) : CampaignEvent
    data object BackQuestion : CampaignEvent
    data object ForgeFinished : CampaignEvent
    data object ToOath : CampaignEvent
    data object Seal : CampaignEvent
    data object AskAbandon : CampaignEvent
    data object ConfirmAbandon : CampaignEvent
    data object DismissAbandon : CampaignEvent
    data object StartAnew : CampaignEvent
}

@HiltViewModel
class CampaignViewModel @Inject constructor(
    observeCampaign: ObserveCampaignUseCase,
    dayRefresher: DayRefresher,
    hunterDao: HunterDao,
    private val previewCampaign: PreviewCampaignUseCase,
    private val startCampaign: StartCampaignUseCase,
    private val abandonCampaign: AbandonCampaignUseCase,
) : ViewModel() {

    private val flow = MutableStateFlow(CampaignFlow())

    val uiState: StateFlow<CampaignUiState> = combine(
        observeCampaign(dayRefresher.ticks),
        hunterDao.observeProfile().map { it?.name.orEmpty() },
        flow,
    ) { campaign, name, f ->
        val running = campaign?.campaign?.status == CampaignStatus.ACTIVE
        val step = when {
            f.step != null -> f.step
            running -> CampaignStep.PROGRESS
            campaign != null && !f.startingAnew -> CampaignStep.PROGRESS
            else -> CampaignStep.INTRO
        }
        CampaignUiState(
            loading = false,
            step = step,
            question = f.question,
            answers = f.answers,
            preview = f.preview,
            campaign = campaign,
            hunterName = name,
            sealing = f.sealing,
            confirmAbandon = f.confirmAbandon,
            error = f.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CampaignUiState())

    fun onEvent(event: CampaignEvent) {
        when (event) {
            CampaignEvent.Begin -> flow.update { CampaignFlow(step = CampaignStep.SCAN, startingAnew = true) }
            is CampaignEvent.Answer -> answer(event.value)
            CampaignEvent.BackQuestion -> flow.update {
                if (it.question == 0) it.copy(step = CampaignStep.INTRO) else it.copy(question = it.question - 1)
            }
            CampaignEvent.ForgeFinished -> flow.update { if (it.preview != null) it.copy(step = CampaignStep.RESULT) else it }
            CampaignEvent.ToOath -> flow.update { it.copy(step = CampaignStep.OATH) }
            CampaignEvent.Seal -> seal()
            CampaignEvent.AskAbandon -> flow.update { it.copy(confirmAbandon = true) }
            CampaignEvent.DismissAbandon -> flow.update { it.copy(confirmAbandon = false) }
            CampaignEvent.ConfirmAbandon -> viewModelScope.launch {
                runCatchingLogged { abandonCampaign() }
                flow.update { CampaignFlow(startingAnew = true) }
            }
            CampaignEvent.StartAnew -> flow.update { CampaignFlow(startingAnew = true) }
        }
    }

    private fun answer(value: Int) {
        val current = flow.value
        val id = CampaignScan.QUESTIONS.getOrNull(current.question)?.first ?: return
        val answers = current.answers + (id to value)
        if (current.question + 1 < CampaignScan.QUESTIONS.size) {
            flow.update { it.copy(answers = answers, question = it.question + 1) }
            return
        }
        // Last answer: forge (the animation runs while the plan is computed; RESULT waits for both).
        flow.update { it.copy(answers = answers, step = CampaignStep.FORGE, preview = null) }
        viewModelScope.launch {
            val preview = runCatchingLogged { previewCampaign(answers) }
            flow.update { it.copy(preview = preview) }
        }
    }

    private fun seal() {
        val answers = flow.value.answers
        if (flow.value.sealing) return
        flow.update { it.copy(sealing = true) }
        viewModelScope.launch {
            when (val result = runCatchingLogged { startCampaign(answers) }) {
                is StartCampaignResult.Started -> flow.update { CampaignFlow() }
                is StartCampaignResult.Refused -> flow.update { it.copy(sealing = false, error = result.problem, step = CampaignStep.RESULT) }
                StartCampaignResult.IncompleteScan, null -> flow.update { it.copy(sealing = false, step = CampaignStep.SCAN, question = 0) }
            }
        }
    }

    private suspend fun <T> runCatchingLogged(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Campaign action failed")
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
