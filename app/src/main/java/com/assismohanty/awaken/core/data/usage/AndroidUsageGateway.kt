package com.assismohanty.awaken.core.data.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.AppKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [UsageGateway] over `UsageStatsManager.queryEvents`. Foreground time is summed from activity resume/pause pairs, which
 * is closer to what the hunter actually saw than the coarse `queryUsageStats` buckets. App labels come from the launcher
 * query allowed by the manifest's `<queries>` block (no `QUERY_ALL_PACKAGES`).
 */
@Singleton
class AndroidUsageGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UsageGateway {

    override fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        // Some OEMs leave the op at MODE_DEFAULT after the switch is turned on; then the permission itself decides.
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    override suspend fun read(start: Instant, end: Instant): UsageRead = withContext(ioDispatcher) {
        if (!hasAccess()) return@withContext UsageRead.NoAccess
        if (!end.isAfter(start)) return@withContext UsageRead.Unavailable
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return@withContext UsageRead.Unavailable
        val events = manager.queryEvents(start.toEpochMilli(), end.toEpochMilli()) ?: return@withContext UsageRead.Unavailable
        val tally = ForegroundTally(start.toEpochMilli())
        val event = UsageEvents.Event()
        var seen = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            seen++
            when (event.eventType) {
                EVENT_RESUMED -> tally.resumed(event.packageName, event.className.orEmpty(), event.timeStamp)
                EVENT_PAUSED, EVENT_STOPPED -> tally.paused(event.packageName, event.className.orEmpty(), event.timeStamp)
            }
        }
        // A phone in use always logs events (screen on, AWAKEN itself); none at all means the log can't be read.
        if (seen == 0) UsageRead.Unavailable else UsageRead.Tracked(tally.finish(end.toEpochMilli()))
    }

    override suspend fun launcherApps(): List<InstalledApp> = withContext(ioDispatcher) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info -> InstalledApp(info.packageName, pm.getApplicationLabel(info).toString(), info.kind()) }
            .sortedBy { it.label.lowercase() }
    }

    private fun ApplicationInfo.kind(): AppKind = when (category) {
        ApplicationInfo.CATEGORY_SOCIAL -> AppKind.SOCIAL
        ApplicationInfo.CATEGORY_VIDEO -> AppKind.VIDEO
        ApplicationInfo.CATEGORY_GAME -> AppKind.GAME
        ApplicationInfo.CATEGORY_NEWS -> AppKind.NEWS
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppKind.PRODUCTIVITY
        else -> AppKind.OTHER
    }

    private companion object {
        // ACTIVITY_RESUMED / ACTIVITY_PAUSED share their values with the pre-Q MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND.
        const val EVENT_RESUMED = 1
        const val EVENT_PAUSED = 2
        /** ACTIVITY_STOPPED (Q+): also ends a visit when no pause was reported. */
        const val EVENT_STOPPED = 23
    }
}

/**
 * Sums foreground time per package from resume/pause events. A package counts as visible while any of its activities
 * is resumed; a pause seen before any resume means the app was already open when the window began.
 */
internal class ForegroundTally(private val windowStart: Long) {
    private val open = HashMap<String, MutableSet<String>>()
    private val since = HashMap<String, Long>()
    private val seen = HashSet<String>()
    private val totals = HashMap<String, Long>()

    fun resumed(pkg: String, cls: String, at: Long) {
        seen += pkg
        val classes = open.getOrPut(pkg) { HashSet() }
        if (classes.isEmpty()) since[pkg] = at
        classes += cls
    }

    fun paused(pkg: String, cls: String, at: Long) {
        val classes = open[pkg]
        if (classes.isNullOrEmpty()) {
            if (pkg !in seen) add(pkg, at - windowStart)
            seen += pkg
            return
        }
        classes -= cls
        if (classes.isEmpty()) since.remove(pkg)?.let { add(pkg, at - it) }
    }

    fun finish(windowEnd: Long): Map<String, Long> {
        since.forEach { (pkg, start) -> add(pkg, windowEnd - start) }
        since.clear()
        open.clear()
        return totals.toMap()
    }

    private fun add(pkg: String, millis: Long) {
        if (millis > 0) totals[pkg] = (totals[pkg] ?: 0L) + millis
    }
}
