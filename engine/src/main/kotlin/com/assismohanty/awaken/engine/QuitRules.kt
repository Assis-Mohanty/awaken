package com.assismohanty.awaken.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** What a counter counts days clean of (GD §16.13). Doomscrolling is the one that counts itself from Screen Time. */
enum class QuitKind { SMOKING, PORN, JUNK_FOOD, DOOMSCROLLING, ALCOHOL, CUSTOM;

    /** Counted from the hunter's own start and restarts; every kind but Doomscrolling. */
    val isTimeBased: Boolean get() = this != DOOMSCROLLING
}

/** How a finalized game-day went for a Doomscrolling counter. */
enum class QuitDayVerdict { CLEAN, OVER, PAUSED }

/** The current and best run of a counter. [bestDays] and [currentDays] are whole 24-hour periods (time-based) or clean days. */
data class QuitRuns(val current: Duration, val best: Duration) {
    val currentDays: Int get() = current.toDays().toInt()
    val bestDays: Int get() = best.toDays().toInt()
}

/**
 * Quit counters (GD §16.13, approved 2026-11-02). Pure. A relapse never deletes history: the best run is kept forever.
 * Nothing here pays XP; the only reward is a gold-only achievement on the in-app run (see [bestInAppRunDays]).
 */
object QuitRules {
    /** N24: active counters at once. */
    const val MAX_COUNTERS = 6
    const val NAME_MAX = 24
    const val NOTE_MAX = 140

    /** N25 */
    val MILESTONES = listOf(1, 3, 7, 14, 30, 90, 180, 365)

    /** N26: a Doomscrolling day is clean at or under this many Distracting-app minutes. */
    const val DOOMSCROLL_LIMIT_MINUTES = 60

    private const val DAY_SECONDS = 24 * 60 * 60L

    /** Runs of a time-based counter at [now]: the gaps between its start, each restart and now. Restarts outside the span are ignored. */
    fun runs(start: Instant, restarts: List<Instant>, now: Instant): QuitRuns {
        if (now.isBefore(start)) return QuitRuns(Duration.ZERO, Duration.ZERO)
        val marks = (listOf(start) + restarts.filter { it.isAfter(start) && !it.isAfter(now) }.sorted() + now)
        val gaps = marks.zipWithNext { a, b -> Duration.between(a, b) }
        return QuitRuns(current = gaps.last(), best = gaps.max())
    }

    /**
     * The best run counted only from when the counter was made in the app (vector #123): a start date in the past never
     * earns anything. Restarts before the in-app start are ignored.
     */
    fun bestInAppRun(start: Instant, created: Instant, restarts: List<Instant>, now: Instant): Duration =
        runs(maxOf(start, created), restarts, now).best

    fun bestInAppRunDays(start: Instant, created: Instant, restarts: List<Instant>, now: Instant): Int =
        bestInAppRun(start, created, restarts, now).toDays().toInt()

    /** Cost per day times whole days of the current run, in minor units. Display only: no real money is involved. */
    fun moneySavedMinor(costPerDayMinor: Long, currentRun: Duration): Long = costPerDayMinor * currentRun.toDays()

    /** Minutes per day times whole days of the current run. */
    fun minutesReclaimed(minutesPerDay: Int, currentRun: Duration): Long = minutesPerDay.toLong() * currentRun.toDays()

    /** The largest milestone reached by [days], or null before the first. */
    fun reachedMilestone(days: Int): Int? = MILESTONES.lastOrNull { days >= it }

    /** The next milestone still ahead, or null after the last. */
    fun nextMilestone(days: Int): Int? = MILESTONES.firstOrNull { days < it }

    /** Fill toward [nextMilestone], 0..1; full once every milestone is behind. */
    fun milestoneProgress(days: Int): Float {
        val next = nextMilestone(days) ?: return 1f
        val previous = reachedMilestone(days) ?: 0
        return ((days - previous).toFloat() / (next - previous)).coerceIn(0f, 1f)
    }

    /** A Doomscrolling day: unreadable usage (null) pauses it, neither counting nor breaking (fail closed). */
    fun doomscrollVerdict(distractingMinutes: Int?, limitMinutes: Int = DOOMSCROLL_LIMIT_MINUTES): QuitDayVerdict = when {
        distractingMinutes == null -> QuitDayVerdict.PAUSED
        distractingMinutes <= limitMinutes -> QuitDayVerdict.CLEAN
        else -> QuitDayVerdict.OVER
    }

    /** The current and best run of clean days, from the verdict of each finalized day (vector #125). Paused days change nothing. */
    fun doomscrollRuns(verdicts: Map<LocalDate, QuitDayVerdict>): Pair<Int, Int> {
        var current = 0
        var best = 0
        verdicts.toSortedMap().values.forEach { verdict ->
            when (verdict) {
                QuitDayVerdict.CLEAN -> best = maxOf(best, ++current)
                QuitDayVerdict.OVER -> current = 0
                QuitDayVerdict.PAUSED -> Unit
            }
        }
        return current to best
    }

    /** Whole hours of a run shorter than a day, for "7 h" under one day. */
    fun hoursUnderOneDay(run: Duration): Int = if (run.seconds < DAY_SECONDS) run.toHours().toInt() else 0
}
