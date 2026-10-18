package com.assismohanty.awaken.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Motion tokens (DESIGN_SYSTEM §2.5). [reduceMotion] turns every animation into a crossfade or a static state. */
@Immutable
data class AwakenMotion(
    val reduceMotion: Boolean = false,
    val quickMs: Int = 120,
    val standardMs: Int = 240,
    val revealMs: Int = 420,
    val ceremonyMs: Int = 2_200,
    val typeOnCharMs: Long = 30,
    val ceremonySkippableAfterMs: Long = 600,
    /** How long a SystemToast stays up (reading time, so not shortened by reduce-motion). */
    val toastHoldMs: Long = 3_000,
    val quickEasing: Easing = FastOutSlowInEasing,
    /** Material "emphasized decelerate". */
    val standardEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val revealEasing: Easing = LinearOutSlowInEasing,
) {
    /** Duration to use for an animation of [ms], or 0 when motion is reduced. */
    fun duration(ms: Int): Int = if (reduceMotion) 0 else ms
}

val LocalAwakenMotion = staticCompositionLocalOf { AwakenMotion() }

/** System "Remove animations" (animator duration scale 0). Combined with the in-app toggle by [AwakenTheme]. */
@Composable
fun systemReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
