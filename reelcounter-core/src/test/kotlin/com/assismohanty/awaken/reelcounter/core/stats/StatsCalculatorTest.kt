package com.assismohanty.awaken.reelcounter.core.stats

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.YOUTUBE as YT
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StatsCalculatorTest {
    private val today = LocalDate.of(2026, 11, 1)
    private fun d(back: Long) = today.minusDays(back)
    private fun row(back: Long, app: com.assismohanty.awaken.reelcounter.core.ShortVideoApp, count: Int, seconds: Long = 0) =
        DayTotal(d(back), app, count, seconds)

    @Test
    fun seriesZeroFillsMissingDays() {
        val bars = StatsCalculator.series(listOf(row(2, IG, 5)), today, 7)
        assertThat(bars.map { it.total }).containsExactly(0, 0, 0, 0, 5, 0, 0).inOrder()
    }

    @Test
    fun seriesHasExactlyNDaysEndingToday() {
        for (n in listOf(7, 30)) {
            val bars = StatsCalculator.series(emptyList(), today, n)
            assertThat(bars).hasSize(n)
            assertThat(bars.last().day).isEqualTo(today)
            assertThat(bars.first().day).isEqualTo(today.minusDays(n - 1L))
        }
    }

    @Test
    fun seriesPerAppBreakdown() {
        val bar = StatsCalculator.series(listOf(row(0, IG, 5, 60), row(0, YT, 3, 30)), today, 7).last()
        assertThat(bar.total).isEqualTo(8)
        assertThat(bar.perApp).containsExactly(IG, 5, YT, 3)
        assertThat(bar.seconds).isEqualTo(90)
    }

    @Test
    fun streakNullWithoutLimits() {
        assertThat(StatsCalculator.withinLimitStreak(emptyList(), emptyMap(), today, d(5))).isNull()
    }

    @Test
    fun streakCountsConsecutiveDaysWithinLimit() {
        val totals = listOf(row(0, IG, 5), row(1, IG, 10), row(2, IG, 11))
        assertThat(StatsCalculator.withinLimitStreak(totals, mapOf(IG to 10), today, d(20))).isEqualTo(2)
    }

    @Test
    fun streakBrokenByDayOverLimit() {
        val totals = listOf(row(0, IG, 5), row(1, IG, 50), row(2, IG, 5))
        assertThat(StatsCalculator.withinLimitStreak(totals, mapOf(IG to 10), today, d(20))).isEqualTo(1)
    }

    @Test
    fun streakIgnoresAppsWithoutLimit() {
        val totals = listOf(row(0, YT, 500), row(0, IG, 1))
        assertThat(StatsCalculator.withinLimitStreak(totals, mapOf(IG to 10), today, d(2))).isEqualTo(3)
    }

    @Test
    fun todayOverLimitStreakZero() {
        assertThat(StatsCalculator.withinLimitStreak(listOf(row(0, IG, 11)), mapOf(IG to 10), today, d(5))).isEqualTo(0)
    }

    @Test
    fun streakStopsAtFirstDay() {
        assertThat(StatsCalculator.withinLimitStreak(listOf(row(0, IG, 1)), mapOf(IG to 10), today, d(1))).isEqualTo(2)
    }

    @Test
    fun exactlyAtLimitIsWithin() {
        assertThat(StatsCalculator.withinLimitStreak(listOf(row(0, IG, 10)), mapOf(IG to 10), today, today)).isEqualTo(1)
    }

    @Test
    fun averageRoundsHalfUpAndIgnoresDaysBeforeFirstDay() {
        val series = StatsCalculator.series(listOf(row(0, IG, 2), row(1, IG, 1), row(3, IG, 100)), today, 7)
        assertThat(StatsCalculator.dailyAverage(series, d(1))).isEqualTo(2)
        assertThat(StatsCalculator.dailyAverage(series, null)).isEqualTo(0)
    }

    @Test
    fun minutesFloor() {
        assertThat(StatsCalculator.minutes(119)).isEqualTo(1)
        assertThat(StatsCalculator.minutes(59)).isEqualTo(0)
    }
}
