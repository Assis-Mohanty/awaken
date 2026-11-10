package com.assismohanty.awaken.core.protect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import androidx.core.app.NotificationCompat
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.repo.LexiconSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The quiet notice that stays up while Pause is watching (Android requires one for a foreground service). */
@Singleton
class ProtectNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val words: LexiconSource,
) {
    fun running(turnOff: PendingIntent): Notification {
        ensureChannel()
        val resources = words.resources(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_awaken)
            .setContentTitle(resources.getString(R.string.protect_notif_title))
            .setContentText(resources.getString(R.string.protect_notif_body))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, resources.getString(R.string.protect_notif_turn_off), turnOff)
            .build()
    }

    /** Shown while a Lockdown runs: the time left, counted down by the system. No action: a Lockdown has no end button. */
    fun lockdownRunning(endsAt: Instant): Notification {
        ensureChannel()
        val resources = words.resources(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_awaken)
            .setContentTitle(resources.getString(R.string.lockdown_notif_title))
            .setContentText(resources.getString(R.string.lockdown_notif_body))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setWhen(endsAt.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** A Lockdown ended while the app was closed (finished, or broke). */
    fun lockdownEnded(completed: Boolean) {
        ensureChannel()
        val resources = words.resources(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_awaken)
            .setContentTitle(resources.getString(if (completed) R.string.lockdown_done_title else R.string.lockdown_broken_title))
            .setContentText(resources.getString(if (completed) R.string.lockdown_notif_done_body else R.string.lockdown_broken_note))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) NotificationManagerCompat.from(context).notify(ENDED_ID, notification)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel() {
        val resources = words.resources(context)
        val channel = NotificationChannel(CHANNEL_ID, resources.getString(R.string.protect_channel_name), NotificationManager.IMPORTANCE_LOW)
            .apply { description = resources.getString(R.string.protect_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "protect"
        const val NOTIFICATION_ID = 806
        private const val ENDED_ID = 807
    }
}
