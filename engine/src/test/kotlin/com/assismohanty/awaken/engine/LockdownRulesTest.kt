package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

class LockdownRulesTest {
    private val t0 = Instant.parse("2026-11-10T10:00:00Z")
    private val min = 60_000L

    private fun start(minutes: Int = 30, elapsed: Long = 1_000_000, boot: Int = 1) = LockdownRules.start(minutes, elapsed, t0, boot)

    @Test
    fun `lengths are 15 to 180 in steps of 5`() {
        assertThat(LockdownRules.MINUTE_CHOICES.first()).isEqualTo(15)
        assertThat(LockdownRules.MINUTE_CHOICES.last()).isEqualTo(180)
        assertThat(LockdownRules.MINUTE_CHOICES).hasSize(34)
        assertThat(LockdownRules.isValidMinutes(30)).isTrue()
        assertThat(listOf(10, 14, 32, 185, 0).map(LockdownRules::isValidMinutes)).containsExactly(false, false, false, false, false)
    }

    @Test
    fun `vector 187 the wall clock moving forward does not shorten a session`() {
        val clock = start()
        val now = 1_000_000 + 10 * min
        assertThat(LockdownRules.remaining(clock, now, 1)).isEqualTo(Duration.ofMinutes(20))
        // Three hours later by the wall clock, ten minutes later by the monotonic one.
        assertThat(LockdownRules.clockChanged(clock, now, t0.plus(Duration.ofHours(3)), 1)).isTrue()
        assertThat(LockdownRules.remaining(clock, now, 1)).isEqualTo(Duration.ofMinutes(20))
    }

    @Test
    fun `vector 188 the wall clock moving back changes nothing either`() {
        val clock = start()
        val now = 1_000_000 + 10 * min
        assertThat(LockdownRules.clockChanged(clock, now, t0.minus(Duration.ofHours(2)), 1)).isTrue()
        assertThat(LockdownRules.remaining(clock, now, 1)).isEqualTo(Duration.ofMinutes(20))
    }

    @Test
    fun `a clock that kept time is not flagged`() {
        val clock = start()
        val now = 1_000_000 + 10 * min
        assertThat(LockdownRules.clockChanged(clock, now, t0.plus(Duration.ofMinutes(10)), 1)).isFalse()
        assertThat(LockdownRules.clockChanged(clock, now, t0.plus(Duration.ofMinutes(10)).plusSeconds(59), 1)).isFalse()
    }

    @Test
    fun `vector 189 after a reboot the session resumes from the last saved total`() {
        // 10 min and 30 s served at the last heartbeat; the phone was off; a new boot starts elapsed time near zero.
        val saved = start().copy(servedMs = 10 * min + 30_000)
        assertThat(LockdownRules.remaining(saved, nowElapsedMs = 5_000, bootCount = 2)).isEqualTo(Duration.ofMillis(30 * min - 10 * min - 30_000))
        val resumed = LockdownRules.reanchor(saved, nowElapsedMs = 5_000, nowWall = t0.plus(Duration.ofHours(9)), bootCount = 2)
        assertThat(resumed.servedMs).isEqualTo(10 * min + 30_000)
        assertThat(resumed.bootCount).isEqualTo(2)
        // Counting goes on from the new anchor.
        assertThat(LockdownRules.served(resumed, 5_000 + min, 2)).isEqualTo(11 * min + 30_000)
    }

    @Test
    fun `an elapsed reading below the anchor never adds time`() {
        val clock = start()
        assertThat(LockdownRules.served(clock, nowElapsedMs = 10, bootCount = 1)).isEqualTo(0)
    }

    @Test
    fun `vector 190 the session is complete exactly at the planned length`() {
        val clock = start()
        assertThat(LockdownRules.isComplete(clock, 1_000_000 + 30 * min - 1, 1)).isFalse()
        assertThat(LockdownRules.isComplete(clock, 1_000_000 + 30 * min, 1)).isTrue()
        assertThat(LockdownRules.served(clock, 1_000_000 + 99 * min, 1)).isEqualTo(30 * min)
    }

    @Test
    fun `a heartbeat keeps the total and moves the anchor`() {
        val clock = start()
        val beat = LockdownRules.reanchor(clock, 1_000_000 + 5 * min, t0.plus(Duration.ofMinutes(5)), 1)
        assertThat(beat.servedMs).isEqualTo(5 * min)
        assertThat(LockdownRules.served(beat, 1_000_000 + 8 * min, 1)).isEqualTo(8 * min)
    }

    @Test
    fun `vector 194 the cooldown after a broken session lasts an hour`() {
        val broken = t0
        assertThat(LockdownRules.canStart(broken, t0.plus(Duration.ofMinutes(59)))).isFalse()
        assertThat(LockdownRules.canStart(broken, t0.plus(Duration.ofMinutes(60)))).isTrue()
        assertThat(LockdownRules.canStart(null, t0)).isTrue()
        assertThat(LockdownRules.cooldownLeft(broken, t0.plus(Duration.ofMinutes(45)))).isEqualTo(Duration.ofMinutes(15))
    }

