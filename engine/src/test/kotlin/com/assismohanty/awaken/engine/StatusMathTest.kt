package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StatusMathTest {

    private val wednesday = LocalDate.parse("2026-10-21")
    private val week = GameWeek.of(wednesday)

    @Test
    fun `game week runs Monday to Sunday`() {
        assertThat(week.monday).isEqualTo(LocalDate.parse("2026-10-19"))
        assertThat(week.sunday).isEqualTo(LocalDate.parse("2026-10-25"))
        assertThat(GameWeek.of(week.sunday)).isEqualTo(week)
        assertThat(week.previous.monday).isEqualTo(LocalDate.parse("2026-10-12"))
        assertThat(LocalDate.parse("2026-10-26") in week).isFalse()
    }

    @Test
    fun `heatmap intensity caps at 4`() {
        assertThat((0..6).map(StatusMath::heatmapIntensity)).containsExactly(0, 1, 2, 3, 4, 4, 4).inOrder()
        assertThat(StatusMath.heatmapIntensity(-1)).isEqualTo(0)
    }

    @Test
    fun `heatmap spans 18 weeks ending this week`() {
        val start = StatusMath.heatmapStart(wednesday)
        assertThat(start.dayOfWeek).isEqualTo(java.time.DayOfWeek.MONDAY)
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(start, week.sunday) + 1).isEqualTo(18 * 7L)
    }

    @Test
    fun `radar max is at least 20 and leaves 5 levels of headroom`() {
        assertThat(StatusMath.radarMax(listOf(1, 3, 15))).isEqualTo(20)
        assertThat(StatusMath.radarMax(listOf(16))).isEqualTo(21)
        assertThat(StatusMath.radarMax(emptyList())).isEqualTo(20)
    }

    @Test
    fun `empty week has no best day and neglects the first stat`() {
        val report = StatusMath.weeklyReport(week, emptyMap(), emptyMap(), questsCompleted = 0)
        assertThat(report.bestDay).isNull()
        assertThat(report.totalXp).isEqualTo(0)
        assertThat(report.mostNeglected).isEqualTo(Stat.STR)
        assertThat(report.statXpGained.values.toSet()).containsExactly(0L)
    }

    @Test
    fun `best day ties go to the earlier day and other weeks are ignored`() {
        val xpPerDay = mapOf(
            week.monday.plusDays(3) to 120L,
            week.monday.plusDays(1) to 120L,
            week.monday.minusDays(1) to 999L,
        )
        val report = StatusMath.weeklyReport(week, mapOf(Stat.STR to 80L, Stat.INT to 40L), xpPerDay, questsCompleted = 4)
        assertThat(report.bestDay).isEqualTo(week.monday.plusDays(1))
        assertThat(report.bestDayXp).isEqualTo(120)
        assertThat(report.totalXp).isEqualTo(240)
    }

    @Test
    fun `most neglected is the lowest gain with stat order on ties`() {
        val gained = mapOf(Stat.STR to 50L, Stat.INT to 50L, Stat.WIL to 10L, Stat.VIT to 10L, Stat.CHA to 30L)
        assertThat(StatusMath.weeklyReport(week, gained, emptyMap(), 0).mostNeglected).isEqualTo(Stat.WIL)
    }
}
