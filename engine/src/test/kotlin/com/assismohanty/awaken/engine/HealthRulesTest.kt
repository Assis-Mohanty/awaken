package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Verification
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HealthRulesTest {

    private val zone = ZoneOffset.UTC
    private val gameDate = LocalDate.parse("2026-10-19")

    private fun at(time: String, date: String = "2026-10-19"): Instant = Instant.parse("${date}T$time:00Z")

    private val window = HealthRules.window(gameDate, zone, dayStartHour = 4, now = at("20:00"))

    private fun met(verification: Verification, target: Int, day: HealthDay) = HealthRules.isMet(verification, target, day, window)

    @Test
    fun `the window runs from the day start until now`() {
        assertThat(window.start).isEqualTo(at("04:00"))
        assertThat(window.end).isEqualTo(at("20:00"))
    }

    @Test
    fun `steps are met at exactly the target`() {
        assertThat(met(Verification.HEALTH_STEPS, 7_000, HealthDay(steps = 7_000))).isTrue()
        assertThat(met(Verification.HEALTH_STEPS, 7_000, HealthDay(steps = 6_999))).isFalse()
    }

    @Test
    fun `one exercise session must reach the target on its own`() {
        val two15s = HealthDay(exercise = listOf(HealthSession(at("07:00"), at("07:15")), HealthSession(at("18:00"), at("18:15"))))
        assertThat(met(Verification.HEALTH_EXERCISE, 30, two15s)).isFalse()
        val one30 = HealthDay(exercise = listOf(HealthSession(at("07:00"), at("07:30"))))
        assertThat(met(Verification.HEALTH_EXERCISE, 30, one30)).isTrue()
    }

    @Test
    fun `exercise before the day start belongs to yesterday`() {
        val lateNight = HealthDay(exercise = listOf(HealthSession(at("02:00"), at("03:30"))))
        assertThat(met(Verification.HEALTH_EXERCISE, 30, lateNight)).isFalse()
    }

    @Test
    fun `last night's sleep counts when it ends after the day start`() {
        val night = HealthDay(sleep = listOf(HealthSession(at("23:00", "2026-10-18"), at("06:30"))))
        assertThat(met(Verification.HEALTH_SLEEP, 7, night)).isTrue()
        val short = HealthDay(sleep = listOf(HealthSession(at("23:30", "2026-10-18"), at("06:29"))))
        assertThat(met(Verification.HEALTH_SLEEP, 7, short)).isFalse()
    }

    @Test
    fun `sleep ending before the day start was the previous game-day`() {
        val endsAt0359 = HealthDay(sleep = listOf(HealthSession(at("20:00", "2026-10-18"), at("03:59"))))
        assertThat(met(Verification.HEALTH_SLEEP, 7, endsAt0359)).isFalse()
        val endsAt0400 = HealthDay(sleep = listOf(HealthSession(at("20:00", "2026-10-18"), at("04:00"))))
        assertThat(met(Verification.HEALTH_SLEEP, 7, endsAt0400)).isTrue()
    }

    @Test
    fun `an empty day and non-health quests are never met`() {
        Verification.entries.forEach { assertThat(met(it, 1, HealthDay())).isFalse() }
        assertThat(met(Verification.SELF, 0, HealthDay(steps = 99_999))).isFalse()
    }
}
