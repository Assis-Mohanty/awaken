package com.assismohanty.awaken.engine

import java.time.Duration
import java.time.Instant

/** Where a Lockdown session stands (GD §19.2). */
enum class LockdownStatus { RUNNING, COMPLETED, BROKEN }

/** Why a Lockdown broke. A broken session pays nothing. EMERGENCY_EXIT is leaving with a Lockdown shield (LD3). */
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

/** Whether a Lockdown shield can be used to leave right now (LD3). */
enum class ExitCheck { ALLOWED, NO_SHIELD, USED_TODAY }

/** Pure Lockdown rules (GD §19.2), kept free of Android so they run without a phone. */
object LockdownRules {
    const val MIN_MINUTES = 15
    const val MAX_MINUTES = 180
    const val STEP_MINUTES = 5
    const val DEFAULT_MINUTES = 45
    const val MAX_APPS = 3
    const val UNCOVERED_BUDGET_MS = 90_000L
    const val HEARTBEAT_MS = 30_000L
    const val PERMISSION_CHECK_MS = 5_000L
    const val DRIFT_FLAG_MS = 60_000L

    /** Lengths a Lockdown can have: 15 to 180 minutes in steps of 5. */
    val MINUTE_CHOICES: List<Int> = (MIN_MINUTES..MAX_MINUTES step STEP_MINUTES).toList()

    /** The three quick choices: 15 minutes, 45 minutes and an hour. */
    val PRESET_MINUTES: List<Int> = listOf(15, 45, 60)

    fun isValidMinutes(minutes: Int): Boolean = minutes in MIN_MINUTES..MAX_MINUTES && (minutes - MIN_MINUTES) % STEP_MINUTES == 0

    /** An app that can be allowed during a Lockdown: not Distracting, and not social, video or a game (LD7). */
    fun canAllow(category: AppCategory, kind: AppKind): Boolean =
        category != AppCategory.DISTRACTING && kind != AppKind.SOCIAL && kind != AppKind.VIDEO && kind != AppKind.GAME

    // --- Leaving (LD3)

    /**
     * Leaving early takes one Lockdown shield and is allowed once per game-day. With no shield, or after one was used
     * today, there is no way out until the time is up.
     */
    fun exitCheck(shields: Int, usedToday: Boolean): ExitCheck = when {
        usedToday -> ExitCheck.USED_TODAY
        shields <= 0 -> ExitCheck.NO_SHIELD
        else -> ExitCheck.ALLOWED
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

    /** Settings and the app installer: covered, so a Lockdown can not be switched off or the app removed from inside it. */
    val ALWAYS_COVER: Set<String> = setOf("com.android.settings", "com.android.packageinstaller", "com.google.android.packageinstaller")

    /**
     * Whether an app that came to the front is covered (GD §19.2). It is strict: the apps allowed for this session, the
     * phone, emergency and payment apps, the keyboard, system UI and AWAKEN itself are let through; **everything else is
     * covered, including the home screen, Settings and the installer**, and the hunter is brought back to the session.
     * @param coverable "a real app"; any other package is one of the phone's own windows (permission dialogs, fingerprint)
     * @param allowed the apps chosen for this session
     * @param okFromPhone this phone's keyboard, dialer, messages and payment apps, and AWAKEN, without its launchers
     * @param homePackages this phone's launchers
     */
    fun decide(packageName: String?, coverable: Boolean, allowed: Set<String>, okFromPhone: Set<String>, homePackages: Set<String>): BlockDecision = when {
        packageName.isNullOrBlank() -> BlockDecision.ALLOW
        packageName in allowed -> BlockDecision.ALLOW
        packageName in ALWAYS_COVER || packageName in homePackages -> BlockDecision.BLOCK
        FocusLockRules.neverBlocked(packageName, okFromPhone) -> BlockDecision.ALLOW
        !coverable -> BlockDecision.ALLOW
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
}
