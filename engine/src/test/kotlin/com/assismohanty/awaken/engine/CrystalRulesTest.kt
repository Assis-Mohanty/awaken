package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.GateRank
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.jupiter.api.Test

/** GD §16.5 gate crystal (vector #102). */
class CrystalRulesTest {
    private fun minutes(m: Long) = Duration.ofMinutes(m)

    @Test
    fun `vector 102 - 20 of 50 minutes is stage 2`() {
        assertThat(CrystalRules.stage(minutes(20), minutes(50))).isEqualTo(2)
    }

    @Test
    fun `stages step at 0 20 40 60 80 and 100 percent`() {
        val planned = minutes(50)
        assertThat((0..50 step 10).map { CrystalRules.stage(minutes(it.toLong()), planned) }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
        assertThat(CrystalRules.stage(Duration.ofSeconds(599), planned)).isEqualTo(0)
        assertThat(CrystalRules.stage(Duration.ofSeconds(600), planned)).isEqualTo(1)
        assertThat(CrystalRules.stage(minutes(80), planned)).isEqualTo(5)
    }

    @Test
    fun `nothing grows from no time, a bad plan or a negative time`() {
        assertThat(CrystalRules.stage(Duration.ZERO, minutes(25))).isEqualTo(0)
        assertThat(CrystalRules.stage(minutes(10), Duration.ZERO)).isEqualTo(0)
        assertThat(CrystalRules.stage(minutes(-5), minutes(25))).isEqualTo(0)
    }

    @Test
    fun `a stopwatch grows toward 90 minutes and stays full beyond it`() {
        val planned = CrystalRules.plannedFor(GateMode.STOPWATCH, plannedSec = 0)
        assertThat(planned).isEqualTo(minutes(90))
        assertThat(CrystalRules.stage(minutes(18), planned)).isEqualTo(1)
        assertThat(CrystalRules.stage(minutes(89), planned)).isEqualTo(4)
        assertThat(CrystalRules.stage(minutes(90), planned)).isEqualTo(5)
        assertThat(CrystalRules.stage(minutes(240), planned)).isEqualTo(5)
        assertThat(CrystalRules.plannedFor(GateMode.PRESET, 25 * 60)).isEqualTo(minutes(25))
        assertThat(CrystalRules.plannedFor(GateMode.CUSTOM, 40 * 60)).isEqualTo(minutes(40))
    }

    @Test
    fun `size follows the focus tier and short runs are the smallest`() {
        assertThat(CrystalRules.tier(5)).isEqualTo(GateRank.E)
        assertThat(CrystalRules.tier(15)).isEqualTo(GateRank.E)
        assertThat(CrystalRules.tier(30)).isEqualTo(GateRank.D)
        assertThat(CrystalRules.tier(50)).isEqualTo(GateRank.C)
        assertThat(CrystalRules.tier(120)).isEqualTo(GateRank.B)
    }

    @Test
    fun `the form follows how the run ended`() {
        assertThat(CrystalRules.form(CrystalOutcome.CLEARED)).isEqualTo(CrystalForm.WHOLE)
        assertThat(CrystalRules.form(CrystalOutcome.COLLAPSED)).isEqualTo(CrystalForm.CRACKED)
        assertThat(CrystalRules.form(CrystalOutcome.LOGGED)).isEqualTo(CrystalForm.SHARD)
    }

    @Test
    fun `a subject always gets the same colour out of six and no subject gets none`() {
        val index = CrystalRules.colourIndex("subject-maths")
        assertThat(index).isIn(0..5)
        assertThat(CrystalRules.colourIndex("subject-maths")).isEqualTo(index)
        assertThat(CrystalRules.colourIndex(null)).isNull()
        assertThat((1..60).map { CrystalRules.colourIndex("s$it") }.toSet().size).isGreaterThan(3)
        assertThat(CrystalRules.colourIndex("x")).isEqualTo(Math.floorMod(Fnv1a.hash64("x"), 6L).toInt())
    }
}
