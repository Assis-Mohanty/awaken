package com.assismohanty.awaken.engine

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields

/**
 * Facts about one game-day, as known at finalization (GD §7).
 * @param itemsCompleted all completions that day of any type (main, side, habit, penalty, trial, Focus Gate).
 */
data class DayRecord(
    val gameDate: LocalDate,
    val mainCompleted: Boolean = false,
    val itemsCompleted: Int = 0,
    val restDay: Boolean = false,
    val recovery: Boolean = false,
)

data class StreakState(
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val shields: Int = 0,
    val recoveryDaysUsed: Map<YearMonth, Int> = emptyMap(),
) {
    fun recoveryUsedIn(month: YearMonth): Int = recoveryDaysUsed[month] ?: 0
}

enum class DayOutcome {
    /** Cleared: streak +1 (even on a rest or recovery day — effort always counts). */
    CLEARED,
    /** Uncleared rest day: streak kept, not incremented. */
    REST,
    /** Uncleared recovery day within the monthly budget: streak kept. */
    RECOVERY,
    /** Uncleared, a Streak Shield was consumed: streak kept, no penalty. */
    SHIELDED,
    /** Uncleared and unprotected: streak resets to 0, Penalty Zone triggers. */
    BROKEN,
}

data class DayResult(
    val gameDate: LocalDate,
    val outcome: DayOutcome,
    val streakAfter: Int,
) {
    val shieldUsed: Boolean get() = outcome == DayOutcome.SHIELDED
    val penaltyTriggered: Boolean get() = outcome == DayOutcome.BROKEN
}

data class StreakFold(val state: StreakState, val days: List<DayResult>) {
    /** True if any folded day should issue a penalty quest. The app issues at most one (GD §6.1: Penalty 0–1). */
    val penaltyPending: Boolean get() = days.any { it.penaltyTriggered }
}

/** Streak, rest, recovery and shields (GD §7). */
object StreakRules {
    const val CLEAR_ITEM_THRESHOLD = 3
    const val RECOVERY_DAYS_PER_MONTH = 7
    const val MAX_SHIELDS = 2
    const val DAY_CLEAR_GOLD = 20

    /** GD §7.1: main quest done, or at least 3 directive items of any type. */
    fun isCleared(day: DayRecord): Boolean = day.mainCompleted || day.itemsCompleted >= CLEAR_ITEM_THRESHOLD

    /**
     * Finalizes one day. Protection order for an uncleared day: rest → recovery (budget permitting) → shield → broken.
     * A shield is only consumed when there is a streak to protect.
     */
    fun finalizeDay(state: StreakState, day: DayRecord): Pair<StreakState, DayResult> {
        val month = YearMonth.from(day.gameDate)
        // Every recovery-flagged day draws on the monthly budget; days past the budget get no protection.
        val recoveryCovered = day.recovery && state.recoveryUsedIn(month) < RECOVERY_DAYS_PER_MONTH
        var next = if (recoveryCovered) {
            state.copy(recoveryDaysUsed = state.recoveryDaysUsed + (month to state.recoveryUsedIn(month) + 1))
        } else {
            state
        }

        val outcome = when {
            isCleared(day) -> DayOutcome.CLEARED
            day.restDay -> DayOutcome.REST
            recoveryCovered -> DayOutcome.RECOVERY
            next.streak > 0 && next.shields > 0 -> DayOutcome.SHIELDED
            else -> DayOutcome.BROKEN
        }
        next = when (outcome) {
            DayOutcome.CLEARED -> (next.streak + 1).let { next.copy(streak = it, bestStreak = maxOf(next.bestStreak, it)) }
            DayOutcome.REST, DayOutcome.RECOVERY -> next
            DayOutcome.SHIELDED -> next.copy(shields = next.shields - 1)
            DayOutcome.BROKEN -> next.copy(streak = 0)
        }
        return next to DayResult(day.gameDate, outcome, next.streak)
    }

    /**
     * Pure fold over finalized days, in date order. Dates missing between consecutive records (the app was never
     * opened) are treated as empty, uncleared days.
     */
    fun fold(initial: StreakState, days: List<DayRecord>): StreakFold {
        val sorted = days.sortedBy { it.gameDate }
        require(sorted.zipWithNext().none { (a, b) -> a.gameDate == b.gameDate }) { "duplicate gameDate in day records" }
        var state = initial
        val results = ArrayList<DayResult>()
        var previous: LocalDate? = null
        for (day in sorted) {
            var gap = previous?.plusDays(1)
            while (gap != null && gap < day.gameDate) {
                val (s, r) = finalizeDay(state, DayRecord(gap))
                state = s
                results += r
                gap = gap.plusDays(1)
            }
            val (s, r) = finalizeDay(state, day)
            state = s
            results += r
            previous = day.gameDate
        }
        return StreakFold(state, results)
    }

    /**
     * Rebuilds streak and best streak from finalized outcomes in date order (ENGINE_DECISIONS #23). This matches
     * [fold] exactly because the outcome already encodes which protection (if any) applied.
     */
    fun replay(outcomes: List<DayOutcome>): Pair<Int, Int> {
        var streak = 0
        var best = 0
        for (outcome in outcomes) {
            when (outcome) {
                DayOutcome.CLEARED -> {
                    streak++
                    best = maxOf(best, streak)
                }
                DayOutcome.REST, DayOutcome.RECOVERY, DayOutcome.SHIELDED -> Unit
                DayOutcome.BROKEN -> streak = 0
            }
        }
        return streak to best
    }

    /** Recovery days already used in a month, from finalized recovery-flagged days (budget-capped like [finalizeDay]). */
    fun recoveryUsed(finalizedRecoveryDays: Int): Int = minOf(finalizedRecoveryDays, RECOVERY_DAYS_PER_MONTH)

    /** Streak to display today: finalized streak, +1 once today is already cleared. */
    fun displayStreak(finalized: StreakState, today: DayRecord): Int =
        finalized.streak + if (isCleared(today)) 1 else 0

    /** A rest day must be marked before it starts, and at most one per ISO week. */
    fun canMarkRest(date: LocalDate, today: LocalDate, existingRestDays: Collection<LocalDate>): Boolean =
        date > today && existingRestDays.none { it != date && isoWeek(it) == isoWeek(date) }

    fun canBuyShield(held: Int): Boolean = held < MAX_SHIELDS

    private fun isoWeek(date: LocalDate): Pair<Int, Int> =
        date.get(IsoFields.WEEK_BASED_YEAR) to date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}
