package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** GD §16.3 Pomodoro cycles (vectors #96-#97). */
class PomodoroRulesTest {

    /** Walks a whole uninterrupted session and lists the phases in order. */
    private fun walk(rounds: Int): List<PomodoroStep> {
        val steps = mutableListOf(PomodoroStep(PomodoroPhase.FOCUS, 1))
        var step = steps.last()
        while (step.phase != PomodoroPhase.DONE) {
            step = when (step.phase) {
                PomodoroPhase.FOCUS -> PomodoroStep(PomodoroRules.breakAfterFocus(step.round, rounds), step.round)
                else -> PomodoroRules.afterBreak(step.phase, step.round)
            }
            steps += step
        }
        return steps
    }

    @Test
    fun `a session alternates focus and short breaks, then ends on a long break`() {
        assertThat(walk(rounds = 4).map { it.phase to it.round }).containsExactly(
            PomodoroPhase.FOCUS to 1, PomodoroPhase.SHORT_BREAK to 1,
            PomodoroPhase.FOCUS to 2, PomodoroPhase.SHORT_BREAK to 2,
            PomodoroPhase.FOCUS to 3, PomodoroPhase.SHORT_BREAK to 3,
            PomodoroPhase.FOCUS to 4, PomodoroPhase.LONG_BREAK to 4,
            PomodoroPhase.DONE to 4,
        ).inOrder()
    }

    @Test
    fun `the shortest session has two rounds and still ends on a long break`() {
        assertThat(walk(rounds = 2).map { it.phase }).containsExactly(
            PomodoroPhase.FOCUS, PomodoroPhase.SHORT_BREAK, PomodoroPhase.FOCUS, PomodoroPhase.LONG_BREAK, PomodoroPhase.DONE,
        ).inOrder()
    }

    @Test
    fun `defaults are 25 5 15 and 4 rounds`() {
        val config = PomodoroConfig()
        assertThat(listOf(config.focusMinutes, config.shortBreakMinutes, config.longBreakMinutes, config.rounds)).containsExactly(25, 5, 15, 4).inOrder()
    }

    @Test
    fun `break lengths come from the config`() {
        val config = PomodoroConfig(focusMinutes = 50, shortBreakMinutes = 10, longBreakMinutes = 30, rounds = 3)
        assertThat(PomodoroRules.breakMinutes(PomodoroPhase.SHORT_BREAK, config)).isEqualTo(10)
        assertThat(PomodoroRules.breakMinutes(PomodoroPhase.LONG_BREAK, config)).isEqualTo(30)
        assertThat(PomodoroRules.breakMinutes(PomodoroPhase.FOCUS, config)).isEqualTo(0)
    }

    @Test
    fun `ranges - focus 10 to 60 by 5, short 1 to 15, long 5 to 30 by 5, rounds 2 to 6`() {
        assertThat(listOf(10, 25, 60).all(PomodoroRules::isValidFocus)).isTrue()
        assertThat(listOf(5, 12, 65).none(PomodoroRules::isValidFocus)).isTrue()
        assertThat(listOf(1, 5, 15).all(PomodoroRules::isValidShortBreak)).isTrue()
        assertThat(listOf(0, 16).none(PomodoroRules::isValidShortBreak)).isTrue()
        assertThat(listOf(5, 15, 30).all(PomodoroRules::isValidLongBreak)).isTrue()
        assertThat(listOf(4, 17, 35).none(PomodoroRules::isValidLongBreak)).isTrue()
        assertThrows<IllegalArgumentException> { PomodoroConfig(rounds = 1) }
        assertThrows<IllegalArgumentException> { PomodoroConfig(rounds = 7) }
        assertThrows<IllegalArgumentException> { PomodoroConfig(focusMinutes = 27) }
    }

    @Test
    fun `a stored value outside the ranges snaps to a valid one`() {
        val config = PomodoroRules.safeConfig(focus = 999, shortBreak = -3, longBreak = 17, rounds = 0)
        assertThat(config).isEqualTo(PomodoroConfig(focusMinutes = 60, shortBreakMinutes = 1, longBreakMinutes = 15, rounds = 2))
    }

    @Test
    fun `vector 96 - a default focus round is a D tier custom run, and the fifth custom run of the day is only logged`() {
        assertThat(FocusRules.tierFor(PomodoroConfig().focusMinutes)).isEqualTo(GateRank.D)
        assertThat(GateRank.D.difficulty).isEqualTo(Difficulty.NORMAL)
        // 69 XP and one fragment for each of the four paid rounds (TIMER, Normal, streak 0, no crit).
        assertThat(XpCalculator.questXp(Difficulty.NORMAL, 0, com.assismohanty.awaken.engine.model.VerificationSource.TIMER)).isEqualTo(69)
        (0 until FocusRules.DAILY_CAP).forEach { paid ->
            assertThat(FocusRules.outcome(25, paidToday = paid)).isEqualTo(FocusRunOutcome.Paid(GateRank.D))
        }
        assertThat(FocusRules.outcome(25, paidToday = FocusRules.DAILY_CAP)).isEqualTo(FocusRunOutcome.OverCap)
    }

    @Test
    fun `a break is over once its end time has passed, and a skipped break ends now`() {
        assertThat(PomodoroRules.breakIsOver(endsAtMillis = 1_000, nowMillis = 999)).isFalse()
        assertThat(PomodoroRules.breakIsOver(endsAtMillis = 1_000, nowMillis = 1_000)).isTrue()
    }
}
