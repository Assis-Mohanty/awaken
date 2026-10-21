package com.assismohanty.awaken.feature.quest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.RerollQuestUseCase
import com.assismohanty.awaken.core.domain.RerollResult
import com.assismohanty.awaken.core.domain.ReportPathQuestUseCase
import com.assismohanty.awaken.core.domain.ReportReason
import com.assismohanty.awaken.core.domain.ReportResult
import com.assismohanty.awaken.core.domain.StartPathGateResult
import com.assismohanty.awaken.core.domain.StartPathGateUseCase
import com.assismohanty.awaken.core.domain.StartQuestUseCase
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.QuestReason
import com.assismohanty.awaken.engine.QuestReasons
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.navigation.QuestDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Static context for the "Why this quest?" line and reroll pricing, loaded once per open. */
private data class PathInfo(val name: String, val rationale: String, val gateRank: GateRank?)

private data class DetailContext(
    val today: LocalDate,
    val focusStats: List<Stat>,
    val lastActive: Map<Stat, LocalDate>,
    /** Cost of the next reroll today; null when the daily limit is used up (GD §6.3). */
    val nextRerollCost: Int?,
    /** Set for Path quests. */
    val path: PathInfo? = null,
)

data class QuestDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val description: String = "",
    val type: QuestType = QuestType.SIDE,
    val stat: Stat = Stat.STR,
    val secondaryStat: Stat? = null,
    val target: Int = 0,
    val unit: String = "",
    val difficulty: Difficulty = Difficulty.NORMAL,
    val verification: VerificationSource = VerificationSource.SELF,
    val minDurationSec: Int = 0,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val reason: QuestReason? = null,
    /** Seconds until Complete is allowed after Begin (GD §6.5); 0 when ready or not started. */
    val readyInSeconds: Long = 0,
    val canBegin: Boolean = false,
    val canComplete: Boolean = false,
    val canUndo: Boolean = false,
    val canReroll: Boolean = false,
    /** 0 = free; meaningful only when [canReroll]. */
    val rerollCost: Int = 0,
    val busy: Boolean = false,
    /** Set when the quest is done or replaced; the screen navigates back once. */
    val closed: Boolean = false,
    /** Path quests (DS §4.3): the Path it belongs to, the Oracle's rationale and the gate a GATE-mode quest needs. */
    val pathName: String? = null,
    val rationale: String? = null,
    val gateRank: GateRank? = null,
    val canEnterGate: Boolean = false,
    val reportable: Boolean = false,
    /** The gate opened; the screen leaves for the Gates tab once. */
    val enteredGate: Boolean = false,
)

sealed interface QuestDetailEvent {
    data object Begin : QuestDetailEvent
    data object Complete : QuestDetailEvent
    data object Undo : QuestDetailEvent
    data object Reroll : QuestDetailEvent
    data object EnterGate : QuestDetailEvent
    data class Report(val reason: ReportReason) : QuestDetailEvent
}

