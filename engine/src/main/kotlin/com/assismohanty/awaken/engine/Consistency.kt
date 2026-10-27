package com.assismohanty.awaken.engine

import java.time.LocalDate

/** "{cleared} of the last {eligible} days" (GD §14.2). [ratio] is null when no day was eligible (UI shows "—"). */
data class ConsistencyScore(val cleared: Int, val eligible: Int) {
    val ratio: Double? get() = if (eligible == 0) null else cleared.toDouble() / eligible
}

/** The calm line Today shows in place of the daily System line after a missed day (GD §14.2). */
enum class MissLine { MISSED_YESTERDAY, FRESH_START }

/** Consistency score, "never miss twice" and recoveries (GD §14.2). Everything is derived from finalized outcomes. */
object Consistency {
    const val WINDOW_DAYS = 28L

    /** Rest/recovery days that were not cleared: left out of the score (GD §14.1). */
    fun isExcluded(outcome: DayOutcome): Boolean = outcome == DayOutcome.REST || outcome == DayOutcome.RECOVERY

    /** Uncleared and not covered by rest/recovery; a shield saves the streak but the day was still missed. */
    fun isMissed(outcome: DayOutcome): Boolean = outcome == DayOutcome.BROKEN || outcome == DayOutcome.SHIELDED

    /**
     * The last [WINDOW_DAYS] game-days ending yesterday, from [firstGameDate] on. Today is never finalized, so a day
     * without an outcome in [outcomes] is simply not counted.
     */
    fun score(outcomes: Map<LocalDate, DayOutcome>, today: LocalDate, firstGameDate: LocalDate): ConsistencyScore {
        val from = maxOf(today.minusDays(WINDOW_DAYS), firstGameDate)
        return over(outcomes.filterKeys { it >= from && it < today }.values)
    }

    /** The §14.2 formula over any set of finalized days (recaps use a whole month or year). */
    fun over(outcomes: Collection<DayOutcome>): ConsistencyScore {
        val eligible = outcomes.filterNot(::isExcluded)
        return ConsistencyScore(cleared = eligible.count { it == DayOutcome.CLEARED }, eligible = eligible.size)
    }

    /** Line for today from yesterday's and the day before's outcomes (null = not finalized or before the hunter began). */
    fun missLine(yesterday: DayOutcome?, dayBefore: DayOutcome?): MissLine? = when {
        yesterday == null || !isMissed(yesterday) -> null
        dayBefore != null && isMissed(dayBefore) -> MissLine.FRESH_START
        else -> MissLine.MISSED_YESTERDAY
    }

    /** CLEARED days whose previous day was missed, over outcomes in date order (never stored, invariant 7). */
    fun recoveries(outcomesInOrder: List<DayOutcome>): Int =
        outcomesInOrder.zipWithNext().count { (before, day) -> day == DayOutcome.CLEARED && isMissed(before) }
}
