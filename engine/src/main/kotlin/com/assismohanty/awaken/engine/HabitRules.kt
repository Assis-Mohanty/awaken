package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.Verification
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Why a habit draft cannot be saved (GD §16.1). */
enum class HabitProblem {
    /** Count habits take a target of [HabitRules.QUANTITY_MIN]..[HabitRules.QUANTITY_MAX]; a Check habit's target must be at least 1. */
    QUANTITY_TARGET,

    /** Timed habits take 15–180 minutes in 5-minute steps. */
    TIMED_MINUTES,

    /** "Times per week" takes 1–6. */
    TIMES_PER_WEEK,

    /** Avoid habits are always on fixed weekdays. */
    NEGATIVE_NEEDS_DAYS,
}

/** Habit types, flexible weeks, skips and reminders (GD §16.1–16.2, approved 2026-11-02). Pure. */
object HabitRules {
    /** N1: the active-habit limit is unchanged. */
    const val MAX_HABITS = QuestRules.MAX_ACTIVE_CUSTOM

    /** N2: a count habit takes 2–100 (1 would be a plain check). */
    const val QUANTITY_MIN = 2
    const val QUANTITY_MAX = 100

    /** N3: a timed habit takes 15–180 minutes in 5-minute steps (the §8.1 P1 range with 15 as the floor). */
    const val TIMED_MIN_MINUTES = 15
    const val TIMED_MAX_MINUTES = 180
    const val TIMED_STEP_MINUTES = 5

    /** N4: "times per week" takes 1–6 (7 is "every day"). */
    const val WEEKLY_MIN = 1
    const val WEEKLY_MAX = 6

    /** N5: at most this many habits may have a reminder. */
    const val MAX_REMINDERS = 2

    /** N6: one skip per habit per ISO week. */
    const val SKIPS_PER_WEEK = 1

    /** Check, Count and Avoid are self-reported; a Timed habit is verified by its focus session (GD §16.1). */
    fun verificationFor(type: HabitType): Verification = if (type == HabitType.TIMED) Verification.TIMER else Verification.SELF

    /** A timed habit's difficulty comes from its length, never from a picker (GD §16.1, §8.1 P2 tiers). */
    fun timedDifficulty(minutes: Int): Difficulty = FocusRules.tierFor(minutes)?.difficulty ?: Difficulty.EASY

    fun isValidTimedMinutes(minutes: Int): Boolean =
        minutes in TIMED_MIN_MINUTES..TIMED_MAX_MINUTES && (minutes - TIMED_MIN_MINUTES) % TIMED_STEP_MINUTES == 0

    /** The first problem with a draft's type, frequency and target, or null when it is fine. */
    fun validate(type: HabitType, frequency: HabitFrequency, timesPerWeek: Int?, target: Int): HabitProblem? {
        if (frequency == HabitFrequency.TIMES_PER_WEEK && (timesPerWeek == null || timesPerWeek !in WEEKLY_MIN..WEEKLY_MAX)) {
            return HabitProblem.TIMES_PER_WEEK
        }
        return when (type) {
            // A Check habit keeps its free-form target and unit ("20 pages"), exactly as habits did before v1.2.
            HabitType.CHECK -> if (target < 1) HabitProblem.QUANTITY_TARGET else null
            HabitType.QUANTITY -> if (target !in QUANTITY_MIN..QUANTITY_MAX) HabitProblem.QUANTITY_TARGET else null
            HabitType.TIMED -> if (!isValidTimedMinutes(target)) HabitProblem.TIMED_MINUTES else null
            HabitType.NEGATIVE -> if (frequency != HabitFrequency.DAYS_OF_WEEK) HabitProblem.NEGATIVE_NEEDS_DAYS else null
        }
    }

    /** Does a flexible habit still need an instance today? Once the week's quota is met it gets none until next Monday. */
    fun flexibleNeedsInstance(timesPerWeek: Int, completionsThisWeek: Int): Boolean = completionsThisWeek < timesPerWeek

    /**
     * The status an unfinished habit instance gets when its game-day closes: a flexible habit never records a miss
     * (EXPIRED), a fixed-day habit is MISSED, and a hunter's unconfirmed Avoid day is MISSED too (fails closed, Q4).
     */
    fun closedStatus(frequency: HabitFrequency): QuestStatus =
        if (frequency == HabitFrequency.TIMES_PER_WEEK) QuestStatus.EXPIRED else QuestStatus.MISSED

    /** Count habits complete themselves the moment progress reaches the target (GD §16.1); partial progress pays nothing. */
    fun quantityComplete(progress: Int, target: Int): Boolean = progress >= target

    fun clampProgress(progress: Int, target: Int): Int = progress.coerceIn(0, target)

    /**
     * "Kept it today" is available from the start of the Evening block until the game-day ends (GD §16.1), so the day has
     * been seen through before it pays. [now] is the local time of day.
     */
    fun canConfirmKept(now: LocalTime, dayStartHour: Int, bounds: TimeBlocks.Bounds = TimeBlocks.Bounds()): Boolean =
        TimeBlocks.blockAt(now, dayStartHour, bounds) == com.assismohanty.awaken.engine.model.TimeOfDay.EVENING

    /** A skip is allowed while the week's allowance is unused, and never for flexible habits (every day is already optional). */
    fun canSkip(frequency: HabitFrequency, skipsThisWeek: Int): Boolean =
        frequency == HabitFrequency.DAYS_OF_WEEK && skipsThisWeek < SKIPS_PER_WEEK

    /** Setting a reminder on a habit: a third habit with one is refused; editing a habit that already has one is fine. */
    fun canSetReminder(otherHabitsWithReminder: Int): Boolean = otherHabitsWithReminder < MAX_REMINDERS

    /** Reminders are whole minutes after local midnight, 0..1439. */
    fun isValidReminder(minutes: Int): Boolean = minutes in 0 until MINUTES_PER_DAY

    /** The ISO week (Monday to Sunday of game-days) a flexible quota and a skip allowance are counted in (GD §16.1-16.2). */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekEnd(date: LocalDate): LocalDate = weekStart(date).plusDays(DAYS_IN_WEEK - 1L)

    private const val MINUTES_PER_DAY = 24 * 60
    private const val DAYS_IN_WEEK = 7
}
