package com.assismohanty.awaken.engine

import java.time.Duration
import java.time.Instant

/** Where a Lockdown session stands (GD §19.2). */
enum class LockdownStatus { RUNNING, COMPLETED, BROKEN }

/** Why a Lockdown broke. A broken session pays nothing and starts a cooldown (LD4). */
enum class LockdownBreak { EMERGENCY_EXIT, PERMISSION_REVOKED, UNCOVERED }

/**
 * What a Lockdown knows about time. Only [servedMs] is a total; the rest anchor it to the monotonic clock. The wall clock
 * is never used for timing, so changing the system time or zone can not shorten a session (GD §19.2).
 * @param servedMs time served up to the anchor
 * @param anchorElapsedMs `elapsedRealtime` when [servedMs] was last saved
 * @param anchorWall the wall clock at that moment, only to notice a changed clock
 * @param bootCount the boot [anchorElapsedMs] belongs to
 */
data class LockdownClock(
    val plannedMs: Long,
    val servedMs: Long,
    val anchorElapsedMs: Long,
    val anchorWall: Instant,
    val bootCount: Int,
)

/** What the usage log showed for a stretch the watcher was down. */
enum class GapLog { ONLY_ALLOWED, SAW_OTHER_APP, UNREADABLE }

/** Where the emergency exit is: held for 3 s, then waited out for 60 s on the session screen (LD3). */
sealed interface ExitPhase {
    data object Idle : ExitPhase
    data class Holding(val sinceMs: Long) : ExitPhase
    data class Waiting(val sinceMs: Long) : ExitPhase
    data object Done : ExitPhase
}

/** Pure Lockdown rules (GD §19.2), kept free of Android so they run without a phone. */
object LockdownRules {
    const val MIN_MINUTES = 15
    const val MAX_MINUTES = 180
    const val STEP_MINUTES = 5
    const val DEFAULT_MINUTES = 30
    const val MAX_APPS = 3
    const val EXIT_HOLD_MS = 3_000L
    const val EXIT_WAIT_MS = 60_000L
    const val COOLDOWN_MINUTES = 60L
    const val UNCOVERED_BUDGET_MS = 90_000L
    const val HEARTBEAT_MS = 30_000L
    const val PERMISSION_CHECK_MS = 5_000L
    const val DRIFT_FLAG_MS = 60_000L

    /** Lengths a Lockdown can have: 15 to 180 minutes in steps of 5. */
    val MINUTE_CHOICES: List<Int> = (MIN_MINUTES..MAX_MINUTES step STEP_MINUTES).toList()

    fun isValidMinutes(minutes: Int): Boolean = minutes in MIN_MINUTES..MAX_MINUTES && (minutes - MIN_MINUTES) % STEP_MINUTES == 0

    /** An app that can be allowed during a Lockdown: not Distracting, and not social, video or a game (LD7). */
    fun canAllow(category: AppCategory, kind: AppKind): Boolean =
        category != AppCategory.DISTRACTING && kind != AppKind.SOCIAL && kind != AppKind.VIDEO && kind != AppKind.GAME

    // --- Starting

    /** True once an hour has passed since the last broken session (LD4). */
    fun canStart(lastBrokenAt: Instant?, now: Instant): Boolean = cooldownLeft(lastBrokenAt, now).isZero

    fun cooldownLeft(lastBrokenAt: Instant?, now: Instant): Duration {
        if (lastBrokenAt == null) return Duration.ZERO
        val left = Duration.between(now, lastBrokenAt.plus(Duration.ofMinutes(COOLDOWN_MINUTES)))
        return if (left.isNegative) Duration.ZERO else left
    }

    // --- Time

    fun start(plannedMinutes: Int, nowElapsedMs: Long, nowWall: Instant, bootCount: Int): LockdownClock =
        LockdownClock(plannedMinutes * 60_000L, 0, nowElapsedMs, nowWall, bootCount)

    /**
     * Time served at this moment. On the same boot it is the saved total plus the monotonic time since the anchor; a
     * lower reading than the anchor means the clock restarted without a boot count change, so only the saved total counts.
     * After a reboot, time with the phone off counts as zero: only the saved total (the last heartbeat) counts.
     */
    fun served(clock: LockdownClock, nowElapsedMs: Long, bootCount: Int): Long {
        if (bootCount != clock.bootCount || nowElapsedMs < clock.anchorElapsedMs) return clock.servedMs.coerceAtMost(clock.plannedMs)
        return (clock.servedMs + (nowElapsedMs - clock.anchorElapsedMs)).coerceAtMost(clock.plannedMs)
    }

