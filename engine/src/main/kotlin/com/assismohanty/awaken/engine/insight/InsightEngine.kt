package com.assismohanty.awaken.engine.insight

import com.assismohanty.awaken.engine.Fnv1a
import com.assismohanty.awaken.engine.model.TimeOfDay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

/** Quests offered and completed in one time-of-day block on one game-day (GD §14.3 I3). */
data class BlockTally(val offered: Int, val completed: Int)

/**
 * One analysable game-day (GD §14.1): finalized, on or after the first game-day, not an uncleared rest/recovery day.
 * @param completions `q(d)`: active completions of every quest type.
 * @param sleepMinutes longest merged sleep session ending in the game-day; null = no session (or Health off).
 * @param steps null or 0 = no step data.
 */
data class InsightDay(
    val date: LocalDate,
    val completions: Int,
    val sleepMinutes: Int? = null,
    val steps: Long? = null,
    val blocks: Map<TimeOfDay, BlockTally> = emptyMap(),
)

/** A cleared Focus Gate by its local start hour (I4). */
data class GateStart(val date: LocalDate, val hour: Int)

/** Everything [InsightEngine.evaluate] reads. The app passes analysable days only; the engine applies the 84-day window. */
data class InsightHistory(
    val days: List<InsightDay>,
    val clearedGates: List<GateStart> = emptyList(),
    /** Game-days on which a Rank Trial stage was cleared (I6). */
    val trialStageClears: Set<LocalDate> = emptySet(),
)

/** Rule order is the tie order (GD §14.3). */
enum class InsightRule { SLEEP, WEEKDAY, TIME_OF_DAY, FOCUS_HOUR, STEPS, MOMENTUM }

sealed interface Insight {
    val rule: InsightRule
}

/**
 * Two buckets of days (I1 sleep, I5 steps, I6 momentum). [aIsHigher]: bucket A (≥ 7 h, ≥ 7,000 steps, the 3 days after a
 * trial stage) has the higher mean. [percent] is null when the lower mean is 0, and the copy uses absolute numbers.
 */
data class BucketInsight(
    override val rule: InsightRule,
    val aIsHigher: Boolean,
    val higherMean: Double,
    val lowerMean: Double,
    val percent: Int?,
) : Insight

data class WeekdayInsight(
    val best: DayOfWeek,
    val worst: DayOfWeek,
    val bestMean: Double,
    val worstMean: Double,
    val percent: Int?,
) : Insight {
    override val rule: InsightRule get() = InsightRule.WEEKDAY
}

/** Rates are completed / offered in the block, 0..1. */
data class BlockInsight(
    val best: TimeOfDay,
    val worst: TimeOfDay,
    val bestRate: Double,
    val worstRate: Double,
    val percent: Int?,
) : Insight {
    override val rule: InsightRule get() = InsightRule.TIME_OF_DAY
}

data class FocusHourInsight(val hour: Int, val gates: Int, val total: Int) : Insight {
    override val rule: InsightRule get() = InsightRule.FOCUS_HOUR
}

/** [daysNeeded] > 0 means too little history: the empty state "{n} more days and your patterns will show." */
data class InsightResult(val analysableDays: Int, val insights: List<Insight>) {
    val daysNeeded: Int get() = (InsightEngine.MIN_DAYS - analysableDays).coerceAtLeast(0)
}

/** Personal insights (GD §14.3): deterministic, correlational, on-device. Nothing here grants anything. */
object InsightEngine {
    const val MIN_DAYS = 14
    const val WINDOW_DAYS = 84L
    const val MIN_BUCKET_DAYS = 5
    const val VISIBLE = 3
    const val SLEEP_TARGET_MINUTES = 7 * 60
    const val STEPS_TARGET = 7_000L
    const val MOMENTUM_DAYS = 3L
    const val MIN_FOCUS_GATES = 8

    /** 0.5 quests/day; 15 percentage points for the I3 rate; 25 % relative (also the I4 share). */
    private val ABS_QUESTS = Ratio(1, 2)
    private val ABS_RATE = Ratio(15, 100)
    private val REL = Ratio(1, 4)

    /** [windowDays]: 84 for Status and the review; a recap passes its whole period (GD §14.6). */
    fun evaluate(history: InsightHistory, asOf: LocalDate, windowDays: Long = WINDOW_DAYS): InsightResult {
        val from = asOf.minusDays(windowDays - 1)
        val days = history.days.filter { it.date in from..asOf }.distinctBy { it.date }
        if (days.size < MIN_DAYS) return InsightResult(days.size, emptyList())
        val insights = listOfNotNull(
            sleep(days),
            weekday(days),
            timeOfDay(days),
            focusHour(history.clearedGates.filter { it.date in from..asOf }),
            steps(days),
            momentum(days, history.trialStageClears),
        )
        return InsightResult(days.size, insights)
    }

    /** The base selection `S0(W)`: the first 3 of the rotated qualifying list, no look-back. */
    fun base(hunterId: String, weekKey: String, qualifying: List<Insight>): List<Insight> =
        rotate(qualifying, hunterId, weekKey).take(VISIBLE)

    /**
     * `S(W)`: insights not in last week's base first, then the repeats, each rotated, capped at 3. [previousBase] is
     * `S0(W−1)`, recomputed by the caller from history up to the end of `W−2`; nothing is stored.
     */
    fun select(hunterId: String, weekKey: String, qualifying: List<Insight>, previousBase: List<Insight>): List<Insight> {
        val repeated = previousBase.map { it.rule }.toSet()
        val fresh = qualifying.filter { it.rule !in repeated }
        val repeats = qualifying.filter { it.rule in repeated }
        return (rotate(fresh, hunterId, weekKey) + rotate(repeats, hunterId, weekKey)).take(VISIBLE)
    }

