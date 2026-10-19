package com.assismohanty.awaken.core.work

import android.Manifest
import android.annotation.SuppressLint
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
import com.assismohanty.awaken.core.domain.ReminderContent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Intent action every notification and the widget use to land on Today (ARCH §3.5, §3.6). */
const val ACTION_OPEN_TODAY = "com.assismohanty.awaken.OPEN_TODAY"

/**
 * Whether we may post notifications, and whether the pre-prompt is still worth showing. The SDK level is a parameter
 * so tests can cover Android 10 (no runtime permission) and Android 13+ (POST_NOTIFICATIONS).
 */
@Singleton
class NotificationAccess @Inject constructor(@ApplicationContext private val context: Context) {
    // The constant is inlined at compile time and only read on Android 13+, behind the SDK check.
    @SuppressLint("InlinedApi")
    fun isGranted(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Only Android 13+ asks; older versions allow notifications by default. */
    fun canAsk(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !isGranted(sdkInt)
}

/**
 * The two daily notifications (ARCH §3.5): the directive at the reminder time and the 21:00 last call. At most one of
 * each per day (stable ids replace rather than stack), so the cap of two non-gate notifications holds by construction.
 */
@Singleton
class ReminderNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: NotificationAccess,
) {
    /** Posts [content] if the permission allows; returns whether it was posted. */
    @SuppressLint("MissingPermission") // guarded by NotificationAccess.isGranted() on the first line
    fun show(content: ReminderContent): Boolean {
        if (!access.isGranted()) return false
        val (channel, id, title, text) = when (content) {
            is ReminderContent.Directive -> Parts(
                CHANNEL_DIRECTIVE,
                DIRECTIVE_ID,
                context.getString(R.string.system_title_daily_directive),
                context.resources.getQuantityString(R.plurals.reminder_directive, content.count, content.count),
            )
            is ReminderContent.LastCall -> Parts(
                CHANNEL_LAST_CALL,
                LAST_CALL_ID,
                context.getString(R.string.reminder_last_call_title),
                context.resources.getQuantityString(R.plurals.reminder_last_call, content.remaining, content.remaining),
            )
        }
        ensureChannels()
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_awaken)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    /** Clears both, e.g. once the hunter has seen them or the day is cleared. */
    fun cancelAll() {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(DIRECTIVE_ID)
        manager.cancel(LAST_CALL_ID)
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DIRECTIVE, context.getString(R.string.directive_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.directive_channel_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LAST_CALL, context.getString(R.string.last_call_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.last_call_channel_description) },
        )
    }

    private data class Parts(val channel: String, val id: Int, val title: String, val text: String)

    companion object {
        const val CHANNEL_DIRECTIVE = "directive"
        const val CHANNEL_LAST_CALL = "last_call"
        private const val DIRECTIVE_ID = 811
        private const val LAST_CALL_ID = 812
    }
}
