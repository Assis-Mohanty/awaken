package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Verification
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A Health Connect session (exercise or sleep), as start/end instants. */
data class HealthSession(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
}

/** What Health Connect reported for one game-day window (ARCH §3.4). */
data class HealthDay(
    val steps: Long = 0,
    val exercise: List<HealthSession> = emptyList(),
    val sleep: List<HealthSession> = emptyList(),
)

/** The part of a game-day Health Connect is asked about: from the day start until now. */
data class HealthWindow(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant): Boolean = instant >= start && instant <= end
}

/** When a HEALTH quest counts as done (GD §6.2; ENGINE_DECISIONS #50). Pure: the app only reads and reports. */
object HealthRules {
    private const val MINUTES_PER_HOUR = 60L

    fun window(gameDate: LocalDate, zone: ZoneId, dayStartHour: Int, now: Instant): HealthWindow =
        HealthWindow(GameDay.startOf(gameDate, zone, dayStartHour), now)

    /**
     * - HEALTH_STEPS: steps over the window ≥ target.
     * - HEALTH_EXERCISE: one session that started in the window and lasted ≥ target minutes (sessions don't add up).
     * - HEALTH_SLEEP: one session that ended in the window (last night's sleep) and lasted ≥ target hours.
     * Non-HEALTH verifications are never met here.
     */
    fun isMet(verification: Verification, target: Int, day: HealthDay, window: HealthWindow): Boolean = when (verification) {
        Verification.HEALTH_STEPS -> day.steps >= target
        Verification.HEALTH_EXERCISE -> day.exercise.any { it.start in window && it.duration.toMinutes() >= target }
        Verification.HEALTH_SLEEP -> day.sleep.any { it.end in window && it.duration.toMinutes() >= target * MINUTES_PER_HOUR }
        Verification.SELF, Verification.TIMER -> false
    }
}
