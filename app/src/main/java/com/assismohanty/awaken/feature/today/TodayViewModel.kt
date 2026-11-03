package com.assismohanty.awaken.feature.today

import java.time.LocalDate
import com.assismohanty.awaken.engine.model.TimeOfDay
import androidx.lifecycle.ViewModel
import com.assismohanty.awaken.core.designsystem.components.HabitRowModel
import com.assismohanty.awaken.core.domain.AdjustQuantityUseCase
import com.assismohanty.awaken.core.domain.AvoidHabitUseCases
import com.assismohanty.awaken.core.domain.HabitActionResult
import com.assismohanty.awaken.core.domain.HabitTodayInfo
import com.assismohanty.awaken.core.domain.SkipHabitUseCase
import com.assismohanty.awaken.core.domain.StartGateResult
import com.assismohanty.awaken.core.domain.TimedHabitStarter
import com.assismohanty.awaken.engine.model.HabitType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.PathBadgeModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowModel
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveTodayUseCase
import com.assismohanty.awaken.core.domain.PathBadgeInfo
import com.assismohanty.awaken.core.domain.StartTrialResult
import com.assismohanty.awaken.core.domain.StartTrialUseCase
import com.assismohanty.awaken.core.domain.TodayModel
import com.assismohanty.awaken.engine.MissLine
import com.assismohanty.awaken.core.domain.TrialSummary
import com.assismohanty.awaken.core.domain.isOpen
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.SetCounter
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.engine.XpCalculator
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
    val rank: Rank = Rank.E,
    /** The comeback quest after a missed day (GD §7.3); leads the plan in its own row. */
    val penalty: QuestRowModel? = null,
    val main: QuestRowModel? = null,
    val sides: List<QuestRowModel> = emptyList(),
    /** Today's Path quests that were not promoted to Main (GD §6.6.6). */
    val paths: List<QuestRowModel> = emptyList(),
    val habits: List<QuestRowModel> = emptyList(),
    /** Habit quest instance → its habit, for the long-press Archive / Delete (campaign habits included; refused there). */
    val habitIdByQuest: Map<String, String> = emptyMap(),
    val completed: Int = 0,
    val total: Int = 0,
    /** The day meets the clear rule (GD §7.1). */
    val cleared: Boolean = false,
    /** The rank-up test: available to begin, or running (GD §5). */
    val trial: TrialSummary? = null,
    /** Today's open and cleared trial stage quests. */
    val trialStages: List<QuestRowModel> = emptyList(),
    /** Today's app-limits quest (GD §6.7), shown as an Anytime row, not as a quest row. */
    val discipline: DisciplineCard? = null,
    /** The block "now" is in (GD §14.5): highlighted on the timeline, earlier blocks collapse. */
    val currentBlock: TimeOfDay = TimeOfDay.MORNING,
    val gameDate: LocalDate? = null,
    /** The calm line shown the day after a missed day. */
    val missLine: MissLine? = null,
    /** "Your weekly review is open" row, Saturday to Monday until done (GD §14.4). */
    val reviewOpen: Boolean = false,
)

/** [held] false once a limit was crossed; [xpAtStake] is the price for today's streak. */
data class DisciplineCard(val held: Boolean, val xpAtStake: Int)

sealed interface TodayEvent {
    data class Complete(val questInstanceId: String) : TodayEvent
    data object BeginTrial : TodayEvent

