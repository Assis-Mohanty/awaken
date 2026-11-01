package com.assismohanty.awaken.engine.body

import java.time.Duration
import java.time.Instant

/**
 * Per-exercise rest between sets (GD §13.2). A tracker setting, not a reward: nothing here touches XP, gold or caps.
 * `null` means no rest timer for that exercise.
 */
object RestRules {
    // Input bounds for the stepper (not game numbers).
    const val STEP_SEC = 15
    const val MAX_SEC = 600

    /** What the stepper shows first when a hunter turns rest on for an exercise with no history. */
    const val FIRST_SEC = 60

    /** A rest that ended longer ago than this is cleared silently (the phone was away); the notification already told them. */
    const val SILENT_AFTER_SEC = 10L

    fun stepUp(current: Int?): Int = if (current == null) FIRST_SEC else (current + STEP_SEC).coerceAtMost(MAX_SEC)

    /** Stepping down from the lowest step turns rest off. */
    fun stepDown(current: Int?): Int? = when {
        current == null -> null
        current <= STEP_SEC -> null
        else -> current - STEP_SEC
    }

    fun clamp(restSec: Int?): Int? = restSec?.takeIf { it > 0 }?.coerceAtMost(MAX_SEC)

    fun remaining(endsAt: Instant, now: Instant): Duration {
        val left = Duration.between(now, endsAt)
        return if (left.isNegative) Duration.ZERO else left
    }

    /** Whether the end should still sound: only if it just happened. */
    fun shouldChime(endsAt: Instant, now: Instant): Boolean = Duration.between(endsAt, now).seconds <= SILENT_AFTER_SEC
}
