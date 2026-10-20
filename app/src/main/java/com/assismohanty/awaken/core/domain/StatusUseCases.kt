package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.db.dao.AchievementDao
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.StatTotal
import com.assismohanty.awaken.core.data.repo.Chapter
import com.assismohanty.awaken.core.data.repo.NarrativeRepository
import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.GameWeek
import com.assismohanty.awaken.engine.QuestOutcome
import com.assismohanty.awaken.engine.StatLevel
import com.assismohanty.awaken.engine.StatusMath
import com.assismohanty.awaken.engine.WeeklyReport
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class StatProgress(val stat: Stat, val level: Int, val xp: Long, val xpIntoLevel: Long, val xpForLevel: Long)

data class AchievementEntry(val id: String, val gold: Int, val titleId: String?, val unlocked: Boolean)

data class ChapterEntry(val chapter: Chapter, val unlocked: Boolean)

data class HeatmapEntry(val date: LocalDate, val completions: Int, val intensity: Int, val restDay: Boolean)

/** Everything Status shows (PRD F12, F16), derived from rows; the screen does no game math. */
data class StatusModel(
    val hunter: HunterState,
    val stats: List<StatProgress>,
    /** Stat levels from ledger rows dated before this week's Monday (the dashed radar polygon). */
    val lastWeekLevels: Map<Stat, Int>,
    val radarMax: Int,
    val heatmap: List<HeatmapEntry>,
    val achievements: List<AchievementEntry>,
    val chapters: List<ChapterEntry>,
    val weeklyReport: WeeklyReport,
    /** What recalibration would do if the week ended now (GD §6.4): only changed stats. */
    val projectedRecalibration: Map<Stat, DifficultyAdapter.Change>,
)

private data class WeekRows(
    val statXpBefore: List<StatTotal>,
    val statXpWeek: List<StatTotal>,
    val xpPerDay: Map<LocalDate, Long>,
    val completionsPerDay: Map<LocalDate, Int>,
    val restDays: Set<LocalDate>,
    val unlocked: Set<String>,
)

class ObserveStatusUseCase @Inject constructor(
    private val observeHunterState: ObserveHunterStateUseCase,
    private val ledgerDao: LedgerDao,
    private val completionDao: CompletionDao,
    private val dayRecordDao: DayRecordDao,
    private val achievementDao: AchievementDao,
    private val questDao: QuestDao,
    private val hunterDao: HunterDao,
    private val narrative: NarrativeRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any> = flowOf(Unit)): Flow<StatusModel?> {
        val hunters = observeHunterState(dayChanges)
        val rows = hunters.map { it?.gameDate }.distinctUntilChanged().flatMapLatest { today ->
            if (today == null) flowOf(null) else observeRows(today)
        }
        return combine(hunters, rows) { hunter, week ->
            if (hunter == null || week == null) null else build(hunter, week)
        }
    }

    private fun observeRows(today: LocalDate): Flow<WeekRows> {
        val week = GameWeek.of(today)
        val heatmapStart = StatusMath.heatmapStart(today)
        val ledgerRows = combine(
            ledgerDao.observeStatXpBefore(week.monday),
            ledgerDao.observeStatXpBetween(week.monday, week.sunday),
            ledgerDao.observeXpPerDay(week.monday, week.sunday),
        ) { before, inWeek, perDay -> Triple(before, inWeek, perDay) }
        return combine(
            ledgerRows,
            completionDao.observeCountsPerDay(heatmapStart, week.sunday),
            dayRecordDao.observeRestDays(heatmapStart, week.sunday),
            achievementDao.observeUnlockedIds(),
        ) { (before, inWeek, perDay), completions, rest, unlocked ->
            WeekRows(
                statXpBefore = before,
                statXpWeek = inWeek,
                xpPerDay = perDay.associate { it.gameDate to it.total },
                completionsPerDay = completions.associate { it.gameDate to it.total.toInt() },
                restDays = rest.toSet(),
                unlocked = unlocked.toSet(),
            )
        }
    }

    private suspend fun build(hunter: HunterState, rows: WeekRows): StatusModel {
        val today = hunter.gameDate
        val week = GameWeek.of(today)
        val lastWeekLevels = rows.statXpBefore.byStat().mapValues { (_, xp) -> StatLevel.level(xp) }
        val heatmapStart = StatusMath.heatmapStart(today)
        val heatmap = generateSequence(heatmapStart) { it.plusDays(1) }
            .takeWhile { it <= week.sunday }
            .map { date ->
                val count = rows.completionsPerDay[date] ?: 0
                HeatmapEntry(date, count, StatusMath.heatmapIntensity(count), date in rows.restDays)
            }
            .toList()
        val weekCompletions = rows.completionsPerDay.filterKeys { it in week }.values.sum()
        return StatusModel(
            hunter = hunter,
            stats = Stat.entries.map { stat ->
                val xp = hunter.statXp[stat] ?: 0L
                val level = StatLevel.level(xp)
                val floor = StatLevel.xpForLevel(level)
                StatProgress(stat, level, xp, xp - floor, StatLevel.xpForLevel(level + 1) - floor)
            },
            lastWeekLevels = lastWeekLevels,
            radarMax = StatusMath.radarMax(hunter.statLevels.values + lastWeekLevels.values),
            heatmap = heatmap,
            achievements = AchievementEvaluator.all.map { AchievementEntry(it.id, it.gold, it.titleId, it.id in rows.unlocked) },
            chapters = narrative.chapters().map { ChapterEntry(it, hunter.level >= it.unlockLevel) },
            weeklyReport = StatusMath.weeklyReport(week, rows.statXpWeek.byStat(), rows.xpPerDay, weekCompletions),
            projectedRecalibration = projectRecalibration(week),
        )
    }

    private suspend fun projectRecalibration(week: GameWeek): Map<Stat, DifficultyAdapter.Change> {
        val outcomes = questDao.generatedBetween(week.monday, week.sunday)
            .map { QuestOutcome(it.gameDate, it.type, it.stat, it.status, it.pathSlotId) }
        val current = hunterDao.getTiers().associate { it.stat to it.tier }
        val nextMonday = week.monday.plusDays(GameWeek.DAYS_PER_WEEK)
        return DifficultyAdapter.recalibrate(current, outcomes, nextMonday).changes
            .filterValues { it != DifficultyAdapter.Change.NONE }
    }

    private fun List<StatTotal>.byStat(): Map<Stat, Long> {
        val totals = associate { it.stat to it.total }
        return Stat.entries.associateWith { totals[it.name] ?: 0L }
    }
}

sealed interface EquipTitleResult {
    data object Equipped : EquipTitleResult
    data object Unequipped : EquipTitleResult
    /** The title's achievement isn't unlocked, or the id is unknown. */
    data object Locked : EquipTitleResult
}

/** Equips a title earned from an unlocked achievement (GD §10); null unequips. Profile state, not the ledger. */
class EquipTitleUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val achievementDao: AchievementDao,
) {
    suspend operator fun invoke(titleId: String?): EquipTitleResult {
        if (titleId == null) {
            hunterDao.setEquippedTitle(null)
            return EquipTitleResult.Unequipped
        }
        val achievement = AchievementEvaluator.all.firstOrNull { it.titleId == titleId } ?: return EquipTitleResult.Locked
        if (achievement.id !in achievementDao.unlockedIds()) return EquipTitleResult.Locked
        hunterDao.setEquippedTitle(titleId)
        return EquipTitleResult.Equipped
    }
}
