package com.assismohanty.awaken.debug

import com.assismohanty.awaken.core.data.usage.AndroidUsageGateway
import com.assismohanty.awaken.core.data.usage.EventsRead
import com.assismohanty.awaken.core.data.usage.InstalledApp
import com.assismohanty.awaken.core.data.usage.OpenedRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.data.usage.UsageRead
import com.assismohanty.awaken.core.data.usage.trackedMillis
import com.assismohanty.awaken.engine.AppKind
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug builds: the real gateway, unless simulated usage is on. Then usage access looks granted, Instagram and YouTube
 * appear in the app list even if not installed, and Instagram gets [DebugSettings.simulatedUsageMinutes] of foreground
 * time on top of whatever the device really recorded.
 */
@Singleton
class DebugUsageGateway @Inject constructor(
    private val real: AndroidUsageGateway,
    private val settings: DebugSettings,
) : UsageGateway {
    private val simulated: Int get() = settings.simulatedUsageMinutes.value

    override fun hasAccess(): Boolean = simulated > 0 || real.hasAccess()

    override suspend fun read(start: Instant, end: Instant): UsageRead {
        val read = real.read(start, end)
        if (simulated <= 0) return read
        val recorded = read.trackedMillis.orEmpty()
        return UsageRead.Tracked(recorded + (SIM_PACKAGE to (recorded[SIM_PACKAGE] ?: 0L) + simulated * MILLIS_PER_MINUTE))
    }

    override suspend fun opened(start: Instant, end: Instant): OpenedRead {
        val read = real.opened(start, end)
        if (simulated <= 0 || read !is OpenedRead.Opened) return read
        return OpenedRead.Opened(read.packages + SIM_PACKAGE)
    }

    override suspend fun events(start: Instant, end: Instant): EventsRead = real.events(start, end)

    override suspend fun nightAllowedPackages(): Set<String> = real.nightAllowedPackages()

    override suspend fun launcherApps(): List<InstalledApp> {
        val apps = real.launcherApps()
        if (simulated <= 0) return apps
        val known = apps.mapTo(HashSet()) { it.packageName }
        return (apps + FAKE_APPS.filter { it.packageName !in known }).sortedBy { it.label.lowercase() }
    }

    private companion object {
        const val SIM_PACKAGE = "com.instagram.android"
        const val MILLIS_PER_MINUTE = 60_000L
        val FAKE_APPS = listOf(
            InstalledApp(SIM_PACKAGE, "Instagram", AppKind.SOCIAL),
            InstalledApp("com.google.android.youtube", "YouTube", AppKind.VIDEO),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugUsageModule {
    @Binds
    abstract fun bindUsageGateway(impl: DebugUsageGateway): UsageGateway
}
