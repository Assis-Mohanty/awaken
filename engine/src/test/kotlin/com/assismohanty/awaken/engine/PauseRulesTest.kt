package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Test

class PauseRulesTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.parse("2026-11-09")
    private val insta = "com.instagram.android"
    private val launcher = "com.sec.android.app.launcher"
    private val always = setOf("com.assismohanty.awaken", launcher, "com.google.android.inputmethod.latin")
    private val config = PauseConfig(targets = setOf(insta))

    // 2026-11-09 is a Monday.
    private fun at(text: String): Instant = Instant.parse("2026-11-09T${text}Z")

    private fun decide(
        pkg: String? = insta,
        previous: String? = launcher,
        now: Instant = at("10:00:00"),
        cfg: PauseConfig = config,
        state: PauseAppState = PauseAppState(),
        lockdown: Boolean = false,
        covering: Boolean = false,
    ) = PauseRules.decide(pkg, previous, now, zone, day, cfg, state, always, lockdown, covering)

    private fun shown(d: PauseDecision): PausePlan = (d as PauseDecision.Show).plan

    private fun delay(plan: PausePlan): Int = plan.variants.filterIsInstance<PauseVariant.Breathing>().single().seconds

    @Test
    fun `vector 178 to 180 the delay grows by 2 s and stops at 15 s`() {
        assertThat(PauseRules.delaySeconds(1, config)).isEqualTo(3)
        assertThat(PauseRules.delaySeconds(4, config)).isEqualTo(9)
        assertThat(PauseRules.delaySeconds(9, config)).isEqualTo(15)
        assertThat(PauseRules.delaySeconds(50, config)).isEqualTo(15)
    }

    @Test
    fun `progressive off keeps the base delay`() {
        val flat = config.copy(progressive = false, delayBaseSeconds = 5)
        assertThat(PauseRules.delaySeconds(1, flat)).isEqualTo(5)
        assertThat(PauseRules.delaySeconds(9, flat)).isEqualTo(5)
    }

    @Test
    fun `the n-th pause uses the pauses already counted today`() {
        val plan = shown(decide(state = PauseAppState(pausesToday = 3)))
        assertThat(plan.n).isEqualTo(4)
        assertThat(delay(plan)).isEqualTo(9)
    }

    @Test
    fun `vector 181 cooldown ends exactly at five minutes`() {
        val state = PauseAppState(lastContinuedAt = at("10:00:00"))
        assertThat(decide(now = at("10:04:59"), state = state)).isEqualTo(PauseDecision.None)
        assertThat(decide(now = at("10:05:00"), state = state)).isInstanceOf(PauseDecision.Show::class.java)
    }

    @Test
    fun `the cooldown length is a setting`() {
        val state = PauseAppState(lastContinuedAt = at("10:00:00"))
        val long = config.copy(cooldownMinutes = 15)
        assertThat(decide(cfg = long, now = at("10:14:59"), state = state)).isEqualTo(PauseDecision.None)
        assertThat(decide(cfg = long, now = at("10:15:00"), state = state)).isInstanceOf(PauseDecision.Show::class.java)
    }

    @Test
    fun `vector 182 not now starts no cooldown and the next open is the next n`() {
        // Not now records an event but leaves lastContinuedAt alone, so the next open pauses again with n + 1.
        val plan = shown(decide(now = at("10:00:10"), state = PauseAppState(pausesToday = 1, lastContinuedAt = null)))
        assertThat(plan.n).isEqualTo(2)
    }

    @Test
    fun `an app that is not a target is never paused`() {
        assertThat(decide(pkg = "com.whatsapp")).isEqualTo(PauseDecision.None)
        assertThat(decide(pkg = null)).isEqualTo(PauseDecision.None)
    }

    @Test
    fun `turned off pauses nothing`() {
        assertThat(decide(cfg = config.copy(enabled = false))).isEqualTo(PauseDecision.None)
    }

    @Test
    fun `vector 186 the same app as before is not a new open`() {
        assertThat(decide(previous = insta)).isEqualTo(PauseDecision.None)
    }

    @Test
    fun `leaving the launcher for a target is an open and leaving another app is too`() {
        assertThat(decide(previous = launcher)).isInstanceOf(PauseDecision.Show::class.java)
        assertThat(decide(previous = "com.whatsapp")).isInstanceOf(PauseDecision.Show::class.java)
        assertThat(decide(previous = null)).isInstanceOf(PauseDecision.Show::class.java)
    }

    @Test
    fun `a keyboard or system window between two looks of the same app is not an open`() {
        val transient = setOf("com.google.android.inputmethod.latin", "com.android.systemui")
        var previous: String? = PauseRules.trackPrevious(null, launcher, transient)
        previous = PauseRules.trackPrevious(previous, insta, transient)
        previous = PauseRules.trackPrevious(previous, "com.google.android.inputmethod.latin", transient)
        assertThat(previous).isEqualTo(insta)
        assertThat(decide(previous = previous)).isEqualTo(PauseDecision.None)
    }

    @Test
    fun `a package that can never be covered is not paused even if it was picked`() {
        val phone = config.copy(targets = setOf("com.android.dialer", "com.google.android.apps.nbu.paisa.user"))
        assertThat(decide(pkg = "com.android.dialer", cfg = phone)).isEqualTo(PauseDecision.None)
        assertThat(decide(pkg = "com.google.android.apps.nbu.paisa.user", cfg = phone)).isEqualTo(PauseDecision.None)
    }

    @Test
    fun `vector 185 lockdown or a covering focus lock means no pause`() {
        assertThat(decide(lockdown = true)).isEqualTo(PauseDecision.None)
        assertThat(decide(covering = true)).isEqualTo(PauseDecision.None)
    }

    @Test
    fun `vector 183 office hours Monday to Friday`() {
        val hours = PauseHours(LocalTime.of(9, 0), LocalTime.of(18, 0), DayOfWeek.entries.take(5).toSet())
        val cfg = config.copy(hours = hours)
        assertThat(decide(cfg = cfg, now = at("08:59:00"))).isEqualTo(PauseDecision.None)
        assertThat(decide(cfg = cfg, now = at("09:00:00"))).isInstanceOf(PauseDecision.Show::class.java)
        assertThat(decide(cfg = cfg, now = at("17:59:59"))).isInstanceOf(PauseDecision.Show::class.java)
        assertThat(decide(cfg = cfg, now = at("18:00:00"))).isEqualTo(PauseDecision.None)
        // Saturday 10:00.
        assertThat(decide(cfg = cfg, now = Instant.parse("2026-11-14T10:00:00Z"))).isEqualTo(PauseDecision.None)
    }

    @Test
    fun `vector 184 a window past midnight belongs to the day it started`() {
        val hours = PauseHours(LocalTime.of(22, 0), LocalTime.of(2, 0), setOf(DayOfWeek.MONDAY))
        val cfg = config.copy(hours = hours)
        assertThat(decide(cfg = cfg, now = at("22:00:00"))).isInstanceOf(PauseDecision.Show::class.java)
        assertThat(decide(cfg = cfg, now = at("09:00:00"))).isEqualTo(PauseDecision.None)
        // Tuesday 01:00 is Monday's window, Wednesday 01:00 is Tuesday's.
        assertThat(decide(cfg = cfg, now = Instant.parse("2026-11-10T01:00:00Z"))).isInstanceOf(PauseDecision.Show::class.java)
        assertThat(decide(cfg = cfg, now = Instant.parse("2026-11-11T01:00:00Z"))).isEqualTo(PauseDecision.None)
        // Monday 01:00 belongs to Sunday's window, which is not selected.
        assertThat(decide(cfg = cfg, now = at("01:00:00"))).isEqualTo(PauseDecision.None)
    }

    @Test
    fun `the next start is the next selected day's opening, never one already passed`() {
        val hours = PauseHours(LocalTime.of(9, 0), LocalTime.of(18, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        // Monday 10:00 is inside today's window, so the next opening is Wednesday 09:00.
        assertThat(hours.nextStart(at("10:00:00"), zone)).isEqualTo(Instant.parse("2026-11-11T09:00:00Z"))
        // Monday 08:00 is before it opens.
        assertThat(hours.nextStart(at("08:00:00"), zone)).isEqualTo(at("09:00:00"))
        // Wednesday 10:00 wraps to next Monday.
        assertThat(hours.nextStart(Instant.parse("2026-11-11T10:00:00Z"), zone)).isEqualTo(Instant.parse("2026-11-16T09:00:00Z"))
        assertThat(PauseHours(LocalTime.of(9, 0), LocalTime.of(18, 0), emptySet()).nextStart(at("10:00:00"), zone)).isNull()
    }

    @Test
    fun `the hours are read in the hunter's zone`() {
        val hours = PauseHours(LocalTime.of(9, 0), LocalTime.of(18, 0), DayOfWeek.entries.toSet())
        val cfg = config.copy(hours = hours)
        val kolkata = ZoneId.of("Asia/Kolkata") // 03:30Z is 09:00 there
        val early = PauseRules.decide(insta, launcher, at("03:00:00"), kolkata, day, cfg, PauseAppState(), always, false, false)
        val inside = PauseRules.decide(insta, launcher, at("03:30:00"), kolkata, day, cfg, PauseAppState(), always, false, false)
        assertThat(early).isEqualTo(PauseDecision.None)
        assertThat(inside).isInstanceOf(PauseDecision.Show::class.java)
    }

    @Test
    fun `the plan is breathing then a line, and the intention step only when it is on`() {
        val plain = shown(decide())
        assertThat(plain.variants.map { it::class }).containsExactly(PauseVariant.Breathing::class, PauseVariant.Line::class).inOrder()
        val withChips = shown(decide(cfg = config.copy(intentionPrompt = true)))
        assertThat(withChips.variants.last()).isEqualTo(PauseVariant.Intention)
    }

    @Test
    fun `the line is deterministic, in range and different for each pause of a day`() {
        val indexes = (1..PauseRules.LINE_COUNT).map { PauseRules.lineIndex(day, it) }
        assertThat(indexes).containsNoDuplicates()
        assertThat(indexes.all { it in 0 until PauseRules.LINE_COUNT }).isTrue()
        assertThat(PauseRules.lineIndex(day, 3)).isEqualTo(PauseRules.lineIndex(day, 3))
        assertThat(PauseRules.lineIndex(LocalDate.parse("1960-01-01"), 1)).isAtLeast(0)
    }

    @Test
    fun `the count starts again each game-day because the caller counts per day`() {
        // PauseAppState carries today's count only, so a new game-day arrives as pausesToday = 0.
        assertThat(shown(decide(state = PauseAppState(pausesToday = 0))).n).isEqualTo(1)
    }

    @Test
    fun `at most 12 targets and never a package that is always allowed`() {
        val twelve = (1..12).map { "com.app$it" }.toSet()
        assertThat(PauseRules.canTarget("com.app13", twelve, always)).isFalse()
        assertThat(PauseRules.canTarget("com.app3", twelve, always)).isTrue()
        assertThat(PauseRules.canTarget("com.instagram.android", emptySet(), always)).isTrue()
        assertThat(PauseRules.canTarget("com.android.dialer", emptySet(), always)).isFalse()
        assertThat(PauseRules.canTarget(launcher, emptySet(), always)).isFalse()
    }
}
