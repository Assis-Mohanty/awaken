package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.GateRank
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FocusRulesTest {
    @Test
    fun `custom 30 minutes is a D tier`() {
        assertThat(FocusRules.outcome(30, paidToday = 0)).isEqualTo(FocusRunOutcome.Paid(GateRank.D))
    }

    @Test
    fun `tier boundaries follow the preset gates`() {
        assertThat(FocusRules.tierFor(14)).isNull()
        assertThat(FocusRules.tierFor(15)).isEqualTo(GateRank.E)
        assertThat(FocusRules.tierFor(24)).isEqualTo(GateRank.E)
        assertThat(FocusRules.tierFor(25)).isEqualTo(GateRank.D)
        assertThat(FocusRules.tierFor(49)).isEqualTo(GateRank.D)
        assertThat(FocusRules.tierFor(50)).isEqualTo(GateRank.C)
        assertThat(FocusRules.tierFor(89)).isEqualTo(GateRank.C)
        assertThat(FocusRules.tierFor(90)).isEqualTo(GateRank.B)
    }

    @Test
    fun `stopwatch under 15 minutes pays nothing`() {
        assertThat(FocusRules.outcome(14, paidToday = 0)).isEqualTo(FocusRunOutcome.ShortRun)
    }

    @Test
    fun `stopwatch 120 minutes is B and no higher`() {
        assertThat(FocusRules.outcome(120, paidToday = 0)).isEqualTo(FocusRunOutcome.Paid(GateRank.B))
    }

    @Test
    fun `the fifth paid run in a game-day is over the cap`() {
        assertThat(FocusRules.outcome(30, paidToday = 3)).isInstanceOf(FocusRunOutcome.Paid::class.java)
        assertThat(FocusRules.outcome(30, paidToday = 4)).isEqualTo(FocusRunOutcome.OverCap)
    }

    @Test
    fun `a short run is short even over the cap`() {
        assertThat(FocusRules.outcome(5, paidToday = 9)).isEqualTo(FocusRunOutcome.ShortRun)
    }

    @Test
    fun `custom length snaps to 5 minute steps in 10 to 180`() {
        assertThat(FocusRules.clampCustomMinutes(3)).isEqualTo(10)
        assertThat(FocusRules.clampCustomMinutes(33)).isEqualTo(30)
        assertThat(FocusRules.clampCustomMinutes(999)).isEqualTo(180)
    }

    @Test
    fun `subject names are trimmed and limited`() {
        assertThat(FocusRules.cleanSubjectName("  ")).isNull()
        assertThat(FocusRules.cleanSubjectName(" DSA ")).isEqualTo("DSA")
        assertThat(FocusRules.cleanSubjectName("x".repeat(40))).hasLength(24)
        assertThat(FocusRules.canAddSubject(11)).isTrue()
        assertThat(FocusRules.canAddSubject(12)).isFalse()
    }
}
