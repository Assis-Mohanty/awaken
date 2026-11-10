package com.assismohanty.awaken.core.protect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.lock.FocusLockSystemAccess
import com.assismohanty.awaken.engine.OemFamily
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Android currently allows for Pause (GD §19.1): usage access to see which app opened, "appear on top" to draw the
 * pause screen, notifications for the service's notice, and the battery setting. Only the first two are required.
 */
@Singleton
class ProtectSystemAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usage: UsageGateway,
    private val focusAccess: FocusLockSystemAccess,
) {
    fun usageAccess(): Boolean = usage.hasAccess()

    fun canAppearOnTop(): Boolean = Settings.canDrawOverlays(context)

    fun notificationsAllowed(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun batteryUnrestricted(): Boolean = focusAccess.batteryUnrestricted()

    fun oem(): OemFamily = focusAccess.oemFamily()

    /** Both required permissions are on: Pause can run. */
    fun ready(): Boolean = usageAccess() && canAppearOnTop()
}
