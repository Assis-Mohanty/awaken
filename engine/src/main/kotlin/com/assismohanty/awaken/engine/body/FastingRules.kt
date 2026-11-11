package com.assismohanty.awaken.engine.body

import java.time.Duration
import java.time.Instant

/** Where a fast stands. [Reached] keeps counting: ending is the hunter's call, never ours. */
sealed interface FastState {
    data object Idle : FastState
    data class Running(val elapsed: Duration, val remaining: Duration) : FastState
    data class Reached(val elapsed: Duration) : FastState
}

/** Fasting timer rules (GD section 20.4): 12, 14 or 16 hours and nothing longer; no streak, no XP. Pure. */
object FastingRules {
    val PRESET_HOURS = listOf(12, 14, 16)
    val PRESET_MINUTES = PRESET_HOURS.map { it * 60 }
    private const val MINUTES_PER_HOUR = 60

    fun isPreset(targetMinutes: Int): Boolean = targetMinutes in PRESET_MINUTES

    fun hoursOf(targetMinutes: Int): Int = targetMinutes / MINUTES_PER_HOUR

    /** [startedAt] null means no fast is running. */
    fun state(startedAt: Instant?, targetMinutes: Int, now: Instant): FastState {
        if (startedAt == null) return FastState.Idle
        val elapsed = Duration.between(startedAt, now).coerceAtLeast(Duration.ZERO)
        val target = Duration.ofMinutes(targetMinutes.toLong())
        return if (elapsed >= target) FastState.Reached(elapsed) else FastState.Running(elapsed, target - elapsed)
    }
}