    fun remaining(clock: LockdownClock, nowElapsedMs: Long, bootCount: Int): Duration =
        Duration.ofMillis(clock.plannedMs - served(clock, nowElapsedMs, bootCount))

    fun isComplete(clock: LockdownClock, nowElapsedMs: Long, bootCount: Int): Boolean =
        served(clock, nowElapsedMs, bootCount) >= clock.plannedMs

    /** Saves the total so far and starts counting from this reading (a heartbeat, or the first look after a reboot). */
    fun reanchor(clock: LockdownClock, nowElapsedMs: Long, nowWall: Instant, bootCount: Int): LockdownClock =
        clock.copy(servedMs = served(clock, nowElapsedMs, bootCount), anchorElapsedMs = nowElapsedMs, anchorWall = nowWall, bootCount = bootCount)

    /** True when the wall clock moved by a different amount than the monotonic clock did (more than 60 s apart): shown in history only. */
    fun clockChanged(clock: LockdownClock, nowElapsedMs: Long, nowWall: Instant, bootCount: Int): Boolean {
        if (bootCount != clock.bootCount || nowElapsedMs < clock.anchorElapsedMs) return false
        val wall = Duration.between(clock.anchorWall, nowWall).toMillis()
        val mono = nowElapsedMs - clock.anchorElapsedMs
        return kotlin.math.abs(wall - mono) > DRIFT_FLAG_MS
    }

    // --- Covering

    /**
     * Whether an app that came to the front is covered. [coverable] is "a real app" (a launcher icon the hunter installed);
     * anything else is the phone itself and is left alone. Payment, phone and system apps, this phone's launcher and
     * keyboard, and AWAKEN are never covered; neither are the apps allowed for this session.
     */
    fun decide(packageName: String?, coverable: Boolean, allowed: Set<String>, alwaysAllowed: Set<String>): BlockDecision = when {
        packageName.isNullOrBlank() -> BlockDecision.ALLOW
        !coverable -> BlockDecision.ALLOW
        FocusLockRules.neverBlocked(packageName, alwaysAllowed) -> BlockDecision.ALLOW
        packageName in allowed -> BlockDecision.ALLOW
        else -> BlockDecision.BLOCK
    }

    // --- Gaps and permissions

    /**
     * What to do about a stretch the watcher was down for (LD6). Up to 90 s is tolerated; past that the usage log decides.
     * @return null to carry on, else the reason the session breaks
     */
    fun gapVerdict(gapMs: Long, log: GapLog): LockdownBreak? = when {
        gapMs <= UNCOVERED_BUDGET_MS -> null
        log == GapLog.ONLY_ALLOWED -> null
        else -> LockdownBreak.UNCOVERED
    }

    /** Losing usage access or the overlay permission breaks the session; fail closed (LD9). */
    fun permissionVerdict(usageAccess: Boolean, canAppearOnTop: Boolean): LockdownBreak? =
        if (usageAccess && canAppearOnTop) null else LockdownBreak.PERMISSION_REVOKED

    // --- Emergency exit

    /**
     * One step of the emergency exit (LD3). Press and hold for 3 s, then wait 60 s without leaving the session screen;
     * letting go early, or leaving the screen during the wait, starts over. [Done] means the session breaks.
     */
    fun nextExit(phase: ExitPhase, pressing: Boolean, onScreen: Boolean, nowMs: Long): ExitPhase = when (phase) {
        ExitPhase.Idle -> if (pressing && onScreen) ExitPhase.Holding(nowMs) else ExitPhase.Idle
        is ExitPhase.Holding -> when {
            !pressing || !onScreen -> ExitPhase.Idle
            nowMs - phase.sinceMs >= EXIT_HOLD_MS -> ExitPhase.Waiting(nowMs)
            else -> phase
        }
        is ExitPhase.Waiting -> when {
            !onScreen -> ExitPhase.Idle
            nowMs - phase.sinceMs >= EXIT_WAIT_MS -> ExitPhase.Done
            else -> phase
        }
        ExitPhase.Done -> ExitPhase.Done
    }
}
