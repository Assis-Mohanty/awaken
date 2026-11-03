package com.assismohanty.awaken.core.designsystem.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Haptics wrapper (DESIGN_SYSTEM §2.6) that respects the in-app toggle. Uses View haptic constants only, so the app needs
 * no VIBRATE permission and follows the user's system touch-feedback setting (ENGINE_DECISIONS #26).
 */
class Haptics internal constructor(private val view: View?, private val enabled: Boolean) {
    /** Checkbox, chip select, swipe threshold. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** Quest completed. */
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)


    /** Gate collapse, penalty. */
    fun reject() = perform(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    private fun perform(constant: Int) {
        if (enabled) view?.performHapticFeedback(constant)
    }

    private companion object {
    }
}

/** Default (outside AwakenTheme, e.g. bare previews) is a no-op. */
val LocalHaptics = staticCompositionLocalOf { Haptics(view = null, enabled = false) }

@Composable
internal fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
