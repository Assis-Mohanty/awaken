package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.data.usage.EventsRead
import com.assismohanty.awaken.core.data.usage.InstalledApp
import com.assismohanty.awaken.core.data.usage.OpenedRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.data.usage.UsageRead
import com.assismohanty.awaken.engine.AppKind
import java.time.Instant

/** Usage access and foreground minutes a test sets directly. Usage is the same for any window asked about. */
class FakeUsageGateway(
    var access: Boolean = true,
    var apps: List<InstalledApp> = listOf(
        InstalledApp(INSTAGRAM, "Instagram", AppKind.SOCIAL),
        InstalledApp(DOCS, "Docs", AppKind.PRODUCTIVITY),
        InstalledApp(CALCULATOR, "Calculator", AppKind.OTHER),
    ),
) : UsageGateway {
    val minutes: MutableMap<String, Int> = mutableMapOf()

    /** False = access is granted but the system returned no usage events at all (OEM restriction, empty log). */
    var readable: Boolean = true

    /** Packages that "came to the foreground" in any window asked about. */
    val opened: MutableSet<String> = mutableSetOf()

    /** True = a night so idle that [read] finds no events at all, while the event log itself is alive. */
    var idle: Boolean = false

    /** Events returned for any window asked about. */
    val screenEvents: MutableList<com.assismohanty.awaken.engine.ScreenEvent> = mutableListOf()

    /** Packages that never break a night. */
    val nightAllowed: MutableSet<String> = mutableSetOf("com.assismohanty.awaken")

    override fun hasAccess(): Boolean = access

    override suspend fun read(start: Instant, end: Instant): UsageRead = when {
        !access -> UsageRead.NoAccess
        !readable || idle -> UsageRead.Unavailable
        else -> UsageRead.Tracked(minutes.filterValues { it > 0 }.mapValues { it.value * 60_000L })
    }

    override suspend fun opened(start: Instant, end: Instant): OpenedRead = when {
        !access -> OpenedRead.NoAccess
        !readable -> OpenedRead.Unavailable
        else -> OpenedRead.Opened(opened.toSet())
    }

    override suspend fun events(start: Instant, end: Instant): EventsRead = when {
        !access -> EventsRead.NoAccess
        !readable -> EventsRead.Unavailable
        else -> screenEvents.filter { !it.at.isBefore(start) && it.at.isBefore(end) }.let { found ->
            // Like the real log: a window with no events at all cannot be read.
            if (found.isEmpty()) EventsRead.Unavailable else EventsRead.Tracked(found)
        }
    }

    override suspend fun nightAllowedPackages(): Set<String> = nightAllowed.toSet()

    override suspend fun launcherApps(): List<InstalledApp> = apps

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val DOCS = "com.google.android.apps.docs"
        const val CALCULATOR = "com.example.calculator"
    }
}
