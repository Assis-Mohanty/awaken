package com.assismohanty.awaken.engine.body

import java.time.LocalDate

/** What a game-day holds: a routine to train, or rest. */
sealed interface DayPlan {
    data class Workout(val routineId: String) : DayPlan
    data object Rest : DayPlan
}

/**
 * The weekly routine schedule (GD §18.1). Each ISO weekday holds at most one routine and repeats every week; a one-off
 * override on a date (a swap, or a rest) wins for that date only. Dates are game-dates, so the caller has already applied
 * the day-start hour. Pure.
 */
object WeekSchedule {
    /** [slots] maps ISO weekday 1..7 to a routine id; [overrides] maps a game-date to a routine id, or null for rest. */
    fun dayPlan(date: LocalDate, slots: Map<Int, String>, overrides: Map<LocalDate, String?>): DayPlan {
        val routineId = if (overrides.containsKey(date)) overrides[date] else slots[date.dayOfWeek.value]
        return if (routineId == null) DayPlan.Rest else DayPlan.Workout(routineId)
    }

    /**
     * "Swap with today": the two dates trade what they hold, for those dates only. Returns the overrides to store (both
     * dates, so a later change to the weekly slots does not move them). Swapping a date with itself changes nothing.
     */
    fun swap(a: LocalDate, b: LocalDate, slots: Map<Int, String>, overrides: Map<LocalDate, String?>): Map<LocalDate, String?> {
        if (a == b) return emptyMap()
        fun held(date: LocalDate): String? = (dayPlan(date, slots, overrides) as? DayPlan.Workout)?.routineId
        return mapOf(a to held(b), b to held(a))
    }

    /** True when [date] is in the same Monday-to-Sunday week as [other]. */
    fun sameWeek(date: LocalDate, other: LocalDate): Boolean = TrainingMath.weekStart(date) == TrainingMath.weekStart(other)
}
