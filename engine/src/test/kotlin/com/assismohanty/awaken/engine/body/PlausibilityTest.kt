package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** GD §18 vectors G-10 and G-11 (plausibility). */
class PlausibilityTest {
    private fun session(day: Int, weight: Double, reps: Int) =
        PlausibilitySession(LocalDate.of(2026, 11, day), listOf(LoggedSet(weight, reps)))

    // 100 kg x 1 = e1RM 100, in three earlier sessions.
    private val base = listOf(session(1, 100.0, 1), session(3, 100.0, 1), session(5, 100.0, 1))

    @Test
    fun `G-10 a jump over 20 percent is flagged and the benchmark stays put`() {
        val r = Plausibility.replay(base + session(7, 125.0, 1), bodyWeightKg = 80.0, kind = LiftKind.BENCH)
        assertThat(r.flags.last()).containsExactly(true)
        assertThat(r.bestConfirmedKg).isEqualTo(100.0)
        assertThat(r.pending).hasSize(1)
    }

    @Test
    fun `G-11 a later game-day set at 95 percent confirms it`() {
        val r = Plausibility.replay(base + session(7, 125.0, 1) + session(9, 120.0, 1), bodyWeightKg = 80.0, kind = LiftKind.BENCH)
        assertThat(r.bestConfirmedKg).isEqualTo(125.0)
        assertThat(r.pending).isEmpty()
        // The confirming set was judged against the new best, so it is not a fresh jump.
        assertThat(r.flags.last()).containsExactly(false)
    }

    @Test
    fun `the same game-day cannot confirm a flagged set`() {
        val sameDay = PlausibilitySession(LocalDate.of(2026, 11, 7), listOf(LoggedSet(125.0, 1), LoggedSet(125.0, 1)))
        val r = Plausibility.replay(base + sameDay, bodyWeightKg = 80.0, kind = LiftKind.BENCH)
        assertThat(r.pending).hasSize(2)
        assertThat(r.bestConfirmedKg).isEqualTo(100.0)
    }

    @Test
    fun `a jump needs at least two earlier sessions`() {
        val r = Plausibility.replay(listOf(session(1, 100.0, 1), session(3, 130.0, 1)), bodyWeightKg = null)
        assertThat(r.flags.last()).containsExactly(false)
        assertThat(r.bestConfirmedKg).isEqualTo(130.0)
    }

    @Test
    fun `the body-weight ceiling flags a first-ever set`() {
        val bench = Plausibility.replay(listOf(session(1, 200.0, 1)), bodyWeightKg = 70.0, kind = LiftKind.BENCH)
        assertThat(bench.flags[0]).containsExactly(true)
        val squat = Plausibility.replay(listOf(session(1, 200.0, 1)), bodyWeightKg = 70.0, kind = LiftKind.SQUAT)
        assertThat(squat.flags[0]).containsExactly(false)
        val other = Plausibility.replay(listOf(session(1, 150.0, 1)), bodyWeightKg = 70.0, kind = LiftKind.OTHER)
        assertThat(other.flags[0]).containsExactly(true)
    }

    @Test
    fun `sets with no estimate are never flagged`() {
        val r = Plausibility.replay(listOf(session(1, 0.0, 20), session(2, 50.0, 20)), bodyWeightKg = 60.0)
        assertThat(r.flags.flatten()).containsExactly(false, false)
        assertThat(r.bestConfirmedKg).isNull()
    }
}
