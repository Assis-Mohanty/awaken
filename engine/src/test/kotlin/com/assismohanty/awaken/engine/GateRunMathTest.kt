package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.GateRank
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class GateRunMathTest {

    private val start = Instant.parse("2026-10-19T10:00:00Z")
    private val dGate = GateTiming(startedAt = start, plannedSec = 25 * 60)

    private fun at(seconds: Long): Instant = start.plusSeconds(seconds)

    private fun counted(delta: BackgroundDelta): Long = (delta as BackgroundDelta.Counted).addedMs

    @Test
    fun `breach is strictly more than 90 seconds`() {
        assertThat(GateRules.isCollapsed(90_000)).isFalse()
        assertThat(GateRules.isCollapsed(90_001)).isTrue()
    }

    @Test
    fun `background time is the elapsed-realtime gap, not the wall clock`() {
        // Wall clock jumped an hour while away; elapsed time says 40 s.
        val delta = GateRunMath.backgroundOnResume(dGate, stopAt = at(60), stopElapsedMs = 1_000, nowElapsedMs = 41_000, sameBoot = true)
        assertThat(counted(delta)).isEqualTo(40_000)
    }

    @Test
    fun `background accumulates across stops and collapses past the budget`() {
        var timing = dGate
        listOf(at(60) to 50_000L, at(300) to 40_001L).forEach { (stopAt, away) ->
            val added = counted(GateRunMath.backgroundOnResume(timing, stopAt, 0, away, sameBoot = true))
            timing = timing.copy(backgroundMs = timing.backgroundMs + added)
        }
        assertThat(timing.backgroundMs).isEqualTo(90_001)
        assertThat(GateRules.isCollapsed(timing.backgroundMs)).isTrue()
        assertThat(GateRunMath.breachLeft(timing.backgroundMs)).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `app killed mid-run counts the whole gap on the next start`() {
        // Process died 10 s after the stop; the next start reads the stored stop and measures 3 min.
        val delta = GateRunMath.backgroundOnResume(dGate, at(120), stopElapsedMs = 500_000, nowElapsedMs = 680_000, sameBoot = true)
        assertThat(counted(delta)).isEqualTo(180_000)
        assertThat(GateRules.isCollapsed(counted(delta))).isTrue()
    }

    @Test
    fun `a reboot while away makes background time unknowable`() {
        val delta = GateRunMath.backgroundOnResume(dGate, at(120), stopElapsedMs = 500_000, nowElapsedMs = 5_000, sameBoot = false)
        assertThat(delta).isEqualTo(BackgroundDelta.Unknowable)
    }

    @Test
    fun `time away during the pause is free`() {
        val paused = dGate.copy(pausedAt = at(600))
        // Leave at the pause start, come back 4 min later: still paused, nothing counted.
        assertThat(counted(GateRunMath.backgroundOnResume(paused, at(600), 0, 240_000, sameBoot = true))).isEqualTo(0)
        // Stay away 6 min: the pause auto-resumes at 5:00 and the last minute counts.
        assertThat(counted(GateRunMath.backgroundOnResume(paused, at(600), 0, 360_000, sameBoot = true))).isEqualTo(60_000)
    }

    @Test
    fun `the pause stops the countdown and auto-resumes at five minutes`() {
        val paused = dGate.copy(pausedAt = at(600))
        val during = GateRunMath.phase(paused, at(660))
        assertThat(during).isEqualTo(GatePhase.Paused(remaining = Duration.ofMinutes(15), pauseLeft = Duration.ofMinutes(4)))

        val after = GateRunMath.phase(paused, at(600 + 360))
        assertThat(after).isEqualTo(GatePhase.Running(Duration.ofMinutes(14)))
        assertThat(GateRunMath.endAt(paused, at(2_000))).isEqualTo(at(25 * 60 + 300))
    }

    @Test
    fun `resuming early shortens the pause`() {
        val resumed = dGate.copy(pausedAt = at(600), resumedAt = at(690))
        assertThat(GateRunMath.isPaused(resumed, at(700))).isFalse()
        assertThat(GateRunMath.endAt(resumed, at(700))).isEqualTo(at(25 * 60 + 90))
    }

    @Test
    fun `background after the planned end does not count`() {
        // Leave 30 s before the end and return an hour later: 30 s counted, gate finished.
        val delta = GateRunMath.backgroundOnResume(dGate, at(25 * 60 - 30), 0, 3_600_000, sameBoot = true)
        assertThat(counted(delta)).isEqualTo(30_000)
        assertThat(GateRunMath.phase(dGate, at(25 * 60 + 3_570))).isEqualTo(GatePhase.Finished)
    }

    @Test
    fun `a gate starts only if it ends with a full pause before the day resets`() {
        val dayStart = Instant.parse("2026-10-20T04:00:00Z")
        // B-Gate: 90 min + 5 min pause.
        assertThat(GateRunMath.canStart(GateRank.B, dayStart.minus(Duration.ofMinutes(95)), dayStart)).isTrue()
        assertThat(GateRunMath.canStart(GateRank.B, dayStart.minus(Duration.ofMinutes(94)), dayStart)).isFalse()
        assertThat(GateRunMath.canStart(GateRank.E, dayStart.minus(Duration.ofMinutes(20)), dayStart)).isTrue()
    }
}
