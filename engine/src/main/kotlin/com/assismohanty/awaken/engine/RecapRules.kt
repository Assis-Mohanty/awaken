package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Stat
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** A recap's period of game-dates (GD §14.6); [key] is "M:2026-10" or "Y:2026". */
data class RecapPeriod(val key: String, val start: LocalDate, val end: LocalDate, val yearly: Boolean) {
    operator fun contains(date: LocalDate): Boolean = date in start..end
}

/** One day of Health data for a recap; null fields mean no data that day. */
data class RecapHealthDay(val distanceMeters: Double? = null, val steps: Long? = null, val sleepMinutes: Int? = null)

/**
 * Everything a recap is built from, all derived from Room by the app.
 * @param outcomesThroughEnd every finalized outcome from the first game-day to the period end, in date order (best streak
 *   needs the run-up before the period).
 * @param health days with Health data in the period; empty when Health is off, and the body card is left out.
 */
data class RecapInput(
    val period: RecapPeriod,
    val questsCompleted: Int,
    val clearedGateSeconds: Long,
    val outcomesThroughEnd: List<Pair<LocalDate, DayOutcome>>,
    val statXpBefore: Map<Stat, Long>,
    val statXpGained: Map<Stat, Long>,
    val xpBefore: Long,
    val xpGained: Long,
    val health: List<RecapHealthDay>,
    val achievementsEarned: Int,
)

/**
 * The stored recap snapshot (GD §14.6): a cache, rebuildable from Room. A field with no data is null and its card row is
 * left out, never shown as 0. The insight is not stored: it is re-derived from the period when the recap opens.
 */
@Serializable
data class RecapPayload(
    val periodKey: String,
    val yearly: Boolean,
    val start: String,
    val end: String,
    val questsCompleted: Int,
    val levelsGained: Int,
    val gateHours: Double? = null,
    val bestStreak: Int? = null,
    val consistencyCleared: Int,
    val consistencyEligible: Int,
    val topStat: Stat? = null,
    val topStatGrowth: Int? = null,
    val distanceKm: Double? = null,
    val averageSteps: Long? = null,
    val averageSleepHours: Double? = null,
    val achievementsEarned: Int,
) {
    val hasBody: Boolean get() = distanceKm != null || averageSteps != null || averageSleepHours != null
}

/** Monthly and yearly recaps (GD §14.6). They grant nothing. */
object RecapRules {
    const val PAYLOAD_VERSION = 1
    const val MIN_MONTH_DAYS = 7
    const val MIN_YEAR_DAYS = 30
    /** The yearly recap is auto-offered on game-days Jan 1–14. */
    const val YEARLY_OFFER_LAST_DAY = 14
    private const val SECONDS_PER_HOUR = 3_600L
    private const val METERS_PER_KM = 1_000.0
    private const val MINUTES_PER_HOUR = 60.0

    /** The previous calendar month of game-dates. Only the immediately previous month is ever generated. */
    fun previousMonth(today: LocalDate): RecapPeriod {
        val month = YearMonth.from(today).minusMonths(1)
        return RecapPeriod("M:$month", month.atDay(1), month.atEndOfMonth(), yearly = false)
    }

    /** The previous year while game-days Jan 1–14 last; null otherwise. */
    fun previousYear(today: LocalDate): RecapPeriod? {
        if (today.monthValue != 1 || today.dayOfMonth > YEARLY_OFFER_LAST_DAY) return null
        val year = today.year - 1
        return RecapPeriod("Y:$year", LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), yearly = true)
    }

    /**
     * Periods to generate now, yearly first (when both are due the yearly one opens and the month waits in the list).
     * [finalizedDays] counts finalized game-days in a period; [existing] are keys already stored.
     */
    fun due(today: LocalDate, existing: Set<String>, finalizedDays: (RecapPeriod) -> Int): List<RecapPeriod> = buildList {
        previousYear(today)?.takeIf { it.key !in existing && finalizedDays(it) >= MIN_YEAR_DAYS }?.let(::add)
        previousMonth(today).takeIf { it.key !in existing && finalizedDays(it) >= MIN_MONTH_DAYS }?.let(::add)
    }

    fun build(input: RecapInput): RecapPayload {
        val period = input.period
        val inPeriod = input.outcomesThroughEnd.filter { (date, _) -> date in period }.map { it.second }
        val consistency = Consistency.over(inPeriod)
        val top = input.statXpGained.filterValues { it > 0 }.entries
            .maxWithOrNull(compareBy<Map.Entry<Stat, Long>> { it.value }.thenByDescending { it.key.ordinal })
        val distance = input.health.mapNotNull { it.distanceMeters }.takeIf { it.isNotEmpty() }?.sum()
        val steps = input.health.mapNotNull { it.steps?.takeIf { s -> s > 0 } }
        val sleep = input.health.mapNotNull { it.sleepMinutes }
        return RecapPayload(
            periodKey = period.key,
            yearly = period.yearly,
            start = period.start.toString(),
            end = period.end.toString(),
            questsCompleted = input.questsCompleted,
            levelsGained = LevelCurve.level(input.xpBefore + input.xpGained) - LevelCurve.level(input.xpBefore),
            gateHours = if (input.clearedGateSeconds > 0) oneDecimal(input.clearedGateSeconds.toDouble() / SECONDS_PER_HOUR) else null,
            bestStreak = bestStreakIn(input.outcomesThroughEnd, period).takeIf { it > 0 },
            consistencyCleared = consistency.cleared,
            consistencyEligible = consistency.eligible,
            topStat = top?.key,
            topStatGrowth = top?.let { (stat, gained) ->
                val before = input.statXpBefore[stat] ?: 0L
                StatLevel.level(before + gained) - StatLevel.level(before)
            },
            distanceKm = distance?.let { oneDecimal(it / METERS_PER_KM) },
            averageSteps = if (steps.isEmpty()) null else steps.sum() / steps.size,
            averageSleepHours = if (sleep.isEmpty()) null else oneDecimal(sleep.average() / MINUTES_PER_HOUR),
            achievementsEarned = input.achievementsEarned,
        )
    }

    /** The highest streak reached on a day in [period], replaying every outcome before it (`StreakRules.replay` rules). */
    private fun bestStreakIn(outcomes: List<Pair<LocalDate, DayOutcome>>, period: RecapPeriod): Int {
        var streak = 0
        var best = 0
        for ((date, outcome) in outcomes) {
            if (date > period.end) break
            when (outcome) {
                DayOutcome.CLEARED -> streak++
                DayOutcome.REST, DayOutcome.RECOVERY, DayOutcome.SHIELDED -> Unit
                DayOutcome.BROKEN -> streak = 0
            }
            if (date in period) best = maxOf(best, streak)
        }
        return best
    }

    private fun oneDecimal(value: Double): Double = BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
}
