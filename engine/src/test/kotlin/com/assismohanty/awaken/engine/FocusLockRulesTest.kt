package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.jupiter.api.Test

class FocusLockRulesTest {
    private val now = Instant.parse("2026-11-01T10:00:00Z")
    private val running = LockGate(running = true, paused = false, endsAt = now.plusSeconds(600))
    private val always = setOf("com.assismohanty.awaken", "com.sec.android.app.launcher", "com.google.android.inputmethod.latin")
    private val user = setOf("com.spotify.music")

    private fun decide(pkg: String?, gate: LockGate = running, at: Instant = now) =
        FocusLockRules.decide(pkg, gate, always, user, at)

    @Test
    fun `any other app is blocked while a session runs`() {
        assertThat(decide("com.instagram.android")).isEqualTo(BlockDecision.BLOCK)
        assertThat(decide("com.whatsapp")).isEqualTo(BlockDecision.BLOCK)
    }

    @Test
    fun `nothing is blocked when no session is running`() {
        assertThat(decide("com.instagram.android", LockGate(running = false, paused = false, endsAt = null))).isEqualTo(BlockDecision.ALLOW)
    }

    @Test
    fun `a paused session blocks nothing`() {
        assertThat(decide("com.instagram.android", running.copy(paused = true))).isEqualTo(BlockDecision.ALLOW)
    }

    @Test
    fun `AWAKEN, the launcher and the keyboard are never blocked`() {
        assertThat(decide("com.assismohanty.awaken")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.sec.android.app.launcher")).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.google.android.inputmethod.latin")).isEqualTo(BlockDecision.ALLOW)
    }

    @Test
    fun `system essentials are never blocked`() {
        listOf(
            "com.android.systemui", "com.android.settings", "com.google.android.dialer", "com.android.incallui",
            "com.google.android.apps.messaging", "com.android.packageinstaller", "com.android.emergency", "com.vendor.dialer.app",
        ).forEach { assertThat(decide(it)).isEqualTo(BlockDecision.ALLOW) }
    }

    @Test
    fun `an app the hunter allowed is not blocked`() {
        assertThat(decide("com.spotify.music")).isEqualTo(BlockDecision.ALLOW)
    }

    @Test
    fun `a session past its end blocks nothing`() {
        assertThat(decide("com.instagram.android", at = now.plusSeconds(600))).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("com.instagram.android", at = now.plusSeconds(599))).isEqualTo(BlockDecision.BLOCK)
    }

    @Test
    fun `a running stopwatch has no end and blocks`() {
        val stopwatch = LockGate(running = true, paused = false, endsAt = null)
        assertThat(decide("com.instagram.android", stopwatch, now.plusSeconds(99_999))).isEqualTo(BlockDecision.BLOCK)
    }

    @Test
    fun `a name that only contains dialer inside a word is still blocked`() {
        assertThat(decide("com.mydialerapp.game")).isEqualTo(BlockDecision.BLOCK)
        assertThat(decide("com.sosgames.arcade")).isEqualTo(BlockDecision.BLOCK)
    }

    @Test
    fun `a session ending mid-block stops blocking`() {
        assertThat(decide("com.instagram.android", at = now.plusSeconds(599))).isEqualTo(BlockDecision.BLOCK)
        assertThat(decide("com.instagram.android", at = now.plusSeconds(601))).isEqualTo(BlockDecision.ALLOW)
    }

    @Test
    fun `a missing or blank package is allowed`() {
        assertThat(decide(null)).isEqualTo(BlockDecision.ALLOW)
        assertThat(decide("  ")).isEqualTo(BlockDecision.ALLOW)
    }

    @Test
    fun `distracted when a Distracting app was opened`() {
        val distracting = setOf("com.instagram.android")
        assertThat(FocusLockRules.distractedDuring(setOf("com.whatsapp", "com.instagram.android"), distracting, always)).isTrue()
        assertThat(FocusLockRules.distractedDuring(setOf("com.whatsapp"), distracting, always)).isFalse()
        assertThat(FocusLockRules.distractedDuring(emptySet(), distracting, always)).isFalse()
    }

    @Test
    fun `an always-allowed app never counts as distraction even if listed`() {
        val distracting = setOf("com.sec.android.app.launcher", "com.android.settings")
        assertThat(FocusLockRules.distractedDuring(setOf("com.sec.android.app.launcher", "com.android.settings"), distracting, always)).isFalse()
    }
}