    @Test
    fun `vector 191 hold three seconds then wait sixty and the session breaks`() {
        var phase: ExitPhase = ExitPhase.Idle
        phase = LockdownRules.nextExit(phase, pressing = true, onScreen = true, nowMs = 0)
        assertThat(phase).isEqualTo(ExitPhase.Holding(0))
        phase = LockdownRules.nextExit(phase, true, true, 2_999)
        assertThat(phase).isEqualTo(ExitPhase.Holding(0))
        phase = LockdownRules.nextExit(phase, true, true, 3_000)
        assertThat(phase).isEqualTo(ExitPhase.Waiting(3_000))
        // The finger can lift once the wait has begun.
        phase = LockdownRules.nextExit(phase, false, true, 62_999)
        assertThat(phase).isEqualTo(ExitPhase.Waiting(3_000))
        phase = LockdownRules.nextExit(phase, false, true, 63_000)
        assertThat(phase).isEqualTo(ExitPhase.Done)
    }

    @Test
    fun `letting go early starts over`() {
        val phase = LockdownRules.nextExit(ExitPhase.Holding(0), pressing = false, onScreen = true, nowMs = 1_000)
        assertThat(phase).isEqualTo(ExitPhase.Idle)
    }

    @Test
    fun `vector 192 leaving the screen during the wait cancels the exit`() {
        val phase = LockdownRules.nextExit(ExitPhase.Waiting(3_000), pressing = false, onScreen = false, nowMs = 62_000)
        assertThat(phase).isEqualTo(ExitPhase.Idle)
    }

    @Test
    fun `the exit cannot start while off the session screen`() {
        assertThat(LockdownRules.nextExit(ExitPhase.Idle, pressing = true, onScreen = false, nowMs = 0)).isEqualTo(ExitPhase.Idle)
    }

    @Test
    fun `vector 193 losing a permission breaks the session`() {
        assertThat(LockdownRules.permissionVerdict(usageAccess = true, canAppearOnTop = true)).isNull()
        assertThat(LockdownRules.permissionVerdict(usageAccess = false, canAppearOnTop = true)).isEqualTo(LockdownBreak.PERMISSION_REVOKED)
        assertThat(LockdownRules.permissionVerdict(usageAccess = true, canAppearOnTop = false)).isEqualTo(LockdownBreak.PERMISSION_REVOKED)
    }

    @Test
    fun `vector 195 and 196 a gap up to ninety seconds is tolerated, past it the log decides`() {
        assertThat(LockdownRules.gapVerdict(90_000, GapLog.SAW_OTHER_APP)).isNull()
        assertThat(LockdownRules.gapVerdict(90_000, GapLog.UNREADABLE)).isNull()
        assertThat(LockdownRules.gapVerdict(91_000, GapLog.SAW_OTHER_APP)).isEqualTo(LockdownBreak.UNCOVERED)
        assertThat(LockdownRules.gapVerdict(91_000, GapLog.UNREADABLE)).isEqualTo(LockdownBreak.UNCOVERED)
        assertThat(LockdownRules.gapVerdict(91_000, GapLog.ONLY_ALLOWED)).isNull()
    }

    private val always = setOf("com.assismohanty.awaken", "com.sec.android.app.launcher", "com.google.android.inputmethod.latin")
    private val allowed = setOf("com.spotify.music")

    private fun decide(pkg: String?, coverable: Boolean = true) = LockdownRules.decide(pkg, coverable, allowed, always)

    @Test
    fun `an app that is not allowed is covered`() {
        assertThat(decide("com.instagram.android")).isEqualTo(BlockDecision.BLOCK)
        assertThat(decide("com.whatsapp")).isEqualTo(BlockDecision.BLOCK)
    }

    @Test
    fun `allowed, phone, payment, launcher, AWAKEN and the phone's own windows are never covered`() {
        assertThat(decide("com.spotify.music")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.android.dialer")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.google.android.apps.nbu.paisa.user")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.sec.android.app.launcher")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.assismohanty.awaken")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.android.settings")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.vendor.permissionwindow", coverable = false)).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide(null)).isEqualTo(BlockDecision.ALLOW)
    }

    @Test
    fun `vector 197a only calm apps can be allowed`() {
        assertThat(LockdownRules.canAllow(AppCategory.NEUTRAL, AppKind.PRODUCTIVITY)).isTrue()
        assertThat(LockdownRules.canAllow(AppCategory.PRODUCTIVE, AppKind.OTHER)).isTrue()
        assertThat(LockdownRules.canAllow(AppCategory.DISTRACTING, AppKind.PRODUCTIVITY)).isFalse()
        assertThat(LockdownRules.canAllow(AppCategory.NEUTRAL, AppKind.SOCIAL)).isFalse()
        assertThat(LockdownRules.canAllow(AppCategory.NEUTRAL, AppKind.VIDEO)).isFalse()
        assertThat(LockdownRules.canAllow(AppCategory.NEUTRAL, AppKind.GAME)).isFalse()
        assertThat(LockdownRules.canAllow(AppCategory.NEUTRAL, AppKind.NEWS)).isTrue()
    }
}
