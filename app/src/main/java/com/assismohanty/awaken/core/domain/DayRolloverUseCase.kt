package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.StatTierEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.DayResult
import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.QuestOutcome
import com.assismohanty.awaken.engine.StreakRules
import com.assismohanty.awaken.engine.StreakState
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import timber.log.Timber

data class RolloverOutcome(
    val today: LocalDate,
    val finalized: List<DayResult>,
    val penaltyIssued: Boolean,
    /** Non-empty when this run recalibrated tiers (GD §6.4) and something changed. */
    val recalibration: Map<Stat, DifficultyAdapter.Change>,
    /** The rank whose trial this run found expired (GD §5), if any. */
    val trialFailed: Rank? = null,
)

/**
 * Day rollover (ARCH §3.2): finalizes every unfinalized game-day before today (catch-up safe, one transaction per day),
 * then recalibrates tiers when due, generates today's directive and issues at most one penalty quest.
 */
class DayRolloverUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val dayRecordDao: DayRecordDao,
    private val ledger: LedgerRepository,
    private val reader: GameStateReader,
    private val templates: TemplateRepository,
    private val generateDirective: GenerateDirectiveUseCase,
    private val advanceTrial: AdvanceTrialUseCase,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    /** Returns null when there is no hunter yet. */
    suspend operator fun invoke(): RolloverOutcome? {
        val profile = hunterDao.getProfile() ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val lastFinalized = dayRecordDao.lastFinalizedDate()
        if (lastFinalized != null && today <= lastFinalized) {
            // Clock moved backwards: never un-finalize (ARCH §3.2).
            Timber.w("Rollover skipped: today %s is not after last finalized day %s", today, lastFinalized)
            return RolloverOutcome(today, emptyList(), penaltyIssued = false, recalibration = emptyMap())
        }

        val finalized = ArrayList<DayResult>()
        var date = maxOf(lastFinalized?.plusDays(1) ?: profile.firstGameDate, profile.firstGameDate)
        while (date < today) {
            carryRecovery(date)
            finalized += finalizeDay(date)
            date = date.plusDays(1)
        }
        // Only the run that crosses into a new day carries Recovery into it, so turning it off today sticks.
        if (finalized.isNotEmpty()) carryRecovery(today)

        val recalibration = recalibrateIfDue(today)
        generateDirective(today)
        val brokenDay = finalized.lastOrNull { it.outcome == DayOutcome.BROKEN }?.gameDate
        val penaltyIssued = brokenDay != null && issuePenalty(profile, today, brokenDay)
        val trialFailed = advanceTrial(profile, today)
        return RolloverOutcome(today, finalized, penaltyIssued, recalibration, trialFailed)
    }

    /** Recovery Mode carries from the previous day while budget remains (ENGINE_DECISIONS #33). */
    private suspend fun carryRecovery(date: LocalDate) = transactions.run {
        if (dayRecordDao.get(date.minusDays(1))?.recovery != true) return@run
        val existing = dayRecordDao.get(date)
        if (existing?.finalizedAt != null || existing?.recovery == true) return@run
        val month = YearMonth.from(date)
        val used = StreakRules.recoveryUsed(dayRecordDao.finalizedRecoveryDays(month.atDay(1), date.minusDays(1)))
        if (StreakRules.recoveryContinues(previousDayRecovery = true, usedThisMonth = used)) {
            dayRecordDao.upsert((existing ?: DayRecordEntity(gameDate = date)).copy(recovery = true))
        }
    }

    /** Finalizes one day. Re-entrant: an already-finalized day is returned as recorded, never re-evaluated. */
    internal suspend fun finalizeDay(date: LocalDate): DayResult = transactions.run {
        val existing = dayRecordDao.get(date)
        val recordedOutcome = existing?.outcome
        if (existing?.finalizedAt != null && recordedOutcome != null) {
            // A concurrent rollover (app start + worker) already did this day; re-running would spend state twice.
            val (streakAfter, _) = StreakRules.replay(dayRecordDao.finalizedOutcomesThrough(date))
            return@run DayResult(date, recordedOutcome, streakAfter)
        }
        questDao.closeOpen(date)
        val record = questDao.getForDate(date)
            .toDayRecord(date, restDay = existing?.restDay ?: false, recovery = existing?.recovery ?: false)

        val streak = reader.finalizedStreak()
        val month = YearMonth.from(date)
        val state = StreakState(
            streak = streak.streak,
            bestStreak = streak.bestStreak,
            shields = ledger.balances().item(LedgerRepository.STREAK_SHIELD_ITEM_ID).toInt(),
            recoveryDaysUsed = mapOf(
                month to StreakRules.recoveryUsed(dayRecordDao.finalizedRecoveryDays(month.atDay(1), month.atEndOfMonth())),
            ),
        )
        val (_, result) = StreakRules.finalizeDay(state, record)
        val now = clock.now()
        ledger.insertIdempotent(rowsFor(result, date, now))
        dayRecordDao.upsert(
            DayRecordEntity(
                gameDate = date,
                cleared = StreakRules.isCleared(record),
                restDay = record.restDay,
                recovery = record.recovery,
                outcome = result.outcome,
                shieldUsed = result.shieldUsed,
                penaltyIssued = existing?.penaltyIssued ?: false,
                finalizedAt = now,
            ),
        )
        result
    }

    private fun rowsFor(result: DayResult, date: LocalDate, now: Instant): List<LedgerEntryEntity> = when (result.outcome) {
        DayOutcome.CLEARED -> listOf(
            LedgerEntryEntity(
                kind = LedgerKind.GOLD,
                delta = StreakRules.DAY_CLEAR_GOLD.toLong(),
                sourceType = SourceType.DAY_CLEAR,
                sourceId = date.toString(),
                gameDate = date,
                createdAt = now,
            ),
        )
        DayOutcome.SHIELDED -> listOf(
            LedgerEntryEntity(
                kind = LedgerKind.ITEM,
                itemId = LedgerRepository.STREAK_SHIELD_ITEM_ID,
                delta = -1,
                sourceType = SourceType.SHIELD_USE,
                sourceId = date.toString(),
                gameDate = date,
                createdAt = now,
            ),
        )
        DayOutcome.REST, DayOutcome.RECOVERY, DayOutcome.BROKEN -> emptyList()
    }

    private suspend fun recalibrateIfDue(today: LocalDate): Map<Stat, DifficultyAdapter.Change> = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run emptyMap()
        if (!DifficultyAdapter.isDue(today, profile.lastRecalibratedOn)) return@run emptyMap()
        val monday = DifficultyAdapter.recalibrationDate(today)
        val outcomes = questDao.generatedBetween(monday.minusDays(DifficultyAdapter.WINDOW_DAYS), monday.minusDays(1))
            .map { QuestOutcome(it.gameDate, it.type, it.stat, it.status) }
        val current = hunterDao.getTiers().associate { it.stat to it.tier }
        val result = DifficultyAdapter.recalibrate(current, outcomes, today)
        hunterDao.upsertTiers(result.tiers.map { (stat, tier) -> StatTierEntity(stat, tier) })
        hunterDao.upsertProfile(profile.copy(lastRecalibratedOn = today))
        result.changes.filterValues { it != DifficultyAdapter.Change.NONE }
    }

    /** One penalty quest for today after a failed day (GD §7.3); its id is deterministic, so it is never duplicated. */
    private suspend fun issuePenalty(profile: HunterProfileEntity, today: LocalDate, brokenDay: LocalDate): Boolean {
        val library = templates.library()
        return transactions.run {
            val context = reader.hunterContext(profile, today)
            val draft = DirectiveGenerator.penaltyQuest(library.templates, context, today) ?: return@run false
            val inserted = questDao.insertAll(listOf(draft.toEntity(clock.now()))).any { it != -1L }
            dayRecordDao.get(brokenDay)?.let { dayRecordDao.upsert(it.copy(penaltyIssued = true)) }
            inserted
        }
    }
}
