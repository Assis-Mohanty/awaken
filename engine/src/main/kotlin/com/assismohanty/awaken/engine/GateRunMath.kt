package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.GateRank
import java.time.Duration
import java.time.Instant

/** Why a Focus Gate collapsed (GD §8, ENGINE_DECISIONS #34–#37). */
enum class GateCollapseReason { BREACH, DEVICE_RESTARTED, DAY_RESET }

/**
 * The timing facts of one gate run, as persisted. A run has at most one pause ([GateRules.MAX_PAUSES]): it starts at
 * [pausedAt] and ends at [resumedAt], or on its own after [GateRules.MAX_PAUSE] (ENGINE_DECISIONS #34).
 */
data class GateTiming(
    val startedAt: Instant,
    val plannedSec: Int,
    val pausedAt: Instant? = null,
    val resumedAt: Instant? = null,
    val backgroundMs: Long = 0,
)

/** Where a run stands at a moment. */
sealed interface GatePhase {
    data class Running(val remaining: Duration) : GatePhase
    data class Paused(val remaining: Duration, val pauseLeft: Duration) : GatePhase
    /** The planned time has run out with the breach budget intact: ready to claim. */
    data object Finished : GatePhase
}

/** Result of adding one stretch of background time to a run. */
sealed interface BackgroundDelta {
    data class Counted(val addedMs: Long) : BackgroundDelta
    /** The device rebooted while the app was away: elapsed time is lost, so the run cannot be trusted. */
    data object Unknowable : BackgroundDelta
}

/** Pure Focus Gate run math (ARCH §3.3): no ticking, everything derives from stored timestamps. */
object GateRunMath {

    fun planned(rank: GateRank): Duration = Duration.ofMinutes(rank.durationMinutes.toLong())

    /** A gate may start only if it ends, even with a full pause, by the next day start (ENGINE_DECISIONS #35). */
    fun canStart(rank: GateRank, now: Instant, nextDayStart: Instant): Boolean =
        !now.plus(planned(rank)).plus(GateRules.MAX_PAUSE).isAfter(nextDayStart)

    /** When the pause ends: on resume, or automatically after [GateRules.MAX_PAUSE]. Null if never paused. */
    fun pauseEndsAt(timing: GateTiming): Instant? {
        val pausedAt = timing.pausedAt ?: return null
        val cap = pausedAt.plus(GateRules.MAX_PAUSE)
        val resumed = timing.resumedAt ?: return cap
        return if (resumed.isBefore(cap)) resumed else cap
    }

    /** Pause time elapsed by [now]; the countdown does not run during it. */
    fun pausedDuration(timing: GateTiming, now: Instant): Duration {
        val pausedAt = timing.pausedAt ?: return Duration.ZERO
        val end = minOf(pauseEndsAt(timing) ?: now, now)
        return if (end.isAfter(pausedAt)) Duration.between(pausedAt, end) else Duration.ZERO
    }

    fun isPaused(timing: GateTiming, now: Instant): Boolean {
        val pausedAt = timing.pausedAt ?: return false
        val end = pauseEndsAt(timing) ?: return false
        return !now.isBefore(pausedAt) && now.isBefore(end)
    }

    /** The planned end as known at [now] (it moves while a pause is running). */
    fun endAt(timing: GateTiming, now: Instant): Instant =
        timing.startedAt.plusSeconds(timing.plannedSec.toLong()).plus(pausedDuration(timing, now))

    fun remaining(timing: GateTiming, now: Instant): Duration {
        val left = Duration.between(now, endAt(timing, now))
        return if (left.isNegative) Duration.ZERO else left
    }

    fun phase(timing: GateTiming, now: Instant): GatePhase {
        val remaining = remaining(timing, now)
        return when {
            remaining.isZero -> GatePhase.Finished
            isPaused(timing, now) -> GatePhase.Paused(
                remaining = remaining,
                pauseLeft = Duration.between(now, requireNotNull(pauseEndsAt(timing))),
            )
            else -> GatePhase.Running(remaining)
        }
    }

    /** Breach budget left before the run collapses. */
    fun breachLeft(backgroundMs: Long): Duration =
        Duration.ofMillis((GateRules.BREACH_BUDGET_MS - backgroundMs).coerceAtLeast(0))

    /**
     * Background time for one stretch away from the app, from [stopAt] (wall) and the elapsed-realtime readings at the
     * stop and on return. Elapsed time is immune to wall-clock changes but resets on reboot, so a different boot is
     * [BackgroundDelta.Unknowable]. Time inside the pause window and time after the planned end do not count
     * (ENGINE_DECISIONS #34, #37). An app killed while away is the same stretch, measured on the next start.
     */
    fun backgroundOnResume(
        timing: GateTiming,
        stopAt: Instant,
        stopElapsedMs: Long,
        nowElapsedMs: Long,
        sameBoot: Boolean,
    ): BackgroundDelta {
        if (!sameBoot) return BackgroundDelta.Unknowable
        val away = (nowElapsedMs - stopElapsedMs).coerceAtLeast(0)
        val returnAt = stopAt.plusMillis(away)
        val countedEnd = minOf(returnAt, endAt(timing, returnAt))
        if (!countedEnd.isAfter(stopAt)) return BackgroundDelta.Counted(0)
        var counted = Duration.between(stopAt, countedEnd).toMillis()
        val pausedAt = timing.pausedAt
        val pauseEnd = pauseEndsAt(timing)
        if (pausedAt != null && pauseEnd != null) {
            val overlapStart = maxOf(stopAt, pausedAt)
            val overlapEnd = minOf(countedEnd, pauseEnd)
            if (overlapEnd.isAfter(overlapStart)) counted -= Duration.between(overlapStart, overlapEnd).toMillis()
        }
        return BackgroundDelta.Counted(counted.coerceAtLeast(0))
    }
}
