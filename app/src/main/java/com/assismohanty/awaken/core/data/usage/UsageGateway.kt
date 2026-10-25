package com.assismohanty.awaken.core.data.usage

import com.assismohanty.awaken.engine.AppKind
import java.time.Instant

/** An app the hunter can open from the launcher: what the Screen Time list shows (GD §6.7). */
data class InstalledApp(val packageName: String, val label: String, val kind: AppKind)

/**
 * Read-only access to Android's usage stats (ARCH §3.11). On-device only: nothing here touches the network, and the
 * numbers never leave the phone. Usage access is a special app access the hunter grants in system settings.
 */
interface UsageGateway {
    /** True when the hunter has granted usage access to AWAKEN. */
    fun hasAccess(): Boolean

    /** Foreground time per package over `[start, end)`, from the system's activity events. Empty without access. */
    suspend fun foregroundMillis(start: Instant, end: Instant): Map<String, Long>

    /** Launcher apps other than AWAKEN itself, sorted by label. */
    suspend fun launcherApps(): List<InstalledApp>
}
