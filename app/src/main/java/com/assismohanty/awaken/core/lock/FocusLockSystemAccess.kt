package com.assismohanty.awaken.core.lock

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.assismohanty.awaken.engine.OemFamily
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** What Android currently allows for Focus Lock (GD 8.2): the Accessibility switch and the battery setting. */
@Singleton
class FocusLockSystemAccess @Inject constructor(@ApplicationContext private val context: Context) {
    private val component = ComponentName(context, FocusLockService::class.java)

    /** The hunter switched AWAKEN on in Android's Accessibility settings. */
    fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    /** AWAKEN is exempt from battery optimisation, so the phone does not stop the service in the background. */
    fun batteryUnrestricted(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    fun oemFamily(): OemFamily = OemFamily.of(Build.MANUFACTURER)
}

/** Opens Android screens for the Focus Lock setup; every one falls back so a missing page is never a crash. */
object FocusLockSettingsLauncher {
    fun openAccessibility(context: Context) = start(context, listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))

    fun openBatteryOptimisation(context: Context) = start(
        context,
        listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails(context)),
    )

    fun openAutostart(context: Context, family: OemFamily) = start(
        context,
        OemSettingsTargets.autostart(family).map { Intent().setComponent(ComponentName(it.packageName, it.className)) } + appDetails(context),
    )

    fun openAppDetails(context: Context) = start(context, listOf(appDetails(context)))

    fun openUsageAccess(context: Context) = start(context, listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), appDetails(context)))

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    private fun start(context: Context, candidates: List<Intent>) {
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "No settings screen for %s", intent.component ?: intent.action)
            } catch (e: SecurityException) {
                Timber.w(e, "Settings screen not allowed: %s", intent.component ?: intent.action)
            }
        }
    }
}
