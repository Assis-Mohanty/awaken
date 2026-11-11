package com.assismohanty.awaken.engine.body

import java.time.LocalDate

/** One logged day's totals. [entries] is the number of diary rows. */
data class DayTotals(val date: LocalDate, val entries: Int, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

/** [avgKcal] and the macro averages are over the days that have entries only; null when no day has. */
data class WeekReport(
    val daysLogged: Int,
    val avgKcal: Double?,
    val avgProtein: Double?,
    val avgCarbs: Double?,
    val avgFat: Double?,
    /** Days in a row, ending on the last day of the week, with at least one entry. Shown here only; it pays nothing (GD 20.5). */
    val loggingStreak: Int,
)

/** The weekly report's numbers (GD section 20.4). Averages skip empty days, so a day off never drags them down. Pure. */
object DietReport {
    fun week(days: List<DayTotals>): WeekReport {
        val sorted = days.sortedBy { it.date }
        val logged = sorted.filter { it.entries > 0 }
        fun avg(pick: (DayTotals) -> Double): Double? = if (logged.isEmpty()) null else logged.sumOf(pick) / logged.size
        val streak = sorted.asReversed().takeWhile { it.entries > 0 }.size
        return WeekReport(logged.size, avg { it.kcal }, avg { it.protein }, avg { it.carbs }, avg { it.fat }, streak)
    }
}
