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
    fun `lengths are 15 to 180 in steps of 5 and the three quick choices are among them`() {
        assertThat(LockdownRules.MINUTE_CHOICES.first()).isEqualTo(15)
        assertThat(LockdownRules.MINUTE_CHOICES.last()).isEqualTo(180)
        assertThat(LockdownRules.MINUTE_CHOICES).hasSize(34)
        assertThat(LockdownRules.PRESET_MINUTES).containsExactly(15, 45, 60).inOrder()
        assertThat(LockdownRules.PRESET_MINUTES.all { it in LockdownRules.MINUTE_CHOICES }).isTrue()
        assertThat(LockdownRules.isValidMinutes(30)).isTrue()
        assertThat(listOf(10, 14, 32, 185, 0).map(LockdownRules::isValidMinutes)).containsExactly(false, false, false, false, false)
    }

    @Test
    fun `vector 187 the wall clock moving forward does not shorten a session`() {
        val clock = start()
        val now = 1_000_000 + 10 * min
        assertThat(LockdownRules.remaining(clock, now, 1)).isEqualTo(Duration.ofMinutes(20))
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
        val saved = start().copy(servedMs = 10 * min + 30_000)
        assertThat(LockdownRules.remaining(saved, nowElapsedMs = 5_000, bootCount = 2)).isEqualTo(Duration.ofMillis(30 * min - 10 * min - 30_000))
        val resumed = LockdownRules.reanchor(saved, nowElapsedMs = 5_000, nowWall = t0.plus(Duration.ofHours(9)), bootCount = 2)
        assertThat(resumed.servedMs).isEqualTo(10 * min + 30_000)
        assertThat(resumed.bootCount).isEqualTo(2)
        assertThat(LockdownRules.served(resumed, 5_000 + min, 2)).isEqualTo(11 * min + 30_000)
    }

    @Test
    fun `an elapsed reading below the anchor never adds time`() {
        assertThat(LockdownRules.served(start(), nowElapsedMs = 10, bootCount = 1)).isEqualTo(0)
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
        val beat = LockdownRules.reanchor(start(), 1_000_000 + 5 * min, t0.plus(Duration.ofMinutes(5)), 1)
        assertThat(beat.servedMs).isEqualTo(5 * min)
        assertThat(LockdownRules.served(beat, 1_000_000 + 8 * min, 1)).isEqualTo(8 * min)
    }

    @Test
    fun `vector 191 a shield lets you leave once a day, and nothing else does`() {
        assertThat(LockdownRules.exitCheck(shields = 1, usedToday = false)).isEqualTo(ExitCheck.ALLOWED)
        assertThat(LockdownRules.exitCheck(shields = 5, usedToday = false)).isEqualTo(ExitCheck.ALLOWED)
        assertThat(LockdownRules.exitCheck(shields = 0, usedToday = false)).isEqualTo(ExitCheck.NO_SHIELD)
        // One use per day, however many shields are left.
        assertThat(LockdownRules.exitCheck(shields = 4, usedToday = true)).isEqualTo(ExitCheck.USED_TODAY)
        assertThat(LockdownRules.exitCheck(shields = 0, usedToday = true)).isEqualTo(ExitCheck.USED_TODAY)
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

    private val launcher = "com.sec.android.app.launcher"
    private val okFromPhone = setOf("com.assismohanty.awaken", "com.google.android.inputmethod.latin", "com.google.android.dialer", "com.google.android.apps.messaging")
    private val homes = setOf(launcher)
    private val allowed = setOf("com.spotify.music")

    private fun decide(pkg: String?, coverable: Boolean = true) = LockdownRules.decide(pkg, coverable, allowed, okFromPhone, homes)

    @Test
    fun `an app that is not allowed is covered`() {
        assertThat(decide("com.instagram.android")).isEqualTo(BlockDecision.BLOCK)
        assertThat(decide("com.whatsapp")).isEqualTo(BlockDecision.BLOCK)
    }

    @Test
    fun `it is strict - the home screen, Settings and the installer are covered too`() {
        assertThat(decide(launcher, coverable = false)).isEqualTo(BlockDecision.BLOCK)
        assertThat(decide("com.android.settings", coverable = false)).isEqualTo(BlockDecision.BLOCK)
        assertThat(decide("com.android.settings")).isEqualTo(BlockDecision.BLOCK)
        assertThat(decide("com.google.android.packageinstaller", coverable = false)).isEqualTo(BlockDecision.BLOCK)
    }

    @Test
    fun `the allowed apps, the phone, payments, the keyboard, system UI and AWAKEN are let through`() {
        assertThat(decide("com.spotify.music")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.android.dialer")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.google.android.dialer")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.android.incallui")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.android.emergency")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.google.android.apps.nbu.paisa.user")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.google.android.inputmethod.latin")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.android.systemui", coverable = false)).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.assismohanty.awaken")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.vendor.permissionwindow", coverable = false)).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide(null)).isEqualTo(BlockDecision.ALLOW)
    }

    @Test
    fun `an app the hunter allowed is let through even if it would otherwise be covered`() {
        assertThat(LockdownRules.decide("com.android.settings", true, setOf("com.android.settings"), okFromPhone, homes)).isEqualTo(BlockDecision.ALLOW)
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
