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
    fun `distance is met at exactly the target kilometres`() {
        assertThat(met(Verification.HEALTH_DISTANCE, 4, HealthDay(distanceMeters = 4_000.0))).isTrue()
        assertThat(met(Verification.HEALTH_DISTANCE, 4, HealthDay(distanceMeters = 3_999.0))).isFalse()
    }

    @Test
    fun `active calories are met at exactly the target`() {
        assertThat(met(Verification.HEALTH_CALORIES, 300, HealthDay(activeKcal = 300.0))).isTrue()
        assertThat(met(Verification.HEALTH_CALORIES, 300, HealthDay(activeKcal = 299.9))).isFalse()
    }

    @Test
    fun `manual entries never count`() {
        val manualWorkout = HealthDay(exercise = listOf(HealthSession(at("07:00"), at("08:00"), manualEntry = true)))
        assertThat(met(Verification.HEALTH_EXERCISE, 30, manualWorkout)).isFalse()
        val manualSleep = HealthDay(sleep = listOf(HealthSession(at("23:00", "2026-10-18"), at("07:00"), manualEntry = true)))
        assertThat(met(Verification.HEALTH_SLEEP, 7, manualSleep)).isFalse()
    }

    @Test
    fun `more than 60000 steps in a day count as zero`() {
        assertThat(met(Verification.HEALTH_STEPS, 4_000, HealthDay(steps = 60_000))).isTrue()
        assertThat(met(Verification.HEALTH_STEPS, 4_000, HealthDay(steps = 60_001))).isFalse()
    }

    @Test
    fun `an exercise session faster than 25 km per h is dropped`() {
        // 30 min: 12.5 km is exactly 25 km/h (kept); 12.6 km is over (dropped).
        val kept = HealthDay(exercise = listOf(HealthSession(at("07:00"), at("07:30"), distanceMeters = 12_500.0)))
        val dropped = HealthDay(exercise = listOf(HealthSession(at("07:00"), at("07:30"), distanceMeters = 12_600.0)))
        assertThat(met(Verification.HEALTH_EXERCISE, 30, kept)).isTrue()
        assertThat(met(Verification.HEALTH_EXERCISE, 30, dropped)).isFalse()
    }

    @Test
    fun `a session with an average heart rate below 60 is dropped but missing heart rate is not penalised`() {
        fun day(heartRate: Int?) = HealthDay(exercise = listOf(HealthSession(at("07:00"), at("07:40"), avgHeartRate = heartRate)))
        assertThat(met(Verification.HEALTH_EXERCISE, 30, day(60))).isTrue()
        assertThat(met(Verification.HEALTH_EXERCISE, 30, day(59))).isFalse()
        assertThat(met(Verification.HEALTH_EXERCISE, 30, day(null))).isTrue()
    }

    @Test
    fun `overlapping sessions from two apps are one session and two 15 minute halves still do not add up`() {
        val sameWalkTwice = HealthDay(
            exercise = listOf(
                HealthSession(at("07:00"), at("07:20"), origin = "watch"),
                HealthSession(at("07:10"), at("07:40"), origin = "phone"),
            ),
        )
        val trusted = HealthRules.trusted(sameWalkTwice)
        assertThat(trusted.exercise).hasSize(1)
        assertThat(trusted.exercise.single().start).isEqualTo(at("07:00"))
        assertThat(trusted.exercise.single().end).isEqualTo(at("07:40"))
        assertThat(trusted.exercise.single().origin).isEqualTo("watch")
        assertThat(met(Verification.HEALTH_EXERCISE, 40, sameWalkTwice)).isTrue()
    }

    @Test
    fun `merging keeps the larger distance and averages heart rate instead of adding`() {
        val day = HealthDay(
            exercise = listOf(
                HealthSession(at("07:00"), at("07:30"), distanceMeters = 3_000.0, avgHeartRate = 120),
                HealthSession(at("07:05"), at("07:30"), distanceMeters = 2_900.0, avgHeartRate = 130),
            ),
        )
        val merged = HealthRules.trusted(day).exercise.single()
        assertThat(merged.distanceMeters).isEqualTo(3_000.0)
        assertThat(merged.avgHeartRate).isEqualTo(125)
    }

    @Test
    fun `trusted is idempotent`() {
        val day = HealthDay(
            steps = 70_000,
            exercise = listOf(
                HealthSession(at("07:00"), at("07:20")),
                HealthSession(at("07:10"), at("07:40")),
                HealthSession(at("09:00"), at("09:30"), manualEntry = true),
            ),
        )
        assertThat(HealthRules.trusted(HealthRules.trusted(day))).isEqualTo(HealthRules.trusted(day))
    }

    @Test
    fun `an empty day and non-health quests are never met`() {
        Verification.entries.forEach { assertThat(met(it, 1, HealthDay())).isFalse() }
        assertThat(met(Verification.SELF, 0, HealthDay(steps = 99_999))).isFalse()
    }

    @Test
    fun `longest sleep is the longest trusted session ending in the window, merged across apps`() {
        val day = HealthDay(
            sleep = listOf(
                HealthSession(at("22:30", "2026-10-18"), at("05:00"), origin = "a"),
                HealthSession(at("23:00", "2026-10-18"), at("06:30"), origin = "b"),
                HealthSession(at("14:00"), at("14:40"), origin = "a"),
                HealthSession(at("01:00", "2026-10-18"), at("09:00", "2026-10-18"), manualEntry = true),
            ),
        )
        assertThat(HealthRules.longestSleepMinutes(day, window)).isEqualTo(480)
        assertThat(HealthRules.longestSleepMinutes(HealthDay(), window)).isNull()
    }
}
