package com.assismohanty.awaken.engine

import java.time.LocalDate

/** A daily focus goal that took effect on [effectiveFrom] (GD §16.4). Versioned, so changing it never rewrites past days. */
data class FocusGoalVersion(val effectiveFrom: LocalDate, val minutes: Int)

/**
 * The daily focus goal ring and the focus streak (GD §16.4, approved 2026-11-02). Everything here is derived from gate
 * runs and day outcomes; nothing is stored, and no XP is ever paid for it (invariant 7). Pure.
 */
object FocusGoalRules {
    /** N11: 120 minutes a day until the hunter chooses otherwise. */
    const val DEFAULT_GOAL = 120

    /** N12: 15-600 minutes in 15-minute steps. */
    const val MIN_GOAL = 15
    const val MAX_GOAL = 600
    const val GOAL_STEP = 15

    /** The streak lengths that unlock Deep Work (GD §16.16). */
    val STREAK_MILESTONES = listOf(7, 30, 100)

    fun isValidGoal(minutes: Int): Boolean = minutes in MIN_GOAL..MAX_GOAL && (minutes - MIN_GOAL) % GOAL_STEP == 0

    fun clampGoal(minutes: Int): Int = MIN_GOAL + (minutes.coerceIn(MIN_GOAL, MAX_GOAL) - MIN_GOAL) / GOAL_STEP * GOAL_STEP

    /**
     * Focus minutes of one game-day: the whole minutes of every run that started that day and ended CLEARED or LOGGED
     * (preset, custom, Pomodoro round, Timed habit, Path gate: planned length; stopwatch: counted time). Collapsed and
     * abandoned runs never count. [runSeconds] holds one entry per qualifying run.
     */
    fun focusMinutes(runSeconds: Collection<Int>): Int = runSeconds.sumOf { it / SECONDS_PER_MINUTE }

    /** The goal in effect on [date]: the latest version taking effect on or before it, else the default. */
    fun goalOn(date: LocalDate, versions: List<FocusGoalVersion>): Int =
        versions.filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }?.minutes ?: DEFAULT_GOAL

    fun met(minutes: Int, goal: Int): Boolean = minutes >= goal

    /**
     * The focus streak on [today]: consecutive game-days that met their goal, counting back from today if today is met,
     * else from yesterday. A finalized REST or RECOVERY day (§7.2) is skipped over: it neither extends nor breaks the
     * streak. Any other day that is not met ends it. [firstDay] (the hunter's first game-day) bounds the walk.
     */
    fun streak(
        today: LocalDate,
        firstDay: LocalDate,
        minutesByDay: Map<LocalDate, Int>,
        goals: List<FocusGoalVersion>,
        outcomes: Map<LocalDate, DayOutcome>,
    ): Int {
        fun metOn(day: LocalDate) = met(minutesByDay[day] ?: 0, goalOn(day, goals))
        var day = if (metOn(today)) today else today.minusDays(1)
        var count = 0
        while (!day.isBefore(firstDay)) {
            when {
                outcomes[day] == DayOutcome.REST || outcomes[day] == DayOutcome.RECOVERY -> Unit
                metOn(day) -> count++
                else -> break
            }
            day = day.minusDays(1)
        }
        return count
    }

    private const val SECONDS_PER_MINUTE = 60
}
