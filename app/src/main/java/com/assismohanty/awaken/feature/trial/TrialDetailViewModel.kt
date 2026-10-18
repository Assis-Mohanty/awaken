package com.assismohanty.awaken.feature.trial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.TrialReader
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.core.domain.isOpen
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.today.toRowModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** One stage of the running trial: cleared on a day, open today (with its quest), or waiting for tomorrow. */
sealed interface StageState {
    val stat: Stat

    data class Cleared(override val stat: Stat, val on: LocalDate) : StageState
    data class Today(override val stat: Stat, val quest: QuestRowModel) : StageState
    data class Tomorrow(override val stat: Stat) : StageState
}

data class TrialDetailUiState(
    val loading: Boolean = true,
    /** No ACTIVE trial (passed, failed or never started). */
    val none: Boolean = false,
    val rank: Rank = Rank.D,
    val start: LocalDate? = null,
    val expires: LocalDate? = null,
    val daysLeft: Int = 0,
    val stages: List<StageState> = emptyList(),
)

sealed interface TrialDetailEvent {
    data class Complete(val questInstanceId: String) : TrialDetailEvent
}

@HiltViewModel
class TrialDetailViewModel @Inject constructor(
    hunterDao: HunterDao,
    trialDao: TrialDao,
    questDao: QuestDao,
    trialReader: TrialReader,
    dayRefresher: DayRefresher,
    clock: Clock,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TrialDetailUiState> = combine(hunterDao.observeProfile(), trialDao.observeActive(), dayRefresher.ticks) { profile, trial, _ ->
        profile to trial
    }.flatMapLatest { (profile, trial) ->
        if (profile == null || trial == null) return@flatMapLatest flowOf(TrialDetailUiState(loading = false, none = true))
        val today = clock.gameDate(profile.dayStartHour)
        questDao.observeTrialQuests(trial.startedGameDate, trial.expiresGameDate).mapLatest { quests ->
            val progress = trialReader.progress(trial, today)
            val todays = quests.filter { it.gameDate == today && it.type == QuestType.TRIAL }
            TrialDetailUiState(
                loading = false,
                rank = trial.targetRank,
                start = trial.startedGameDate,
                expires = trial.expiresGameDate,
                daysLeft = (ChronoUnit.DAYS.between(today, trial.expiresGameDate).toInt() + 1).coerceAtLeast(0),
                stages = progress.stageStats.map { stat ->
                    val clearedOn = progress.cleared[stat]
                    val open = todays.firstOrNull { it.stat == stat && it.isOpen }
                    when {
                        clearedOn != null -> StageState.Cleared(stat, clearedOn)
                        open != null -> StageState.Today(stat, open.toRowModel())
                        else -> StageState.Tomorrow(stat)
                    }
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TrialDetailUiState())

    fun onEvent(event: TrialDetailEvent) {
        when (event) {
            is TrialDetailEvent.Complete -> viewModelScope.launch {
                try {
                    completionCenter.complete(event.questInstanceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Completing a trial stage failed")
                    completionCenter.message(UserMessage.Error)
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
