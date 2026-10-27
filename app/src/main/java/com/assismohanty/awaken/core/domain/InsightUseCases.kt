package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.InsightDao
import com.assismohanty.awaken.core.data.db.entity.HealthDayCacheEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthReadException
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.engine.Consistency
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.GameWeek
import com.assismohanty.awaken.engine.HealthRules
import com.assismohanty.awaken.engine.HealthWindow
import com.assismohanty.awaken.engine.insight.BlockTally
import com.assismohanty.awaken.engine.insight.GateStart
import com.assismohanty.awaken.engine.insight.Insight
import com.assismohanty.awaken.engine.insight.InsightDay
import com.assismohanty.awaken.engine.insight.InsightEngine
import com.assismohanty.awaken.engine.insight.InsightHistory
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale
import javax.inject.Inject
import timber.log.Timber

/** What Status shows in INSIGHTS (GD §14.3): this week's fixed set, or how many more days are needed. */
data class InsightsModel(
    val insights: List<Insight> = emptyList(),
    /** > 0: "{n} more days and your patterns will show." 0 with no insights: "No clear patterns yet." */
    val daysNeeded: Int = InsightEngine.MIN_DAYS,
)

/**
 * Fills the Health cache for finalized days of the insight window that aren't cached yet (GD §14.3 I1/I5). Health
 * Connect is read in the foreground only (Phase 11), so Status calls this when it opens. Does nothing with Health off.
 */
class RefreshHealthCacheUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val insightDao: InsightDao,
    private val gateway: HealthGateway,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val profile = hunterDao.getProfile() ?: return
        if (!profile.healthEnabled) return
        if (gateway.status() != HealthStatus.AVAILABLE || !gateway.grantedPermissions().containsAll(gateway.requiredPermissions)) return
        val today = clock.gameDate(profile.dayStartHour)
        val from = maxOf(today.minusDays(InsightEngine.WINDOW_DAYS + GameWeek.DAYS_PER_WEEK * 2), profile.firstGameDate)
        val cached = insightDao.healthDays(from, today).map { it.gameDate }.toSet()
        val missing = generateSequence(from) { it.plusDays(1) }.takeWhile { it < today }.filter { it !in cached }.toList()
        if (missing.isEmpty()) return
        val zone = clock.zone()
        val rows = ArrayList<HealthDayCacheEntity>()
        try {
            for (date in missing) {
                val window = HealthWindow(
                    GameDay.startOf(date, zone, profile.dayStartHour),
                    GameDay.endOf(date, zone, profile.dayStartHour),
                )
                val day = HealthRules.trusted(gateway.readDay(window))
                rows += HealthDayCacheEntity(
                    gameDate = date,
                    steps = day.steps.takeIf { it > 0 },
                    sleepMinutes = HealthRules.longestSleepMinutes(day, window),
                    distanceMeters = day.distanceMeters.takeIf { it > 0 },
                    readAt = clock.now(),
                )
            }
        } catch (e: HealthReadException) {
            // Keep what was read; the rest is tried the next time Status opens.
            Timber.w(e, "Health cache refresh stopped after %d days", rows.size)
        }
        if (rows.isNotEmpty()) insightDao.upsertHealthDays(rows)
    }
}

/**
 * Builds the insight history from Room and picks this week's set (GD §14.3): `Q(W)` from history up to the last
 * game-day of last week, rotated, with last week's base `S0(W−1)` recomputed from history up to the end of `W−2`.
 */
class LoadInsightsUseCase @Inject constructor(
    private val insightDao: InsightDao,
    private val clock: Clock,
) {
    suspend operator fun invoke(profile: HunterProfileEntity, today: LocalDate): InsightsModel {
        val monday = GameWeek.of(today).monday
        val asOf = monday.minusDays(1)
        val previousAsOf = asOf.minusDays(GameWeek.DAYS_PER_WEEK)
        val history = history(profile, from = previousAsOf.minusDays(InsightEngine.WINDOW_DAYS - 1), to = today.minusDays(1))

        val current = InsightEngine.evaluate(history, asOf)
        val previous = InsightEngine.evaluate(history, previousAsOf)
        val previousBase = InsightEngine.base(profile.hunterId, weekKey(monday.minusDays(GameWeek.DAYS_PER_WEEK)), previous.insights)
        val selected = InsightEngine.select(profile.hunterId, weekKey(monday), current.insights, previousBase)
        // The empty state counts every analysable day so far, not only those before this week.
        val soFar = InsightEngine.evaluate(history, today.minusDays(1))
        return InsightsModel(insights = selected, daysNeeded = soFar.daysNeeded)
    }

    /** Analysable days in `[from, to]` with their Health, block, gate and trial data (also used for recap insights). */
    suspend fun history(profile: HunterProfileEntity, from: LocalDate, to: LocalDate): InsightHistory {
        val counts = insightDao.completionCounts(from, to).associate { it.gameDate to it.total.toInt() }
        val health = if (profile.healthEnabled) insightDao.healthDays(from, to).associateBy { it.gameDate } else emptyMap()
        val blocks = insightDao.blockTallies(from, to).groupBy { it.gameDate }
        val days = insightDao.outcomes(from, to)
            .filter { it.gameDate >= profile.firstGameDate && !Consistency.isExcluded(it.outcome) }
            .map { (date, _) ->
                val cache = health[date]
                InsightDay(
                    date = date,
                    completions = counts[date] ?: 0,
                    sleepMinutes = cache?.sleepMinutes,
                    steps = cache?.steps,
                    blocks = blocks[date].orEmpty().associate { it.timeOfDay to BlockTally(it.offered, it.completed) },
                )
            }
        val zone = clock.zone()
        return InsightHistory(
            days = days,
            clearedGates = insightDao.clearedGateStarts(from, to).map { GateStart(it.gameDate, it.startedAt.atZone(zone).hour) },
            trialStageClears = insightDao.trialStageDates(from, to).toSet(),
        )
    }

    /** "2026-W44": the ISO week of [monday], the rotation seed (GD §14.3). */
    private fun weekKey(monday: LocalDate): String =
        String.format(Locale.ROOT, "%d-W%02d", monday.get(IsoFields.WEEK_BASED_YEAR), monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
}
