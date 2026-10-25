package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.data.usage.InstalledApp
import com.assismohanty.awaken.core.data.usage.UsageGateway
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

    override fun hasAccess(): Boolean = access

    override suspend fun foregroundMillis(start: Instant, end: Instant): Map<String, Long> =
        if (access) minutes.mapValues { it.value * 60_000L } else emptyMap()

    override suspend fun launcherApps(): List<InstalledApp> = apps

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val DOCS = "com.google.android.apps.docs"
        const val CALCULATOR = "com.example.calculator"
    }
}
