package com.assismohanty.awaken.engine.body

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One finished session of an exercise. */
data class ExerciseSession(val date: LocalDate, val sets: List<LoggedSet>)

enum class HistoryMetric { TOP_WEIGHT, E1RM, VOLUME }

/** How far back the history chart looks (GD §16.9, N18). */
enum class HistoryRange { MONTH, THREE_MONTHS, YEAR, ALL }

data class TrendPoint(val date: LocalDate, val value: Double)

/** Per-exercise history series (GD §16.9). Pure. */
object ExerciseHistory {
    /** Fewer points than this and the chart shows its empty state. */
    const val MIN_POINTS = 2

    fun value(session: ExerciseSession, metric: HistoryMetric): Double? {
        val done = session.sets.filter { it.done && it.reps >= 1 }
        if (done.isEmpty()) return null
        return when (metric) {
            HistoryMetric.TOP_WEIGHT -> done.maxOf { it.weightKg }
            HistoryMetric.E1RM -> done.mapNotNull { TrainingMath.estimatedOneRepMax(it) }.maxOrNull()
            HistoryMetric.VOLUME -> TrainingMath.volume(done)
        }
    }

    fun since(range: HistoryRange, today: LocalDate): LocalDate? = when (range) {
        HistoryRange.MONTH -> today.minusMonths(1)
        HistoryRange.THREE_MONTHS -> today.minusMonths(3)
        HistoryRange.YEAR -> today.minusYears(1)
        HistoryRange.ALL -> null
    }

    /** One point per session in [range], oldest first. Year and All keep the best value of each ISO week. */
    fun series(sessions: List<ExerciseSession>, metric: HistoryMetric, range: HistoryRange, today: LocalDate): List<TrendPoint> {
        val from = since(range, today)
        val points = sessions
            .filter { from == null || !it.date.isBefore(from) }
            .filter { !it.date.isAfter(today) }
            .mapNotNull { s -> value(s, metric)?.let { TrendPoint(s.date, it) } }
            .sortedBy { it.date }
        if (range != HistoryRange.YEAR && range != HistoryRange.ALL) return points
        return points.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .map { (_, week) -> week.maxBy { it.value } }
            .sortedBy { it.date }
    }
}
