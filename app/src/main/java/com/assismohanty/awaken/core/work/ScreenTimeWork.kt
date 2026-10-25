package com.assismohanty.awaken.core.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.data.di.ScreenTimeAlertPrefs
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.domain.DisciplineCheck
import com.assismohanty.awaken.core.domain.DisciplineSyncUseCase
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.model.QuestStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/** Remembers which Screen Discipline alerts went out, so each fires once per app per game-day (GD §6.7). */
@Singleton
class ScreenTimeAlertStore @Inject constructor(@ScreenTimeAlertPrefs private val store: DataStore<Preferences>) {
    /** Records [key] for [gameDate]; returns false when it was already recorded. Older days are dropped. */
    suspend fun markOnce(gameDate: LocalDate, key: String): Boolean {
        val entry = "$gameDate|$key"
        var fresh = false
        store.edit { prefs ->
            val today = prefs[SENT].orEmpty().filter { it.startsWith("$gameDate|") }.toSet()
            fresh = entry !in today
            prefs[SENT] = today + entry
        }
        return fresh
    }

    private companion object {
        val SENT = stringSetPreferencesKey("sent")
    }
}

/** The `screen_time` channel: the ≤ 5 min warning per app and the one "limit crossed" notice per day. */
@Singleton
class ScreenTimeNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: NotificationAccess,
) {
    @SuppressLint("MissingPermission") // guarded by NotificationAccess.isGranted()
    fun warn(appLabel: String, remainingMinutes: Int, xpAtStake: Int, id: Int) = post(
        id = id,
        title = context.getString(R.string.screentime_warning_title, appLabel),
        text = context.resources.getQuantityString(R.plurals.screentime_warning_text, remainingMinutes, remainingMinutes, xpAtStake),
    )

    fun forfeited(appLabel: String) = post(
        id = FORFEIT_ID,
        title = context.getString(R.string.screentime_forfeit_title),
        text = context.getString(R.string.screentime_forfeit_text, appLabel),
    )

    @SuppressLint("MissingPermission") // guarded by NotificationAccess.isGranted() on the first line
    private fun post(id: Int, title: String, text: String): Boolean {
        if (!access.isGranted()) return false
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.screentime_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.screentime_channel_description) },
        )
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_awaken)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    companion object {
        const val CHANNEL = "screen_time"
        const val FORFEIT_ID = 830
        /** Warning ids live in 831..899, one stable id per package. */
        fun warningId(packageName: String): Int = 831 + Math.floorMod(packageName.hashCode(), 69)
    }
}

/**
 * One Screen Discipline check (ARCH §3.11): sync (which may forfeit), send the alerts that are due, and arm a one-shot
 * check for when the closest limit should warn, so a warning isn't left waiting for the next 15-minute run.
 */
@Singleton
class ScreenTimeChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sync: DisciplineSyncUseCase,
    private val alerts: ScreenTimeAlertStore,
    private val notifications: ScreenTimeNotifications,
    private val usage: UsageGateway,
) {
    suspend fun run(): DisciplineCheck? {
        val check = sync() ?: return null
        // App names are looked up only when an alert actually goes out.
        var names: Map<String, String>? = null
        suspend fun label(pkg: String): String {
            val known = names ?: usage.launcherApps().associate { it.packageName to it.label }.also { names = it }
            return known[pkg] ?: pkg
        }
        if (check.forfeitedNow && alerts.markOnce(check.gameDate, FORFEIT_KEY)) {
            check.state.limits.firstOrNull { it.breached }?.let { notifications.forfeited(label(it.packageName)) }
        }
        check.warnings.forEach { limit ->
            if (alerts.markOnce(check.gameDate, "warn:${limit.packageName}")) {
                notifications.warn(label(limit.packageName), limit.remainingMinutes, check.xpAtStake, ScreenTimeNotifications.warningId(limit.packageName))
            }
        }
        armNextWarning(check)
        return check
    }

    /** If a limit will reach its warning point within the next periodic gap, check again right then (inexact). */
    private suspend fun armNextWarning(check: DisciplineCheck) {
        val questOpen = check.quest?.status.let { it == QuestStatus.ACTIVE || it == QuestStatus.STARTED }
        val minutes = if (questOpen) {
            check.state.limits
                .filter { !it.breached }
                .map { it.remainingMinutes - ScreenTimeRules.WARNING_MINUTES }
                .filter { it in 1 until PERIOD_MINUTES }
                .minOrNull()
        } else {
            null
        }
        val workManager = WorkManager.getInstance(context)
        if (minutes == null) {
            workManager.cancelUniqueWork(ONE_SHOT).await()
            return
        }
        workManager.enqueueUniqueWork(
            ONE_SHOT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScreenTimeWorker>().setInitialDelay(minutes.toLong(), TimeUnit.MINUTES).build(),
        ).await()
    }

    companion object {
        const val PERIODIC = "screen_time_periodic"
        const val ONE_SHOT = "screen_time_next_warning"
        const val PERIOD_MINUTES = 15
        private const val FORFEIT_KEY = "forfeit"
    }
}

/** Runs [ScreenTimeChecker] in the background: periodically every 15 minutes, and as the one-shot warning check. */
@HiltWorker
class ScreenTimeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checker: ScreenTimeChecker,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            checker.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Screen time check failed")
        }
        return Result.success()
    }
}

/**
 * Keeps the periodic check armed exactly while Screen Discipline is on, and checks once each time the app comes to the
 * foreground (so a crossing seen in the app is settled at once).
 */
@Singleton
class ScreenTimeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hunterDao: HunterDao,
    private val checker: ScreenTimeChecker,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    fun start() {
        scope.launch {
            hunterDao.observeProfile()
                .map { it?.screenTimeFrom != null }
                .distinctUntilChanged()
                .collect { enabled ->
                    try {
                        val workManager = WorkManager.getInstance(context)
                        if (enabled) {
                            workManager.enqueueUniquePeriodicWork(
                                ScreenTimeChecker.PERIODIC,
                                ExistingPeriodicWorkPolicy.KEEP,
                                PeriodicWorkRequestBuilder<ScreenTimeWorker>(ScreenTimeChecker.PERIOD_MINUTES.toLong(), TimeUnit.MINUTES).build(),
                            ).await()
                        } else {
                            workManager.cancelUniqueWork(ScreenTimeChecker.PERIODIC).await()
                            workManager.cancelUniqueWork(ScreenTimeChecker.ONE_SHOT).await()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Scheduling screen time checks failed")
                    }
                }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            try {
                if (hunterDao.observeProfile().first()?.screenTimeFrom != null) checker.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Foreground screen time check failed")
            }
        }
    }
}
