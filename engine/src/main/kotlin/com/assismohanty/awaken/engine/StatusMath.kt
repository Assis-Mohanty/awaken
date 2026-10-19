package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalDate

/** A game-week: ISO Monday → Sunday, the same weeks recalibration runs on (GD §6.4). */
data class GameWeek(val monday: LocalDate) {
    val sunday: LocalDate get() = monday.plusDays(DAYS_PER_WEEK - 1)
    val previous: GameWeek get() = GameWeek(monday.minusDays(DAYS_PER_WEEK))
    operator fun contains(date: LocalDate): Boolean = date >= monday && date <= sunday

    companion object {
        const val DAYS_PER_WEEK = 7L
        fun of(gameDate: LocalDate): GameWeek = GameWeek(DifficultyAdapter.recalibrationDate(gameDate))
    }
}

/**
 * Weekly Report (GD §11).
 * @param statXpGained net stat XP per stat for the week (undo rows included, so undone work nets out).
 * @param bestDay the day with the most net XP; null when no XP was earned. Ties go to the earlier day.
 * @param mostNeglected the stat with the least gain; ties go by [Stat] order.
 */
data class WeeklyReport(
    val week: GameWeek,
    val statXpGained: Map<Stat, Long>,
    val totalXp: Long,
    val bestDay: LocalDate?,
    val bestDayXp: Long,
    val mostNeglected: Stat,
    val questsCompleted: Int,
)

/** Pure Status-screen math (ENGINE_DECISIONS #38, #39). */
object StatusMath {
    const val HEATMAP_WEEKS = 18
    const val MAX_HEATMAP_INTENSITY = 4

    /** Heatmap step 0–4: active completions on the day, capped (ENGINE_DECISIONS #39). */
    fun heatmapIntensity(completions: Int): Int = completions.coerceIn(0, MAX_HEATMAP_INTENSITY)

    /** First day of the 18-week heatmap ending in [today]'s week: the Monday 17 weeks before this one. */
    fun heatmapStart(today: LocalDate): LocalDate = GameWeek.of(today).monday.minusWeeks(HEATMAP_WEEKS - 1L)

    /** Radar axis max over a set of stat levels (GD §2). */
    fun radarMax(levels: Collection<Int>): Int = StatLevel.radarMax(levels.maxOrNull() ?: 1)

    fun weeklyReport(
        week: GameWeek,
        statXpGained: Map<Stat, Long>,
        xpPerDay: Map<LocalDate, Long>,
        questsCompleted: Int,
    ): WeeklyReport {
        val gained = Stat.entries.associateWith { statXpGained[it] ?: 0L }
        val best = xpPerDay.filterKeys { it in week }
            .filterValues { it > 0 }
            .entries
            .sortedBy { it.key }
            .maxByOrNull { it.value }
        return WeeklyReport(
            week = week,
            statXpGained = gained,
            totalXp = xpPerDay.filterKeys { it in week }.values.sum(),
            bestDay = best?.key,
            bestDayXp = best?.value ?: 0L,
            // minBy keeps the first minimum, i.e. Stat order on ties.
            mostNeglected = Stat.entries.minBy { gained.getValue(it) },
            questsCompleted = questsCompleted,
        )
    }
}
