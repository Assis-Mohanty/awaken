package com.assismohanty.awaken.core.protect

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.LockdownDao
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.data.repo.PauseSettings
import com.assismohanty.awaken.core.data.repo.PauseStore
import com.assismohanty.awaken.core.lock.FocusLockState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** Whether the Protect service is really up, for settings screens that must not say "watching" from permissions alone. */
interface ProtectServiceStatus {
    /** The service is in the foreground and watching. */
    val running: StateFlow<Boolean>

    /** The last check decided the service should be running (Pause hours open, a Lockdown or a covered focus session). */
    val wanted: StateFlow<Boolean>
}

/** It should run but does not: Android refused to start it, so the settings say so instead of "watching". */
val ProtectServiceStatus.stalled: Boolean get() = wanted.value && !running.value

/**
 * Decides whether the Pause service should be running right now and starts or stops it (GD §19.1). It runs only while
 * Pause is on, at least one app is chosen, both permissions are granted and the active hours (if any) are open; outside
 * the hours a one-time background job starts it again when they open, so nothing polls while there is nothing to do.
 * Called when the settings change, when the app comes to the front (a permission may have just been granted), after a
 * restart, and every half minute by the service itself.
 */
@Singleton
class ProtectController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PauseStore,
    private val lockdownDao: LockdownDao,
    private val access: ProtectSystemAccess,
    private val focusState: FocusLockState,
    private val clock: Clock,
    private val notifications: ProtectNotifications,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver, ProtectServiceStatus {

    private val runningState = MutableStateFlow(false)
    private val wantedState = MutableStateFlow(false)
    override val running: StateFlow<Boolean> = runningState.asStateFlow()
    override val wanted: StateFlow<Boolean> = wantedState.asStateFlow()

    private val serviceRunning: Boolean get() = runningState.value

    fun start() {
        scope.launch { store.settings.collect { refresh(it) } }
        // A focus session starting, pausing or ending decides whether the Focus Lock cover needs the service.
        scope.launch { focusState.snapshot.collect { refresh() } }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { refresh() }
    }

    /**
     * The service reports itself: true once it is in the foreground, false when it goes. [refresh] only starts it when it
     * is not running and stops it when it is.
     */
    fun onServiceChanged(running: Boolean) {
        runningState.value = running
        if (running) notifications.clearNotStarted()
    }

    suspend fun refresh(settings: PauseSettings? = null) {
        try {
            // A running Lockdown needs the service whatever Pause says; if a permission is gone the service is what breaks it (LD9).
            if (lockdownDao.running() != null) {
                cancelWake()
                scheduleWatchdog()
                wantedState.value = true
                if (!serviceRunning) startService()
                return
            }
            cancelWatchdog()
            focusState.refreshAccess()
            if (access.ready() && focusState.sessionRunning(clock.now())) {
                cancelWake()
                wantedState.value = true
                if (!serviceRunning) startService()
                return
            }
            val current = settings ?: store.current()
            val config = current.config
            if (!(config.enabled && config.targets.isNotEmpty() && access.ready())) {
                wantedState.value = false
                stop()
                cancelWake()
                return
            }
            val now = clock.now()
            val hours = config.hours
            if (hours == null || hours.contains(now, clock.zone())) {
                cancelWake()
                wantedState.value = true
                if (!serviceRunning) startService()
            } else {
                wantedState.value = false
                stop()
                hours.nextStart(now, clock.zone())?.let { scheduleWake(Duration.between(now, it)) } ?: cancelWake()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Updating Pause failed")
        }
    }

    private fun startService() {
        // Android may refuse a background start (no overlay exemption yet, battery limits): the next refresh tries again, and
        // a notice asks the hunter to open AWAKEN, because a start from the front is always allowed.
        runCatching { ContextCompat.startForegroundService(context, Intent(context, ProtectService::class.java)) }
            .onFailure {
                Timber.w(it, "Pause service could not be started")
                notifications.notStarted()
            }
    }

    private fun stop() {
        // Also while it is still starting (not yet in the foreground); stopping a service that is not there does nothing.
        context.stopService(Intent(context, ProtectService::class.java))
        notifications.clearNotStarted()
    }

    private fun scheduleWake(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<ProtectWakeWorker>()
            .setInitialDelay(delay.toMillis().coerceAtLeast(MIN_WAKE_DELAY_MS), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WAKE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Every 15 minutes while a Lockdown runs, make sure the service is up (PLAN §3.6). */
    private fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<ProtectWakeWorker>(WATCHDOG_MINUTES, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WATCHDOG_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancelWatchdog() {
        WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG_WORK)
    }

    private fun cancelWake() {
        WorkManager.getInstance(context).cancelUniqueWork(WAKE_WORK)
    }

    private companion object {
        const val WAKE_WORK = "protect_wake"
        const val WATCHDOG_WORK = "protect_watchdog"
        const val WATCHDOG_MINUTES = 15L
        const val MIN_WAKE_DELAY_MS = 60_000L
    }
}

/** Starts Pause when its active hours open (the service stops itself when they close). */
@HiltWorker
class ProtectWakeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val controller: ProtectController,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        controller.refresh()
        return Result.success()
    }
}
