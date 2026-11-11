package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class DietReportTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private fun day(offset: Long, entries: Int, kcal: Double) = DayTotals(monday.plusDays(offset), entries, kcal, kcal / 20, kcal / 10, kcal / 30)

    @Test
    fun averagesSkipEmptyDaysAndTheStreakEndsOnTheLastDay() {
        val week = DietReport.week(listOf(day(0, 3, 2_000.0), day(1, 0, 0.0), day(2, 2, 1_000.0), day(3, 1, 3_000.0), day(4, 4, 2_000.0)))
        assertThat(week.daysLogged).isEqualTo(4)
        assertThat(week.avgKcal).isWithin(1e-9).of(2_000.0)
        assertThat(week.loggingStreak).isEqualTo(3)
    }

    @Test
    fun aWeekWithNothingLoggedHasNoAveragesAndNoStreak() {
        val week = DietReport.week(listOf(day(0, 0, 0.0), day(1, 0, 0.0)))
        assertThat(week.avgKcal).isNull()
        assertThat(week.loggingStreak).isEqualTo(0)
    }
}