@HiltViewModel
class QuestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val questDao: QuestDao,
    completionDao: CompletionDao,
    private val hunterDao: HunterDao,
    private val reader: GameStateReader,
    private val startQuest: StartQuestUseCase,
    private val rerollQuest: RerollQuestUseCase,
    private val pathDao: PathDao,
    private val startPathGate: StartPathGateUseCase,
    private val reportPathQuest: ReportPathQuestUseCase,
    private val completionCenter: CompletionCenter,
    private val clock: Clock,
) : ViewModel() {

    private val questInstanceId = savedStateHandle.toRoute<QuestDetailRoute>().questInstanceId
    private val context = MutableStateFlow<DetailContext?>(null)
    private val action = MutableStateFlow(ActionState())

    private data class ActionState(val busy: Boolean = false, val closed: Boolean = false, val enteredGate: Boolean = false)

    /** Re-evaluates time-based state (countdown, undo window) once a second while the screen is visible. */
    private val ticks: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<QuestDetailUiState> = combine(
        questDao.observe(questInstanceId),
        completionDao.observeLatestActive(questInstanceId),
        context,
        action,
        ticks,
    ) { quest, completion, ctx, actionState, now ->
        if (quest == null) return@combine QuestDetailUiState(loading = false, notFound = true, closed = actionState.closed)
        if (ctx == null) return@combine QuestDetailUiState()
        quest.toUiState(ctx, completion?.completedAt, actionState, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QuestDetailUiState())

    init {
        viewModelScope.launch { refreshContext() }
    }

    fun onEvent(event: QuestDetailEvent) {
        when (event) {
            QuestDetailEvent.Begin -> run { startQuest(questInstanceId) }
            QuestDetailEvent.Complete -> run {
                if (completionCenter.complete(questInstanceId)) action.update { it.copy(closed = true) }
            }
            QuestDetailEvent.Undo -> run { completionCenter.undo(questInstanceId) }
            QuestDetailEvent.EnterGate -> run {
                when (startPathGate(questInstanceId)) {
                    is StartPathGateResult.Started -> action.update { it.copy(enteredGate = true) }
                    StartPathGateResult.AlreadyRunning -> completionCenter.message(UserMessage.GateAlreadyRunning)
                    StartPathGateResult.TooLate -> completionCenter.message(UserMessage.GateTooLate)
                    StartPathGateResult.NoHunter, StartPathGateResult.NotAGateQuest -> completionCenter.message(UserMessage.Error)
                }
            }
            is QuestDetailEvent.Report -> run {
                when (reportPathQuest(questInstanceId, event.reason)) {
                    ReportResult.Reported -> {
                        completionCenter.message(UserMessage.QuestReported)
                        action.update { it.copy(closed = true) }
                    }
                    ReportResult.NotReportable -> completionCenter.message(UserMessage.Error)
                }
            }
            QuestDetailEvent.Reroll -> run {
                when (val result = rerollQuest(questInstanceId)) {
                    is RerollResult.Rerolled -> {
                        completionCenter.message(UserMessage.Rerolled(result.cost))
                        action.update { it.copy(closed = true) }
                    }
                    RerollResult.LimitReached -> completionCenter.message(UserMessage.RerollLimit)
                    is RerollResult.InsufficientGold -> completionCenter.message(UserMessage.RerollGold(result.cost))
                    RerollResult.NoAlternative -> completionCenter.message(UserMessage.RerollNone)
                    RerollResult.NotRerollable -> completionCenter.message(UserMessage.Error)
                }
                refreshContext()
            }
        }
    }

    /** One action at a time; failures surface as a System message, never a crash. */
    private fun run(block: suspend () -> Unit) {
        if (action.value.busy) return
        action.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Quest action failed")
                completionCenter.message(UserMessage.Error)
            } finally {
                action.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun refreshContext() {
        val profile = hunterDao.getProfile() ?: return
        val today = clock.gameDate(profile.dayStartHour)
        context.value = DetailContext(
            today = today,
            focusStats = profile.focusStats,
            lastActive = reader.lastActiveDates(),
            nextRerollCost = DirectiveGenerator.rerollCost(questDao.countRerolled(today) + 1),
            path = pathInfo(),
        )
    }

    private suspend fun pathInfo(): PathInfo? {
        val quest = questDao.get(questInstanceId) ?: return null
        val slot = quest.pathSlotId?.let { pathDao.slot(it) } ?: return null
        val path = pathDao.get(slot.pathId) ?: return null
        return PathInfo(path.goal, slot.rationale, slot.gateRank)
    }

    private fun QuestInstanceEntity.toUiState(
        ctx: DetailContext,
        completedAt: Instant?,
        actionState: ActionState,
        now: Instant,
    ): QuestDetailUiState {
        val open = status == QuestStatus.ACTIVE || status == QuestStatus.STARTED
        val isToday = gameDate == ctx.today
        val self = verification.source == VerificationSource.SELF
        val started = startedAt
        val readyIn = if (status == QuestStatus.STARTED && started != null && !QuestRules.canComplete(started, now, minDurationSec)) {
            Duration.ofSeconds(minDurationSec.toLong()).minus(Duration.between(started, now)).seconds.coerceAtLeast(1)
        } else {
            0L
        }
        val rerollCost = ctx.nextRerollCost
        return QuestDetailUiState(
            loading = false,
            title = title,
            description = description,
            type = type,
            stat = stat,
            secondaryStat = secondaryStat,
            target = target,
            unit = unit,
            difficulty = difficulty,
            verification = verification.source,
            minDurationSec = minDurationSec,
            status = status,
            reason = QuestReasons.reasonFor(type, stat, ctx.focusStats, ctx.lastActive, ctx.today),
            readyInSeconds = readyIn,
            canBegin = isToday && self && status == QuestStatus.ACTIVE && minDurationSec > 0,
            canComplete = isToday && self && open && readyIn == 0L,
            canUndo = status == QuestStatus.COMPLETED && completedAt != null && QuestRules.canUndo(completedAt, now),
            canReroll = isToday && open && (type == QuestType.SIDE || (type == QuestType.PATH && pathSlotId != null)) && rerollCost != null,
            rerollCost = rerollCost ?: 0,
            busy = actionState.busy,
            closed = actionState.closed,
            pathName = ctx.path?.name,
            rationale = ctx.path?.rationale?.takeIf { it.isNotBlank() },
            gateRank = ctx.path?.gateRank,
            canEnterGate = isToday && open && ctx.path?.gateRank != null && verification.source == VerificationSource.TIMER,
            reportable = isToday && open && pathSlotId != null,
            enteredGate = actionState.enteredGate,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 1_000L
    }
}
