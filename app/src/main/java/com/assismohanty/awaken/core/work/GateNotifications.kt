package com.assismohanty.awaken.core.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.R
import com.assismohanty.awaken.feature.quest.shortClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Focus Gate notifications (ARCH §3.3). No foreground service: the ongoing one carries a system-drawn countdown
 * (`setUsesChronometer` + `setChronometerCountDown`), so nothing in the app has to run while it's away.
 */
@Singleton
class GateNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    /** Shown when the app leaves the foreground mid-run: the countdown and how much breach budget is left. */
    fun showRunning(gateName: String, endAt: Instant, breachLeft: Duration, paused: Boolean, pauseEndsAt: Instant?) {
        val text = if (paused) {
            context.getString(R.string.gate_notif_paused)
        } else {
            context.getString(R.string.gate_notif_running, shortClock(breachLeft.toMillis()))
        }
        val countdownTo = if (paused && pauseEndsAt != null) pauseEndsAt else endAt
        val builder = base()
            .setContentTitle(context.getString(R.string.gate_notif_title, gateName))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(countdownTo.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        // Once the budget is spent the run has collapsed; don't leave a countdown that no longer means anything.
        if (!paused) builder.setTimeoutAfter(breachLeft.toMillis())
        notify(RUNNING_ID, builder)
    }

    fun showEnded(gateName: String) {
        cancelRunning()
        notify(
            ENDED_ID,
            base()
                .setContentTitle(context.getString(R.string.gate_notif_ended_title, gateName))
                .setContentText(context.getString(R.string.gate_notif_ended_body))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER),
        )
    }

    fun cancelRunning() = NotificationManagerCompat.from(context).cancel(RUNNING_ID)

    fun cancelAll() {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(RUNNING_ID)
        manager.cancel(ENDED_ID)
    }

    private fun base(): NotificationCompat.Builder {
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gate)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        // Without the permission the gate still runs; only the outside-the-app view is missing.
        if (granted) NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.gate_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.gate_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "focus_gates"
        const val RUNNING_ID = 801
        const val ENDED_ID = 802
    }
}
