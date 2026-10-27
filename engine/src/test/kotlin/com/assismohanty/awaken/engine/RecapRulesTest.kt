package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.DayOutcome.BROKEN
import com.assismohanty.awaken.engine.DayOutcome.CLEARED
import com.assismohanty.awaken.engine.DayOutcome.REST
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** GAME_DESIGN §14.6 and vectors 77–79 (78, idempotent storage, is covered by the app test). */
class RecapRulesTest {

    @Test
    fun `vector 77 - a month with 6 finalized days gets no recap`() {
        val today = LocalDate.of(2026, 11, 1)
        assertThat(RecapRules.due(today, emptySet()) { 6 }).isEmpty()
        assertThat(RecapRules.due(today, emptySet()) { 7 }.map { it.key }).containsExactly("M:2026-10")
    }

    @Test
    fun `vector 79 - the yearly recap is not offered on January 15`() {
        val jan15 = LocalDate.of(2027, 1, 15)
        assertThat(RecapRules.due(jan15, emptySet()) { 200 }.map { it.key }).containsExactly("M:2026-12")
        val jan14 = LocalDate.of(2027, 1, 14)
        assertThat(RecapRules.due(jan14, emptySet()) { 200 }.map { it.key }).containsExactly("Y:2026", "M:2026-12").inOrder()
    }

    @Test
    fun `stored periods are not generated again`() {
        val today = LocalDate.of(2027, 1, 3)
        assertThat(RecapRules.due(today, setOf("Y:2026", "M:2026-12")) { 200 }).isEmpty()
    }

    @Test
    fun `build derives every field and leaves empty ones out`() {
        val period = RecapRules.previousMonth(LocalDate.of(2026, 11, 2))
        // A 3-day run-up in September, then October: 5 cleared, 1 rest, 1 broken, 2 cleared.
        val september = (28..30).map { LocalDate.of(2026, 9, it) to CLEARED }
        val october = listOf(CLEARED, CLEARED, CLEARED, CLEARED, CLEARED, REST, BROKEN, CLEARED, CLEARED)
            .mapIndexed { i, outcome -> LocalDate.of(2026, 10, i + 1) to outcome }
        val payload = RecapRules.build(
            RecapInput(
                period = period,
                questsCompleted = 41,
                clearedGateSeconds = 5_400 + 1_500,
                outcomesThroughEnd = september + october,
                statXpBefore = mapOf(Stat.STR to 0L),
                statXpGained = mapOf(Stat.STR to 400L, Stat.INT to 400L, Stat.WIL to 0L),
                xpBefore = 0,
                xpGained = 1_000,
                health = emptyList(),
                achievementsEarned = 2,
            ),
        )
        assertThat(payload.periodKey).isEqualTo("M:2026-10")
        assertThat(payload.gateHours).isEqualTo(1.9)
        assertThat(payload.bestStreak).isEqualTo(8) // 3 in September + 5 in October
        assertThat(payload.consistencyCleared).isEqualTo(7)
        assertThat(payload.consistencyEligible).isEqualTo(8)
        assertThat(payload.topStat).isEqualTo(Stat.STR) // tie with INT goes by stat order
        assertThat(payload.levelsGained).isEqualTo(LevelCurve.level(1_000) - 1)
        assertThat(payload.hasBody).isFalse()
        assertThat(payload.distanceKm).isNull()
    }

    @Test
    fun `health averages use only days with data`() {
        val period = RecapRules.previousMonth(LocalDate.of(2026, 11, 2))
        val payload = RecapRules.build(
            RecapInput(
                period, 0, 0, emptyList(), emptyMap(), emptyMap(), 0, 0,
                health = listOf(
                    RecapHealthDay(distanceMeters = 5_250.0, steps = 8_000, sleepMinutes = 450),
                    RecapHealthDay(steps = 0, sleepMinutes = 390),
                    RecapHealthDay(distanceMeters = 3_000.0, steps = 6_000),
                ),
                achievementsEarned = 0,
            ),
        )
        assertThat(payload.distanceKm).isEqualTo(8.3)
        assertThat(payload.averageSteps).isEqualTo(7_000)
        assertThat(payload.averageSleepHours).isEqualTo(7.0)
        assertThat(payload.gateHours).isNull()
        assertThat(payload.bestStreak).isNull()
        assertThat(payload.topStat).isNull()
    }
}
