package com.assismohanty.awaken.reelcounter.core.stats

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import java.time.LocalDate

object StatsCalculator {

    /** Exactly [days] bars, oldest first, the last one being [today]. */
    fun series(totals: List<DayTotal>, today: LocalDate, days: Int): List<DayBar> {
        val byDay = totals.groupBy { it.day }
        return (days - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val rows = byDay[day].orEmpty()
            DayBar(
                day = day,
                total = rows.sumOf { it.count },
                perApp = rows.groupBy { it.app }.mapValues { (_, v) -> v.sumOf { it.count } },
                seconds = rows.sumOf { it.seconds },
            )
        }
    }

    /**
     * Days in a row, counting back from [today], on which every app with a limit stayed at or under it.
     * Null when no limits are set. Today over its limit gives 0.
     */
    fun withinLimitStreak(
        totals: List<DayTotal>,
        limits: Map<ShortVideoApp, Int>,
        today: LocalDate,
        firstDay: LocalDate?,
    ): Int? {
        if (limits.isEmpty()) return null
        if (firstDay == null) return 0
        val byDay = totals.groupBy { it.day }
        var streak = 0
        var day = today
        while (!day.isBefore(firstDay)) {
            val rows = byDay[day].orEmpty()
            val within = limits.all { (app, limit) -> rows.filter { it.app == app }.sumOf { it.count } <= limit }
            if (!within) break
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Round-half-up average of the bars on or after [firstDay]. */
    fun dailyAverage(series: List<DayBar>, firstDay: LocalDate?): Int {
        if (firstDay == null) return 0
        val bars = series.filter { !it.day.isBefore(firstDay) }
        if (bars.isEmpty()) return 0
        val sum = bars.sumOf { it.total }
        return (sum * 2 + bars.size) / (bars.size * 2)
    }

    fun minutes(seconds: Long): Long = seconds / 60

    fun totalsForDay(totals: List<DayTotal>, day: LocalDate): Map<ShortVideoApp, DayTotal> =
        totals.filter { it.day == day }.associateBy { it.app }
}
