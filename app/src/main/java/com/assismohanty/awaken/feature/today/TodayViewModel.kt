package com.assismohanty.awaken.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveTodayUseCase
import com.assismohanty.awaken.core.domain.StartTrialResult
import com.assismohanty.awaken.core.domain.StartTrialUseCase
import com.assismohanty.awaken.core.domain.TodayModel
import com.assismohanty.awaken.core.domain.TrialSummary
import com.assismohanty.awaken.core.domain.isOpen
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class TodayUiState(
    val loading: Boolean = true,
    val header: HunterHeaderModel? = null,
    /** Equipped title id; the screen resolves the display text into the header. */
    val equippedTitleId: String? = null,
    val rank: Rank = Rank.E,
    /** Pinned above the directive in an ember window when a penalty quest exists (DESIGN_SYSTEM §4.2). */
    val penalty: QuestRowModel? = null,
    val main: QuestRowModel? = null,
    val sides: List<QuestRowModel> = emptyList(),
    val habits: List<QuestRowModel> = emptyList(),
    val completed: Int = 0,
    val total: Int = 0,
    /** The day meets the clear rule (GD §7.1): title becomes "DIRECTIVE CLEARED". */
    val cleared: Boolean = false,
    val systemLine: String? = null,
    /** The Rank Trial slot: available to begin, or running (GD §5). */
    val trial: TrialSummary? = null,
    /** Today's open and cleared trial stage quests. */
    val trialStages: List<QuestRowModel> = emptyList(),
    /** A penalty quest is still open: Today is tinted ember (GD §7.3, visual only). */
    val penaltyOpen: Boolean = false,
)

sealed interface TodayEvent {
    data class Complete(val questInstanceId: String) : TodayEvent
    data object BeginTrial : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    observeToday: ObserveTodayUseCase,
    dayRefresher: DayRefresher,
    private val completionCenter: CompletionCenter,
    private val startTrial: StartTrialUseCase,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = observeToday(dayChanges = dayRefresher.ticks)
        .map { model -> model?.toUiState() ?: TodayUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayUiState())

    fun onEvent(event: TodayEvent) {
        when (event) {
            is TodayEvent.Complete -> viewModelScope.launch {
                try {
                    completionCenter.complete(event.questInstanceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Completion failed")
                    completionCenter.message(UserMessage.Error)
                }
            }
            TodayEvent.BeginTrial -> viewModelScope.launch {
                val message = try {
                    when (startTrial()) {
                        is StartTrialResult.Started -> UserMessage.TrialStarted
                        StartTrialResult.NoStages -> UserMessage.TrialNoStages
                        StartTrialResult.NotAvailable -> UserMessage.Error
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Starting the trial failed")
                    UserMessage.Error
                }
                completionCenter.message(message)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun TodayModel.toUiState(): TodayUiState {
    val directive = quests.filter { it.type == QuestType.MAIN || it.type == QuestType.SIDE }
    val habits = quests.filter { it.type == QuestType.HABIT }
    val counted = directive + habits
    return TodayUiState(
        loading = false,
        header = HunterHeaderModel(
            name = hunter.name,
            title = null,
            level = hunter.level,
            rank = hunter.rank,
            xpIntoLevel = hunter.xpIntoLevel,
            xpForLevel = hunter.xpForLevel,
            streak = hunter.streak,
            gold = hunter.gold,
        ),
        equippedTitleId = hunter.equippedTitleId,
        rank = hunter.rank,
        penalty = quests.firstOrNull { it.type == QuestType.PENALTY }?.toRowModel(),
        main = directive.firstOrNull { it.type == QuestType.MAIN }?.toRowModel(),
        sides = directive.filter { it.type == QuestType.SIDE }.map { it.toRowModel() },
        habits = habits.map { it.toRowModel() },
        completed = counted.count { it.status == QuestStatus.COMPLETED },
        total = counted.size,
        cleared = hunter.todayCleared,
        systemLine = systemLine,
        trial = trial,
        trialStages = quests.filter { it.type == QuestType.TRIAL }.map { it.toRowModel() },
        penaltyOpen = quests.any { it.type == QuestType.PENALTY && it.isOpen },
    )
}

internal fun QuestInstanceEntity.toRowModel() = QuestRowModel(
    id = id,
    title = title,
    stat = stat,
    target = target,
    unit = unit,
    difficulty = difficulty,
    verification = verification.source,
    isMain = type == QuestType.MAIN,
    state = when (status) {
        QuestStatus.COMPLETED -> QuestRowState.DONE
        QuestStatus.ACTIVE, QuestStatus.STARTED -> QuestRowState.OPEN
        QuestStatus.EXPIRED, QuestStatus.MISSED, QuestStatus.REROLLED -> QuestRowState.CLOSED
    },
)
