package com.assismohanty.awaken.engine

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One step of a pause, shown in order. A later variant (for example a call from the mascot) is one more subclass and one
 * more branch in the overlay's `when`; nothing here changes.
 */
sealed interface PauseVariant {
    /** The breathing ring and the countdown. */
    data class Breathing(val seconds: Int) : PauseVariant

    /** One line from the bundled bank, by index into it (GD §19.1 PZ10). */
    data class Line(val index: Int) : PauseVariant

    /** The optional "What for?" chips (GD §19.1 PZ7). */
    data object Intention : PauseVariant
}

/** How a pause ended (GD §19.1 PZ3, PZ4): the app opened, or the hunter chose Not now. */
enum class PauseOutcome { CONTINUED, NOT_NOW }

/** What to show for one pause. [n] is the 1-based count of pauses of that app this game-day, this one included. */
data class PausePlan(val n: Int, val variants: List<PauseVariant>)

sealed interface PauseDecision {
    data object None : PauseDecision
    data class Show(val plan: PausePlan) : PauseDecision
}

/**
 * The daily window Pause is active in (GD §19.1 PZ8). [start] == [end] means all day. A window that crosses midnight
 * belongs to the weekday it started on, so Monday 22:00-02:00 covers Tuesday 01:00 and not Monday 01:00.
 */
data class PauseHours(val start: LocalTime, val end: LocalTime, val days: Set<DayOfWeek>) {
    fun contains(at: Instant, zone: ZoneId): Boolean {
        val local = at.atZone(zone)
        val time = local.toLocalTime()
        val day = local.dayOfWeek
        return when {
            start == end -> day in days
            start < end -> day in days && time >= start && time < end
            time >= start -> day in days
            time < end -> day.minus(1) in days
            else -> false
        }
    }

    /**
     * The first moment after [from] that a window opens, or null when no weekday is selected. A window that is already
     * open at [from] is not returned (the caller checks [contains] first). All-day windows open at midnight.
     */
    fun nextStart(from: Instant, zone: ZoneId): Instant? {
        val opens = if (start == end) LocalTime.MIDNIGHT else start
        val today = from.atZone(zone).toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in days) continue
            val at = date.atTime(opens).atZone(zone).toInstant()
            if (at.isAfter(from)) return at
        }
        return null
    }
}

/** The hunter's Pause settings. [delayBaseSeconds] is one of 3, 5, 10; [cooldownMinutes] one of 1, 5, 10, 15. */
data class PauseConfig(
    val enabled: Boolean = true,
    val targets: Set<String> = emptySet(),
    val delayBaseSeconds: Int = PauseRules.DEFAULT_DELAY_SECONDS,
    val progressive: Boolean = true,
    val cooldownMinutes: Int = PauseRules.DEFAULT_COOLDOWN_MINUTES,
    val intentionPrompt: Boolean = false,
    /** Null = all day, every day. */
    val hours: PauseHours? = null,
)

/** What is known about one target app: pauses so far this game-day and when it was last continued into. */
data class PauseAppState(val pausesToday: Int = 0, val lastContinuedAt: Instant? = null)

/**
 * Whether to pause an app that just came to the front (GD §19.1), kept pure so it runs without a phone. Pause pays
 * nothing: no XP, gold or streak, so nothing here is farmable.
 */
object PauseRules {
    const val DEFAULT_DELAY_SECONDS = 3
    const val DELAY_STEP_SECONDS = 2
    const val MAX_DELAY_SECONDS = 15
    const val DEFAULT_COOLDOWN_MINUTES = 5
    const val MAX_TARGETS = 12
    const val LINE_COUNT = 40
    const val STATS_DAYS = 7

    /** Rows older than this many game-days are deleted at rollover (PZ9). */
    const val KEEP_DAYS = 8

    val DELAY_CHOICES: List<Int> = listOf(3, 5, 10)
    val COOLDOWN_CHOICES: List<Int> = listOf(1, 5, 10, 15)

