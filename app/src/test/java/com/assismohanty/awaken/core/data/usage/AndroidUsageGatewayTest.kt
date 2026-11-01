package com.assismohanty.awaken.core.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The real gateway over `UsageStatsManager.queryEvents`, fed recorded events (fail closed: no data is not 0 min). */
@RunWith(AndroidJUnit4::class)
class AndroidUsageGatewayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gateway = AndroidUsageGateway(context, Dispatchers.Unconfined)
    private val start = Instant.parse("2026-10-18T04:00:00Z")
    private val end = start.plus(Duration.ofHours(20))

    private fun grantAccess(mode: Int = AppOpsManager.MODE_ALLOWED) {
        shadowOf(context.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, mode)
    }

    private fun event(pkg: String, at: Instant, type: Int) {
        shadowOf(context.getSystemService(UsageStatsManager::class.java)).addEvent(pkg, at.toEpochMilli(), type)
    }

    @Test
    fun withoutAccessTheReadIsNoAccess() = runTest {
        grantAccess(AppOpsManager.MODE_IGNORED)

        assertThat(gateway.read(start, end)).isEqualTo(UsageRead.NoAccess)
    }

    @Test
    fun noEventsAtAllIsUnavailableNotZeroMinutes() = runTest {
        grantAccess()

        assertThat(gateway.read(start, end)).isEqualTo(UsageRead.Unavailable)
    }

    @Test
    fun twelveHoursInAnAppAreCountedFromForegroundEvents() = runTest {
        grantAccess()
        event(INSTAGRAM, start.plus(Duration.ofHours(1)), UsageEvents.Event.ACTIVITY_RESUMED)
        event(INSTAGRAM, start.plus(Duration.ofHours(13)), UsageEvents.Event.ACTIVITY_PAUSED)
        event(LAUNCHER, start.plus(Duration.ofHours(13)), UsageEvents.Event.ACTIVITY_RESUMED)
        event(LAUNCHER, start.plus(Duration.ofHours(14)), UsageEvents.Event.ACTIVITY_PAUSED)

        val read = gateway.read(start, end) as UsageRead.Tracked

        assertThat(read.millis[INSTAGRAM]).isEqualTo(Duration.ofHours(12).toMillis())
        assertThat(read.millis[LAUNCHER]).isEqualTo(Duration.ofHours(1).toMillis())
    }

    @Test
    fun anAppStillOpenAtTheEndCountsUpToTheEnd() = runTest {
        grantAccess()
        event(INSTAGRAM, end.minus(Duration.ofMinutes(45)), UsageEvents.Event.ACTIVITY_RESUMED)

        val read = gateway.read(start, end) as UsageRead.Tracked

        assertThat(read.millis[INSTAGRAM]).isEqualTo(Duration.ofMinutes(45).toMillis())
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
    }
}
