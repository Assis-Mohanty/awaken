package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Verification
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Sleep stages Health Connect can report inside a sleep session (display only). */
enum class SleepStageType { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

data class SleepStage(val start: Instant, val end: Instant, val type: SleepStageType)

/**
 * A Health Connect session (exercise or sleep). Everything past start/end is optional: older sources don't report it.
 * [manualEntry] marks a record typed in by hand (GD §6.2 "Health data trust"); [origin] is the writing app's package.
 */
data class HealthSession(
    val start: Instant,
    val end: Instant,
    val manualEntry: Boolean = false,
    val origin: String? = null,
    val label: String? = null,
    val distanceMeters: Double? = null,
    val activeKcal: Double? = null,
    val avgHeartRate: Int? = null,
    val stages: List<SleepStage> = emptyList(),
) {
    val duration: Duration get() = Duration.between(start, end)
}

/** What Health Connect reported for one game-day window (ARCH §3.4). */
data class HealthDay(
    val steps: Long = 0,
    val exercise: List<HealthSession> = emptyList(),
    val sleep: List<HealthSession> = emptyList(),
    val distanceMeters: Double = 0.0,
    val activeKcal: Double = 0.0,
    val avgHeartRate: Int? = null,
)

/** The part of a game-day Health Connect is asked about: from the day start until now. */
data class HealthWindow(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant): Boolean = instant >= start && instant <= end
}

/** When a HEALTH quest counts as done (GD §6.2; ENGINE_DECISIONS #50, #81, #82). Pure: the app only reads and reports. */
object HealthRules {
    private const val MINUTES_PER_HOUR = 60L
    private const val METERS_PER_KM = 1_000.0
    private const val SECONDS_PER_HOUR = 3_600.0

    /** More steps than this in one game-day count as 0 (GD §6.2 "Health data trust"). */
    const val MAX_STEPS_PER_DAY = 60_000L

    /** An exercise session faster than this on average (km/h) is dropped. */
    const val MAX_AVG_SPEED_KMH = 25.0

    /** An exercise session with a lower average heart rate (bpm) is dropped; no heart-rate data is never penalised. */
    const val MIN_SESSION_AVG_HEART_RATE = 60

    fun window(gameDate: LocalDate, zone: ZoneId, dayStartHour: Int, now: Instant): HealthWindow =
        HealthWindow(GameDay.startOf(gameDate, zone, dayStartHour), now)

    /**
     * The day with only trustworthy data left (ENGINE_DECISIONS #82): manual records are dropped, implausible steps and
     * exercise sessions are dropped, and overlapping sessions from different apps are merged into one. Idempotent.
     * Both [isMet] and the Health screens read through this, so a faked record can neither complete a quest nor show up.
     */
    fun trusted(day: HealthDay): HealthDay = day.copy(
        steps = if (day.steps > MAX_STEPS_PER_DAY) 0 else day.steps,
        exercise = mergeOverlapping(day.exercise.filter { !it.manualEntry && isPlausibleExercise(it) }),
        sleep = mergeOverlapping(day.sleep.filter { !it.manualEntry }),
    )

    /**
     * - HEALTH_STEPS: steps over the window ≥ target.
     * - HEALTH_EXERCISE: one session that started in the window and lasted ≥ target minutes (sessions don't add up).
     * - HEALTH_SLEEP: one session that ended in the window (last night's sleep) and lasted ≥ target hours.
     * - HEALTH_DISTANCE: distance over the window ≥ target km. HEALTH_CALORIES: active kcal over the window ≥ target.
     * Non-HEALTH verifications are never met here.
     */
    fun isMet(verification: Verification, target: Int, day: HealthDay, window: HealthWindow): Boolean {
        val trusted = trusted(day)
        return when (verification) {
            Verification.HEALTH_STEPS -> trusted.steps >= target
            Verification.HEALTH_EXERCISE -> trusted.exercise.any { it.start in window && it.duration.toMinutes() >= target }
            Verification.HEALTH_SLEEP -> trusted.sleep.any { it.end in window && it.duration.toMinutes() >= target * MINUTES_PER_HOUR }
            Verification.HEALTH_DISTANCE -> trusted.distanceMeters >= target * METERS_PER_KM
            Verification.HEALTH_CALORIES -> trusted.activeKcal >= target
            Verification.SELF, Verification.TIMER, Verification.DEVICE_USAGE -> false
        }
    }

    private fun isPlausibleExercise(session: HealthSession): Boolean {
        val heartRate = session.avgHeartRate
        if (heartRate != null && heartRate < MIN_SESSION_AVG_HEART_RATE) return false
        val meters = session.distanceMeters
        val seconds = session.duration.seconds
        if (meters != null && seconds > 0) {
            val kmh = meters / METERS_PER_KM / (seconds / SECONDS_PER_HOUR)
            if (kmh > MAX_AVG_SPEED_KMH) return false
        }
        return true
    }

    /** Sessions whose time ranges overlap are the same activity reported by two apps: one session, the union range. */
    private fun mergeOverlapping(sessions: List<HealthSession>): List<HealthSession> {
        val sorted = sessions.sortedBy { it.start }
        val merged = mutableListOf<HealthSession>()
        for (session in sorted) {
            val last = merged.lastOrNull()
            if (last != null && session.start < last.end) {
                merged[merged.size - 1] = combine(last, session)
            } else {
                merged += session
            }
        }
        return merged
    }

    private fun combine(a: HealthSession, b: HealthSession): HealthSession {
        val heartRates = listOfNotNull(a.avgHeartRate, b.avgHeartRate)
        return HealthSession(
            start = minOf(a.start, b.start),
            end = maxOf(a.end, b.end),
            origin = a.origin ?: b.origin,
            label = a.label ?: b.label,
            // The same activity seen twice: take the larger reading rather than adding it up.
            distanceMeters = listOfNotNull(a.distanceMeters, b.distanceMeters).maxOrNull(),
            activeKcal = listOfNotNull(a.activeKcal, b.activeKcal).maxOrNull(),
            avgHeartRate = if (heartRates.isEmpty()) null else heartRates.average().toInt(),
            stages = if (a.stages.size >= b.stages.size) a.stages else b.stages,
        )
    }
}