    private fun rotate(items: List<Insight>, hunterId: String, weekKey: String): List<Insight> {
        if (items.isEmpty()) return items
        val sorted = items.sortedBy { it.rule.ordinal }
        val shift = Math.floorMod(Fnv1a.hash64("$hunterId|$weekKey|insights"), sorted.size.toLong()).toInt()
        return sorted.drop(shift) + sorted.take(shift)
    }

    // ---- Rules ----

    private fun sleep(days: List<InsightDay>): Insight? {
        val withSleep = days.filter { it.sleepMinutes != null }
        val (a, b) = withSleep.partition { (it.sleepMinutes ?: 0) >= SLEEP_TARGET_MINUTES }
        return buckets(InsightRule.SLEEP, a, b)
    }

    private fun steps(days: List<InsightDay>): Insight? {
        val withSteps = days.filter { (it.steps ?: 0L) > 0L }
        val (a, b) = withSteps.partition { (it.steps ?: 0L) >= STEPS_TARGET }
        return buckets(InsightRule.STEPS, a, b)
    }

    private fun momentum(days: List<InsightDay>, clears: Set<LocalDate>): Insight? {
        val (a, b) = days.partition { day -> (1..MOMENTUM_DAYS).any { day.date.minusDays(it) in clears } }
        return buckets(InsightRule.MOMENTUM, a, b)
    }

    private fun buckets(rule: InsightRule, a: List<InsightDay>, b: List<InsightDay>): Insight? {
        if (a.size < MIN_BUCKET_DAYS || b.size < MIN_BUCKET_DAYS) return null
        val meanA = a.mean()
        val meanB = b.mean()
        val aIsHigher = meanA >= meanB
        val (hi, lo) = if (aIsHigher) meanA to meanB else meanB to meanA
        if (!passes(hi, lo, ABS_QUESTS)) return null
        return BucketInsight(rule, aIsHigher, hi.toDouble(), lo.toDouble(), percent(hi, lo))
    }

    private fun weekday(days: List<InsightDay>): Insight? {
        val means = days.groupBy { it.date.dayOfWeek }
            .filterValues { it.size >= MIN_BUCKET_DAYS }
            .toSortedMap()
            .mapValues { (_, bucket) -> bucket.mean() }
        if (means.size < 2) return null
        // Ties go to the earlier weekday: the map is in ISO order and a strict comparison keeps the first.
        val best = means.entries.reduce { acc, e -> if (e.value > acc.value) e else acc }
        val worst = means.entries.reduce { acc, e -> if (e.value < acc.value) e else acc }
        if (best.key == worst.key || !passes(best.value, worst.value, ABS_QUESTS)) return null
        return WeekdayInsight(best.key, worst.key, best.value.toDouble(), worst.value.toDouble(), percent(best.value, worst.value))
    }

    private fun timeOfDay(days: List<InsightDay>): Insight? {
        val rates = TimeOfDay.entries.mapNotNull { block ->
            val blockDays = days.mapNotNull { it.blocks[block] }.filter { it.offered >= 1 }
            if (blockDays.size < MIN_BUCKET_DAYS) return@mapNotNull null
            block to Ratio(blockDays.sumOf { it.completed }.toLong(), blockDays.sumOf { it.offered }.toLong())
        }
        if (rates.size < 2) return null
        val best = rates.reduce { acc, e -> if (e.second > acc.second) e else acc }
        val worst = rates.reduce { acc, e -> if (e.second < acc.second) e else acc }
        if (best.first == worst.first || !passes(best.second, worst.second, ABS_RATE)) return null
        return BlockInsight(best.first, worst.first, best.second.toDouble(), worst.second.toDouble(), percent(best.second, worst.second))
    }

    private fun focusHour(gates: List<GateStart>): Insight? {
        if (gates.size < MIN_FOCUS_GATES) return null
        val byHour = gates.groupingBy { it.hour }.eachCount()
        val top = byHour.values.max()
        val leaders = byHour.filterValues { it == top }.keys
        if (leaders.size != 1 || Ratio(top.toLong(), gates.size.toLong()) < REL) return null
        return FocusHourInsight(leaders.single(), top, gates.size)
    }

    // ---- Guardrails, exact ----

    private fun List<InsightDay>.mean(): Ratio = Ratio(sumOf { it.completions.toLong() }, size.toLong())

    /** Absolute difference ≥ [absMin] and relative difference `(hi − lo) / lo` ≥ 25 %; `lo = 0` passes the relative test. */
    private fun passes(hi: Ratio, lo: Ratio, absMin: Ratio): Boolean {
        val diff = hi - lo
        if (diff < absMin) return false
        return lo.num == 0L || diff / lo >= REL
    }

    /** `round((hi − lo) / lo × 100)`, half up; null when `lo = 0`. */
    private fun percent(hi: Ratio, lo: Ratio): Int? {
        if (lo.num == 0L) return null
        val rel = (hi - lo) / lo
        return BigDecimal(rel.num * PERCENT).divide(BigDecimal(rel.den), 0, RoundingMode.HALF_UP).toInt()
    }

    private const val PERCENT = 100L
}

/** A non-negative fraction kept exact, so guardrails and rounding never depend on floating point. */
private data class Ratio(val num: Long, val den: Long) : Comparable<Ratio> {
    operator fun minus(other: Ratio) = Ratio(num * other.den - other.num * den, den * other.den)
    operator fun div(other: Ratio) = Ratio(num * other.den, den * other.num)
    override fun compareTo(other: Ratio): Int = (num * other.den).compareTo(other.num * den)
    fun toDouble(): Double = num.toDouble() / den
}
