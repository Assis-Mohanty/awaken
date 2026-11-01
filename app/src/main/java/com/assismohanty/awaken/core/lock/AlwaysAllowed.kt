package com.assismohanty.awaken.core.lock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.assismohanty.awaken.core.data.clock.DeviceUptime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * The packages Focus Lock never covers on this phone (GD 8.2): AWAKEN, every launcher, the keyboard, the default dialer
 * and the default messages app. The built-in never-block list lives in `:engine` `FocusLockRules`. Everything but the
 * keyboard is cached for a few minutes; the keyboard is read again on every event because the hunter can switch it.
 */
@Singleton
class AlwaysAllowed @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uptime: DeviceUptime,
) {
    private var cached: Set<String> = emptySet()
    private var cachedAtMs: Long = NEVER

    /** The fixed part plus the keyboard as it is right now. */
    @Synchronized
    fun current(): Set<String> {
        val now = uptime.elapsedRealtimeMs()
        if (cachedAtMs == NEVER || now - cachedAtMs > CACHE_MS || now < cachedAtMs) {
            cached = buildSet {
                add(context.packageName)
                addAll(homePackages())
                defaultDialer()?.let(::add)
                defaultSms()?.let(::add)
            }
            cachedAtMs = now
        }
        return inputMethod()?.let { cached + it } ?: cached
    }

    private fun homePackages(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        infos.mapNotNull { it.activityInfo?.packageName }.toSet()
    }.onFailure { Timber.w(it, "Could not list launchers") }.getOrDefault(emptySet())

    private fun defaultDialer(): String? =
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

    private fun defaultSms(): String? = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()

    private fun inputMethod(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)?.packageName
    }.getOrNull()

    private companion object {
        const val CACHE_MS = 5 * 60 * 1000L
        const val NEVER = -1L
    }
}
