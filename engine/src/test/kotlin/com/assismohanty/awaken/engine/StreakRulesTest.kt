package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class StreakRulesTest {

    private val start = LocalDate.of(2026, 10, 1)
    private fun cleared(date: LocalDate) = DayRecord(date, itemsCompleted = 3)

    @Test
    fun `consecutive cleared days count up and track best`() {
        val fold = StreakRules.fold(StreakState(), (0L until 4L).map { cleared(start.plusDays(it)) })
        assertThat(fold.state.streak).isEqualTo(4)
        assertThat(fold.state.bestStreak).isEqualTo(4)
        assertThat(fold.penaltyPending).isFalse()
    }

    @Test
    fun `two items without main is not cleared, main alone is`() {
        assertThat(StreakRules.isCleared(DayRecord(start, itemsCompleted = 2))).isFalse()
        assertThat(StreakRules.isCleared(DayRecord(start, mainCompleted = true, itemsCompleted = 1))).isTrue()
    }

    @Test
    fun `gap of 5 unopened days uses the shield once then breaks the streak`() {
        val days = listOf(cleared(start), cleared(start.plusDays(1)), cleared(start.plusDays(7)))
        val fold = StreakRules.fold(StreakState(shields = 1), days)

        assertThat(fold.days.map { it.outcome }).containsExactly(
            DayOutcome.CLEARED, DayOutcome.CLEARED,
            DayOutcome.SHIELDED, DayOutcome.BROKEN, DayOutcome.BROKEN, DayOutcome.BROKEN, DayOutcome.BROKEN,
            DayOutcome.CLEARED,
        ).inOrder()
        assertThat(fold.days.map { it.gameDate }).containsExactlyElementsIn((0L..7L).map { start.plusDays(it) }).inOrder()
        assertThat(fold.state.streak).isEqualTo(1)
        assertThat(fold.state.bestStreak).isEqualTo(2)
        assertThat(fold.state.shields).isEqualTo(0)
        assertThat(fold.penaltyPending).isTrue()
    }

    @Test
    fun `uncleared rest day keeps the streak without incrementing or penalty`() {
        val days = listOf(cleared(start), DayRecord(start.plusDays(1), restDay = true), cleared(start.plusDays(2)))
        val fold = StreakRules.fold(StreakState(shields = 1), days)

        assertThat(fold.days.map { it.streakAfter }).containsExactly(1, 1, 2).inOrder()
        assertThat(fold.days[1].outcome).isEqualTo(DayOutcome.REST)
        assertThat(fold.state.shields).isEqualTo(1)
        assertThat(fold.penaltyPending).isFalse()
    }

    @Test
    fun `cleared rest day still increments`() {
        val fold = StreakRules.fold(StreakState(), listOf(cleared(start).copy(restDay = true)))
        assertThat(fold.state.streak).isEqualTo(1)
    }

    @Test
    fun `rest day must be in advance and at most one per ISO week`() {
        val today = LocalDate.of(2026, 10, 14) // Wednesday
        assertThat(StreakRules.canMarkRest(today, today, emptyList())).isFalse()
        assertThat(StreakRules.canMarkRest(today.plusDays(1), today, emptyList())).isTrue()
        assertThat(StreakRules.canMarkRest(today.plusDays(2), today, listOf(today.plusDays(1)))).isFalse()
        assertThat(StreakRules.canMarkRest(today.plusDays(5), today, listOf(today.plusDays(1)))).isTrue() // next Monday
    }

    @Test
    fun `recovery budget of 7 days per month is exhausted on the 8th`() {
        val days = listOf(cleared(start)) + (1L..8L).map { DayRecord(start.plusDays(it), recovery = true) }
        val fold = StreakRules.fold(StreakState(), days)

        assertThat(fold.days.drop(1).take(7).map { it.outcome }).containsExactlyElementsIn(List(7) { DayOutcome.RECOVERY })
        assertThat(fold.days.last().outcome).isEqualTo(DayOutcome.BROKEN)
        assertThat(fold.state.recoveryUsedIn(YearMonth.of(2026, 10))).isEqualTo(7)
        assertThat(fold.state.streak).isEqualTo(0)
    }

    @Test
    fun `recovery budget resets with the calendar month`() {
        val exhausted = StreakState(streak = 4, recoveryDaysUsed = mapOf(YearMonth.of(2026, 10) to 7))
        val (_, octResult) = StreakRules.finalizeDay(exhausted, DayRecord(LocalDate.of(2026, 10, 31), recovery = true))
        val (_, novResult) = StreakRules.finalizeDay(exhausted, DayRecord(LocalDate.of(2026, 11, 1), recovery = true))
        assertThat(octResult.outcome).isEqualTo(DayOutcome.BROKEN)
        assertThat(novResult.outcome).isEqualTo(DayOutcome.RECOVERY)
    }

    @Test
    fun `shield is used once per uncleared day and only while there is a streak`() {
        val fold = StreakRules.fold(StreakState(shields = 2), listOf(cleared(start), DayRecord(start.plusDays(1))))
        assertThat(fold.state.shields).isEqualTo(1)
        assertThat(fold.state.streak).isEqualTo(1)
        assertThat(fold.days.count { it.shieldUsed }).isEqualTo(1)

        val (zeroStreak, result) = StreakRules.finalizeDay(StreakState(streak = 0, shields = 2), DayRecord(start))
        assertThat(zeroStreak.shields).isEqualTo(2)
        assertThat(result.penaltyTriggered).isTrue()
    }

    @Test
    fun `shield cap is 2`() {
        assertThat(StreakRules.canBuyShield(1)).isTrue()
        assertThat(StreakRules.canBuyShield(2)).isFalse()
    }

    @Test
    fun `display streak adds today once cleared`() {
        val state = StreakState(streak = 5)
        assertThat(StreakRules.displayStreak(state, DayRecord(start, itemsCompleted = 1))).isEqualTo(5)
        assertThat(StreakRules.displayStreak(state, DayRecord(start, mainCompleted = true))).isEqualTo(6)
    }
}
