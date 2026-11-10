package com.assismohanty.awaken.core.data.usage

import com.assismohanty.awaken.engine.AppKind
import com.assismohanty.awaken.engine.ScreenEvent
import java.time.Instant

/** An app the hunter can open from the launcher: what the Screen Time list shows (GD §6.7). */
data class InstalledApp(val packageName: String, val label: String, val kind: AppKind)

/**
 * One usage read. Only [Tracked] is real data: a missing permission or an empty event log is never "0 minutes", so
 * nothing downstream can pay for usage it never saw (GD §6.7, fail closed).
 */
sealed interface UsageRead {
    /** Usage access isn't granted. */
    data object NoAccess : UsageRead

    /** Access is granted but the system reported no events for the window (OEM restriction, cleared log, future window). */
    data object Unavailable : UsageRead

    /** Foreground time per package; packages with no foreground time are absent. */
    data class Tracked(val millis: Map<String, Long>) : UsageRead
}

/**
 * Which apps came to the foreground in a window (Focus Lock's fallback, GD 8.2). Like [UsageRead], only [Opened] is
 * real data; a missing permission or an unreadable log is never "nothing opened".
 */
sealed interface OpenedRead {
    data object NoAccess : OpenedRead
    data object Unavailable : OpenedRead
    data class Opened(val packages: Set<String>) : OpenedRead
}

/**
 * What the foreground watcher learned from one look at the event log (Pause and Lockdown, GD §19). Only [Top] and
 * [NoChange] are real; a missing permission or an unreadable log is never "nothing changed".
 */
sealed interface ForegroundRead {
    data object NoAccess : ForegroundRead
    data object Unavailable : ForegroundRead

    /** No app came to the front in the window. */
    data object NoChange : ForegroundRead

    /** Every app that came to the front in the window, oldest first, one entry per change of app. */
    data class Resumed(val apps: List<ResumedApp>) : ForegroundRead
}

/** An app that came to the front, and when. */
data class ResumedApp(val packageName: String, val at: Instant)

/**
 * Device events for pickups and the night check (GD §16.10). Like [UsageRead], only [Tracked] is real data; a missing
 * permission or an unreadable log is never "no pickups".
 */
sealed interface EventsRead {
    data object NoAccess : EventsRead
    data object Unavailable : EventsRead
    data class Tracked(val events: List<ScreenEvent>) : EventsRead
}

/** Foreground time per package when the read is real data, else null. */
val UsageRead.trackedMillis: Map<String, Long>? get() = (this as? UsageRead.Tracked)?.millis

/**
 * Read-only access to Android's usage stats (ARCH §3.11). On-device only: nothing here touches the network, and the
 * numbers never leave the phone. Usage access is a special app access the hunter grants in system settings.
 */
interface UsageGateway {
    /** True when the hunter has granted usage access to AWAKEN. */
    fun hasAccess(): Boolean

    /** Foreground time per package over `[start, end)`, from the system's activity events. */
    suspend fun read(start: Instant, end: Instant): UsageRead

    /** The packages that came to the foreground during `[start, end)`. Package names only; nothing is stored. */
    suspend fun opened(start: Instant, end: Instant): OpenedRead

    /** The apps that came to the front over `[since, until)`, with times. Package names only; nothing is stored. */
    suspend fun foreground(since: Instant, until: Instant): ForegroundRead

    /** Screen and keyguard events plus app visits over `[start, end)`. Times only; no package name leaves this read. */
    suspend fun events(start: Instant, end: Instant): EventsRead

    /** Packages that never break a phone-down night (GD §16.12): AWAKEN, the dialer and telecom apps, the clock/alarm apps. */
    suspend fun nightAllowedPackages(): Set<String>

    /** Launcher apps other than AWAKEN itself, sorted by label. */
    suspend fun launcherApps(): List<InstalledApp>
}
