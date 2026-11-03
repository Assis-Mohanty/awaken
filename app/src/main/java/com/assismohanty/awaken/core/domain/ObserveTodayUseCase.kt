package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.dao.WeeklyReviewDao
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.engine.Consistency
import com.assismohanty.awaken.engine.ConsistencyScore
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.HabitRules
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.MissLine
import com.assismohanty.awaken.engine.TimeBlocks
import com.assismohanty.awaken.engine.model.TimeOfDay
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.WeeklyReviewRules
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

/** The Rank Trial slot on Today (GD §5). */
sealed interface TrialSummary {
    /** The level reached the next band and no cooldown applies: the trial can begin. */
    data class Available(val rank: Rank) : TrialSummary
    data class Active(val rank: Rank, val cleared: Int, val stages: Int, val daysLeft: Int) : TrialSummary
}

/** What a habit's row needs that the quest instance does not hold (GD §16.1): its type and, for a flexible habit, the week's tally. */
data class HabitTodayInfo(val type: HabitType, val timesPerWeek: Int?, val weekDone: Int)

/** Where a Path quest came from; [gateRank] is set for GATE-mode quests. */
data class PathBadgeInfo(val pathName: String, val gateRank: GateRank?)

/** Everything the Today screen shows (DESIGN_SYSTEM §4.2). Rerolled-away and reported quests are already filtered out. */
data class TodayModel(
    val hunter: HunterState,
    val quests: List<QuestInstanceEntity>,
    val trial: TrialSummary? = null,
    /** Path name and gate per quest id, for the Path badge on rows (DS §3 PathQuestBadge). */
    val pathBadges: Map<String, PathBadgeInfo> = emptyMap(),
    /** Per habit quest id: its type and weekly tally (GD §16.1). */
    val habitInfo: Map<String, HabitTodayInfo> = emptyMap(),
    /**
     * The time-of-day block "now" falls in (GD §14.5), from the injected clock. Re-derived on every emission (quest
     * changes, foreground, rollover) and when the block boundaries change, not on a timer.
     */
    val currentBlock: TimeOfDay = TimeOfDay.MORNING,
    /** Cleared of eligible days over the last 28 finalized days (GD §14.2). */
    val consistency: ConsistencyScore = ConsistencyScore(0, 0),
    /** The calm line the day after a missed day (GD §14.2). */
    val missLine: MissLine? = null,
    /** The weekly review is open and not yet done (GD §14.4: Saturday to Monday). */
    val reviewOpen: Boolean = false,
)

private data class TodayInputs(
    val quests: List<QuestInstanceEntity>,
    val bounds: TimeBlocks.Bounds,
    val outcomes: Map<LocalDate, DayOutcome>,
    val reviewOpen: Boolean,
)

internal val QuestInstanceEntity.isOpen: Boolean get() = status == QuestStatus.ACTIVE || status == QuestStatus.STARTED

class ObserveTodayUseCase @Inject constructor(
    private val observeHunterState: ObserveHunterStateUseCase,
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val reader: GameStateReader,
    private val trialDao: TrialDao,
    private val pathDao: PathDao,
    private val habitDao: HabitDao,
    private val trialReader: TrialReader,
    private val clock: Clock,
    private val uiPrefs: UiPrefsRepository,
    private val dayRecordDao: DayRecordDao,
    private val weeklyReviewDao: WeeklyReviewDao,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any>): Flow<TodayModel?> =
        observeHunterState(dayChanges).flatMapLatest { hunter ->
            if (hunter == null) return@flatMapLatest flowOf(null)
            val today = hunter.gameDate
            combine(
                questDao.observeForDate(today),
                uiPrefs.prefs,
                dayRecordDao.observeDatedOutcomesFrom(today.minusDays(Consistency.WINDOW_DAYS)),
                weeklyReviewDao.observeSubmittedWeeks(),
            ) { all, prefs, outcomes, reviewed ->
                val dated = outcomes.associate { it.gameDate to it.outcome }
                TodayInputs(all, prefs.blockBounds, dated, WeeklyReviewRules.isOpen(today, reviewed.toSet()))
            }
                .mapLatest { (all, bounds, outcomes, reviewOpen) ->
                    val quests = all.filter { !it.status.isHidden }
                    val profile = hunterDao.getProfile()
                    val dayStart = profile?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR
                    val block = TimeBlocks.blockAt(clock.now().atZone(clock.zone()).toLocalTime(), dayStart, bounds)
                    TodayModel(
                        hunter = hunter,
                        quests = quests,
                        trial = trialSummary(hunter),
                        pathBadges = pathBadges(quests),
                        habitInfo = habitInfo(quests, today),
                        currentBlock = block,
                        consistency = Consistency.score(outcomes, today, profile?.firstGameDate ?: today),
                        missLine = Consistency.missLine(outcomes[today.minusDays(1)], outcomes[today.minusDays(2)]),
                        reviewOpen = reviewOpen,
                    )
                }
        }

    private suspend fun trialSummary(hunter: HunterState): TrialSummary? {
        val active = trialDao.active()
        if (active != null) {
            val progress = trialReader.progress(active, hunter.gameDate)
            val daysLeft = ChronoUnit.DAYS.between(hunter.gameDate, active.expiresGameDate).toInt() + 1
            return TrialSummary.Active(active.targetRank, progress.cleared.size, RankRules.TRIAL_STAGES, daysLeft.coerceAtLeast(0))
        }
        return RankRules.trialAvailable(
            level = hunter.level,
            passedTrials = trialDao.passedRanks().toSet(),
            trialActive = false,
            lastFailedAt = trialDao.lastFailedAt(),
            now = clock.now(),
        )?.let { TrialSummary.Available(it) }
    }

    private suspend fun habitInfo(quests: List<QuestInstanceEntity>, today: LocalDate): Map<String, HabitTodayInfo> = buildMap {
        quests.filter { it.type == QuestType.HABIT }.forEach { quest ->
            val habit = quest.habitId?.let { habitDao.get(it) } ?: return@forEach
            val done = if (habit.frequency == HabitFrequency.TIMES_PER_WEEK) {
                questDao.habitCompletionsBetween(habit.id, HabitRules.weekStart(today), HabitRules.weekEnd(today))
            } else {
                0
            }
            put(quest.id, HabitTodayInfo(habit.type, habit.timesPerWeek.takeIf { habit.frequency == HabitFrequency.TIMES_PER_WEEK }, done))
        }
    }

    private suspend fun pathBadges(quests: List<QuestInstanceEntity>): Map<String, PathBadgeInfo> {
        if (quests.none { it.pathSlotId != null }) return emptyMap()
        val paths = pathDao.all().associateBy { it.id }
        return buildMap {
            quests.forEach { quest ->
                val slot = quest.pathSlotId?.let { pathDao.slot(it) } ?: return@forEach
                val path = paths[slot.pathId] ?: return@forEach
                put(quest.id, PathBadgeInfo(path.goal.take(BADGE_NAME_MAX), slot.gateRank))
            }
        }
    }

    private companion object {
        const val BADGE_NAME_MAX = 28
    }
}
