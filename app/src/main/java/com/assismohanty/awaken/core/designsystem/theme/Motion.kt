package com.assismohanty.awaken.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens (DESIGN_SYSTEM §2.5): 200–300ms ease-out, nothing pulses or bounces. [reduceMotion] turns every
 * animation into a crossfade or a static state.
 */
@Immutable
data class AwakenMotion(
    val reduceMotion: Boolean = false,
    val quickMs: Int = 200,
    val standardMs: Int = 250,
    val revealMs: Int = 300,
    /** Onboarding: a step sliding in or out (docs/onboarding/PLAN.md §7). */
    val stepMs: Int = 280,
    /** Delay between consecutive items of a staggered entrance. */
    val staggerStepMs: Int = 30,
    /** A number counting up, a ring closing on the reward sheet. */
    val countUpMs: Int = 900,
    val ceremonyMs: Int = 1_600,
    val ceremonySkippableAfterMs: Long = 400,
    /** How long a SystemToast stays up (reading time, so not shortened by reduce-motion). */
    val toastHoldMs: Long = 3_000,
    val quickEasing: Easing = EaseOut,
    val standardEasing: Easing = EaseOut,
    val revealEasing: Easing = EaseOut,
) {
    /** Duration to use for an animation of [ms], or 0 when motion is reduced. */
    fun duration(ms: Int): Int = if (reduceMotion) 0 else ms
}

/** iOS-like ease-out: quick start, long gentle settle. */
private val EaseOut: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

val LocalAwakenMotion =staticCompositionLocalOf { AwakenMotion() }

/** System "Remove animations" (animator duration scale 0). Combined with the in-app toggle by [AwakenTheme]. */
@Composable
fun systemReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