    /** Habit controls (GD §16.1-16.2); the id is the day's habit quest instance. */
    data class HabitPlus(val questInstanceId: String) : TodayEvent
    data class HabitMinus(val questInstanceId: String) : TodayEvent
    data class HabitKept(val questInstanceId: String) : TodayEvent
    data class HabitSlip(val questInstanceId: String) : TodayEvent
    data class HabitUndoSlip(val questInstanceId: String) : TodayEvent
    data class HabitStartTimed(val questInstanceId: String) : TodayEvent
    data class HabitUndoSkip(val habitId: String) : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    observeToday: ObserveTodayUseCase,
    dayRefresher: DayRefresher,
    private val completionCenter: CompletionCenter,
    private val startTrial: StartTrialUseCase,
    private val adjustQuantity: AdjustQuantityUseCase,
    private val avoidHabit: AvoidHabitUseCases,
    private val skipHabit: SkipHabitUseCase,
    private val timedHabits: TimedHabitStarter,
) : ViewModel() {

    private val gateStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once when a Timed habit's session started, so Today opens the Focus screen (GD §16.1). */
    val gateEntered: SharedFlow<Unit> = gateStarted.asSharedFlow()

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
            is TodayEvent.HabitPlus -> habitAction { adjustQuantity.plus(event.questInstanceId) }
            is TodayEvent.HabitMinus -> habitAction { adjustQuantity.minus(event.questInstanceId) }
            is TodayEvent.HabitKept -> habitAction { avoidHabit.keptToday(event.questInstanceId) }
            is TodayEvent.HabitSlip -> habitAction { avoidHabit.slipped(event.questInstanceId) }
            is TodayEvent.HabitUndoSlip -> habitAction { avoidHabit.undoSlip(event.questInstanceId) }
            is TodayEvent.HabitUndoSkip -> habitAction { skipHabit.undoSkip(event.habitId) }
            is TodayEvent.HabitStartTimed -> viewModelScope.launch {
                try {
                    when (timedHabits.startHabit(event.questInstanceId)) {
                        is StartGateResult.Started -> gateStarted.tryEmit(Unit)
                        StartGateResult.AlreadyRunning -> completionCenter.message(UserMessage.GateAlreadyRunning)
                        StartGateResult.TooLate -> completionCenter.message(UserMessage.GateTooLate)
                        StartGateResult.NoHunter, StartGateResult.NotATimedHabit -> completionCenter.message(UserMessage.HabitNotAllowed)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Starting the habit session failed")
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

    /** Runs a habit action; a completion goes to the reward host, a refusal says so, nothing else needs saying. */
    private fun habitAction(action: suspend () -> HabitActionResult) {
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is HabitActionResult.Completed -> completionCenter.completed(result.outcome)
                    HabitActionResult.NotAllowed, HabitActionResult.NotFound -> completionCenter.message(UserMessage.HabitNotAllowed)
                    is HabitActionResult.Progressed, HabitActionResult.Done -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Habit action failed")
                completionCenter.message(UserMessage.Error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun TodayModel.toUiState(): TodayUiState {
    val directive = quests.filter { it.type == QuestType.MAIN || it.type == QuestType.SIDE }
    val pathQuests = quests.filter { it.type == QuestType.PATH }
    val habits = quests.filter { it.type == QuestType.HABIT }
    val counted = directive + pathQuests + habits
    return TodayUiState(
        loading = false,
        header = HunterHeaderModel(
            name = hunter.name,
            level = hunter.level,
            rank = hunter.rank,
            xpIntoLevel = hunter.xpIntoLevel,
            xpForLevel = hunter.xpForLevel,
            streak = hunter.streak,
            gold = hunter.gold,
        ),
        rank = hunter.rank,
        penalty = quests.firstOrNull { it.type == QuestType.PENALTY }?.toRowModel(),
        main = directive.firstOrNull { it.type == QuestType.MAIN }?.let { it.toRowModel(pathBadges[it.id]) },
        sides = directive.filter { it.type == QuestType.SIDE }.map { it.toRowModel() },
        paths = pathQuests.map { it.toRowModel(pathBadges[it.id]) },
        habits = habits.map { it.toRowModel(habit = habitInfo[it.id]?.let { info -> it.toHabitRow(info, currentBlock) }) },
        habitIdByQuest = habits.mapNotNull { quest -> quest.habitId?.let { quest.id to it } }.toMap(),
        completed = counted.count { it.status == QuestStatus.COMPLETED },
        // A skipped habit is neither done nor owed today (GD §16.2).
        total = counted.count { it.status != QuestStatus.SKIPPED },
        cleared = hunter.todayCleared,
        currentBlock = currentBlock,
        gameDate = hunter.gameDate,
        missLine = missLine,
        reviewOpen = reviewOpen,
        trial = trial,
        trialStages = quests.filter { it.type == QuestType.TRIAL }.map { it.toRowModel() },
        discipline = quests.firstOrNull { it.type == QuestType.DISCIPLINE }?.let { quest ->
            DisciplineCard(
                held = quest.status != QuestStatus.EXPIRED,
                xpAtStake = XpCalculator.questXp(quest.difficulty, hunter.streak, VerificationSource.USAGE),
            )
        },
    )
}

/** The habit part of a row (GD §16.1-16.2). Kept-it opens with the Evening block, which Today re-derives, never on a timer. */
internal fun QuestInstanceEntity.toHabitRow(info: HabitTodayInfo, currentBlock: TimeOfDay) = HabitRowModel(
    type = info.type,
    progress = progress,
    weekDone = info.weekDone.takeIf { info.timesPerWeek != null },
    weekTarget = info.timesPerWeek,
    skipped = status == QuestStatus.SKIPPED,
    slipped = info.type == HabitType.NEGATIVE && status == QuestStatus.MISSED,
    keptWindowOpen = currentBlock == TimeOfDay.EVENING,
)

internal fun QuestInstanceEntity.toRowModel(badge: PathBadgeInfo? = null, habit: HabitRowModel? = null) = QuestRowModel(
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
        QuestStatus.EXPIRED, QuestStatus.MISSED, QuestStatus.REROLLED, QuestStatus.REPORTED, QuestStatus.SKIPPED -> QuestRowState.CLOSED
    },
    pathBadge = badge?.let { PathBadgeModel(it.pathName, it.gateRank) },
    progress = if (SetCounter.appliesTo(unit, verification) && status in OPEN_STATUSES) SetCounter.total(SetCounter.parse(setReps)).takeIf { it > 0 } else null,
    timeOfDay = timeOfDay,
    habit = habit,
)

private val OPEN_STATUSES = setOf(QuestStatus.ACTIVE, QuestStatus.STARTED)
