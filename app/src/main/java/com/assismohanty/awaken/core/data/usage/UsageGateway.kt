package com.assismohanty.awaken.core.data.usage

import com.assismohanty.awaken.engine.AppKind
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

    /** Launcher apps other than AWAKEN itself, sorted by label. */
    suspend fun launcherApps(): List<InstalledApp>
}
