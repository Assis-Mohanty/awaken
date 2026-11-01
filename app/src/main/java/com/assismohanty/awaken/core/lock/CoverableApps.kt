package com.assismohanty.awaken.core.lock

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which packages Focus Lock may ever cover (GD 8.2). Phone makers show dozens of system windows (fingerprint, screenshot
 * tools, phone managers, gesture panels) that report their own package names, so a list of "never cover" names can never be
 * complete. Instead only real apps are coverable: a package with a launcher icon that the hunter installed, or a
 * pre-installed app the hunter has updated (YouTube, Chrome). Anything else is the phone itself and is left alone.
 */
@Singleton
class CoverableApps @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = HashMap<String, Boolean>()

    @Synchronized
    fun isCoverable(packageName: String): Boolean = cache.getOrPut(packageName) { compute(packageName) }

    /** Forget what was learned, e.g. when an app is installed or updated. */
    @Synchronized
    fun invalidate() = cache.clear()

    private fun compute(packageName: String): Boolean = runCatching {
        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(packageName) == null) return@runCatching false
        val flags = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA).flags
        val system = flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updated = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        !system || updated
    }.getOrDefault(false)
}