    /**
     * Windows that appear over an app without being a new app: system UI, the permission dialogs of the usual phone
     * makers, the share sheet, Google Play services. The app adds this phone's keyboard and AWAKEN itself at runtime.
     * Not verified on every phone (docs/protect/PLAN.md §3.3); a name missing here only means one extra pause.
     */
    val TRANSIENT_WINDOWS: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.intentresolver",
        "com.google.android.gms",
        "com.lbe.security.miui",
        "com.miui.securitycenter",
        "com.coloros.securitypermission",
        "com.oplus.securitypermission",
        "com.coloros.safecenter",
        "com.oneplus.security",
        "com.vivo.permissionmanager",
        "com.iqoo.secure",
    )

    /** The fixed "What for?" chips, in order (PZ7). */
    enum class Intention { REPLY, LOOK_UP, POST, BREAK, NO_REASON }

    /** The countdown for the [n]-th pause of an app today (PZ2). */
    fun delaySeconds(n: Int, config: PauseConfig): Int {
        require(n >= 1) { "n is 1-based" }
        if (!config.progressive) return config.delayBaseSeconds
        return minOf(config.delayBaseSeconds + DELAY_STEP_SECONDS * (n - 1), MAX_DELAY_SECONDS)
    }

    /** The line for a pause: deterministic, and different for each n within a game-day (PZ10). */
    fun lineIndex(gameDay: LocalDate, n: Int): Int = Math.floorMod(gameDay.toEpochDay() + n, LINE_COUNT.toLong()).toInt()

    /** An app can be picked as a target unless it is never covered, and only while there is room (PZ6). */
    fun canTarget(packageName: String, current: Set<String>, alwaysAllowed: Set<String>): Boolean =
        packageName in current || (current.size < MAX_TARGETS && !FocusLockRules.neverBlocked(packageName, alwaysAllowed))

    /**
     * The package to remember as "the app before this one" after [current] came to the front. A transient window (the
     * keyboard, system UI, a permission dialog, AWAKEN's own overlay) leaves it unchanged, so returning from one is not
     * a new open. The launcher is not transient: leaving it for an app is an open.
     */
    fun trackPrevious(previous: String?, current: String?, transient: Set<String>): String? =
        if (current.isNullOrBlank() || current in transient) previous else current

    /**
     * @param packageName the app that just came to the front
     * @param previousPackage the app before it, as kept by [trackPrevious]
     * @param gameDay today's game-day, for the line bank
     * @param alwaysAllowed this phone's launcher, keyboard, dialer, SMS and payment apps, and AWAKEN
     * @param lockdownRunning a Lockdown session is running: it covers the app itself, so no pause
     * @param lockCovering a Focus Gate is running with Focus Lock on, which covers the app itself
     */
    fun decide(
        packageName: String?,
        previousPackage: String?,
        now: Instant,
        zone: ZoneId,
        gameDay: LocalDate,
        config: PauseConfig,
        state: PauseAppState,
        alwaysAllowed: Set<String>,
        lockdownRunning: Boolean,
        lockCovering: Boolean,
    ): PauseDecision {
        val skip = !config.enabled ||
            lockdownRunning ||
            lockCovering ||
            packageName.isNullOrBlank() ||
            packageName !in config.targets ||
            packageName == previousPackage ||
            FocusLockRules.neverBlocked(packageName, alwaysAllowed) ||
            config.hours?.contains(now, zone) == false ||
            inCooldown(state, config, now)
        if (skip) return PauseDecision.None
        val n = state.pausesToday + 1
        val variants = buildList {
            add(PauseVariant.Breathing(delaySeconds(n, config)))
            add(PauseVariant.Line(lineIndex(gameDay, n)))
            if (config.intentionPrompt) add(PauseVariant.Intention)
        }
        return PauseDecision.Show(PausePlan(n, variants))
    }

    /** True for the cooldown minutes after a continue; the instant it ends the app pauses again (PZ3). */
    private fun inCooldown(state: PauseAppState, config: PauseConfig, now: Instant): Boolean {
        val last = state.lastContinuedAt ?: return false
        return now.isBefore(last.plus(Duration.ofMinutes(config.cooldownMinutes.toLong())))
    }
}
