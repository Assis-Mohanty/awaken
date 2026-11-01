package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.data.usage.InstalledApp
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

    override fun hasAccess(): Boolean = access

    override suspend fun read(start: Instant, end: Instant): UsageRead = when {
        !access -> UsageRead.NoAccess
        !readable -> UsageRead.Unavailable
        else -> UsageRead.Tracked(minutes.filterValues { it > 0 }.mapValues { it.value * 60_000L })
    }

    override suspend fun launcherApps(): List<InstalledApp> = apps

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val DOCS = "com.google.android.apps.docs"
        const val CALCULATOR = "com.example.calculator"
    }
}
