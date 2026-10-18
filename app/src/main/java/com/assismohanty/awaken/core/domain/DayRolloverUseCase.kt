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
            finalized += finalizeDay(date)
            date = date.plusDays(1)
        }

        val recalibration = recalibrateIfDue(today)
        generateDirective(today)
        val brokenDay = finalized.lastOrNull { it.outcome == DayOutcome.BROKEN }?.gameDate
        val penaltyIssued = brokenDay != null && issuePenalty(profile, today, brokenDay)
        return RolloverOutcome(today, finalized, penaltyIssued, recalibration)
    }

    private suspend fun finalizeDay(date: LocalDate): DayResult = transactions.run {
        val existing = dayRecordDao.get(date)
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
