package com.assismohanty.awaken.core.work

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import com.assismohanty.awaken.core.audio.ChimePlayer
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.domain.GymTracker
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The gym rest timer while the screen is off (GD §18.2). A foreground service (type `specialUse`) shows the countdown as a
 * chronometer notification with Skip and +15 s, holds a partial wake lock until the rest ends, then chimes and vibrates
 * as the Gym settings say. The end time lives on the workout, so this only waits for it; whoever clears a finished rest
 * first (this, the open screen or the fallback worker) handles it, and the others find nothing to do.
 */
@AndroidEntryPoint
class RestTimerService : Service() {
    @Inject lateinit var gym: GymTracker
    @Inject lateinit var notifications: GateNotifications
    @Inject lateinit var chimePlayer: ChimePlayer
    @Inject lateinit var uiPrefs: UiPrefsRepository
    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var waiting: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val workoutId = intent?.getStringExtra(EXTRA_WORKOUT_ID)
        val endsAt = intent?.getLongExtra(EXTRA_ENDS_AT, 0L)?.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        if (workoutId == null) {
            stop()
            return START_NOT_STICKY
        }
        // Foreground within seconds of being started, with the end time the caller already knows.
        if (endsAt != null) show(workoutId, endsAt)
        when (intent.action) {
            ACTION_SKIP -> scope.launch {
                gym.skipRest(workoutId)
                stop()
            }
            ACTION_ADD -> scope.launch {
                gym.adjustRest(workoutId, ADD_SEC)
                wait(workoutId)
            }
            else -> wait(workoutId)
        }
        return START_NOT_STICKY
    }

    private fun show(workoutId: String, endsAt: Instant) {
        val notification = notifications.restRunning(
            endsAt = endsAt,
            skip = serviceIntent(this, ACTION_SKIP, workoutId, endsAt),
            add = serviceIntent(this, ACTION_ADD, workoutId, endsAt),
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    /** Waits until the stored end time (it can move while waiting: +15 s, −15 s, skipped), then handles the end once. */
    private fun wait(workoutId: String) {
        waiting?.cancel()
        waiting = scope.launch {
            try {
                while (true) {
                    val ends = gym.workout(workoutId)?.restEndsAt ?: break
                    val left = Duration.between(clock.now(), ends).toMillis()
                    if (left > 0) {
                        hold(left)
                        show(workoutId, ends)
                        delay(left)
                        continue
                    }
                    gym.finishRestIfDue(workoutId)?.let { end ->
                        val alerts = gym.restAlerts()
                        if (end.chime) {
                            if (alerts.sound) chimePlayer.play(uiPrefs.prefs.first().chime)
                            if (alerts.vibrate) vibrate()
                        }
                        notifications.showRestOver()
                    }
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.w(e, "The rest timer service failed")
            }
            stop()
        }
    }

    /** Keeps the CPU awake for the wait; it times out by itself shortly after the rest would end. */
    private fun hold(millis: Long) {
        wakeLock?.let { if (it.isHeld) it.release() }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply { acquire(millis + WAKE_LOCK_SLACK_MS) }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun stop() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.assismohanty.awaken.REST_START"
        const val ACTION_SKIP = "com.assismohanty.awaken.REST_SKIP"
        const val ACTION_ADD = "com.assismohanty.awaken.REST_ADD"
        const val EXTRA_WORKOUT_ID = "workout_id"
        const val EXTRA_ENDS_AT = "ends_at"
        private const val NOTIFICATION_ID = 805
        private const val ADD_SEC = 15
        private const val VIBRATE_MS = 400L
        private const val WAKE_LOCK_TAG = "awaken:gym_rest"
        private const val WAKE_LOCK_SLACK_MS = 5_000L

        fun intent(context: Context, action: String, workoutId: String, endsAt: Instant): Intent =
            Intent(context, RestTimerService::class.java).setAction(action).putExtra(EXTRA_WORKOUT_ID, workoutId).putExtra(EXTRA_ENDS_AT, endsAt.toEpochMilli())

        private fun serviceIntent(context: Context, action: String, workoutId: String, endsAt: Instant) = android.app.PendingIntent.getService(
            context,
            action.hashCode(),
            intent(context, action, workoutId, endsAt),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
