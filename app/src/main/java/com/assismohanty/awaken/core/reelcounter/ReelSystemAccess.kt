package com.assismohanty.awaken.core.reelcounter

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.assismohanty.awaken.core.lock.FocusLockSystemAccess
import com.assismohanty.awaken.engine.OemFamily
import com.assismohanty.awaken.reelcounter.service.ReelAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Android currently allows for Reel Counter. The battery and phone-maker checks are Focus Lock's: one setting
 * covers both services, so they are read from the same place instead of being duplicated.
 */
@Singleton
class ReelSystemAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shared: FocusLockSystemAccess,
) {
    private val component = ComponentName(context, ReelAccessibilityService::class.java)

    /** The hunter switched Reel counter on in Android's Accessibility settings. */
    fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun batteryUnrestricted(): Boolean = shared.batteryUnrestricted()

    fun oemFamily(): OemFamily = shared.oemFamily()
}
