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
 * Motion tokens (DESIGN_SYSTEM §2.5, Plates): 90-320ms, one signature move (a plate seating on the bar), nothing pulses
 * or bounces. [reduceMotion] turns every animation into a crossfade or a static state.
 */
@Immutable
data class AwakenMotion(
    val reduceMotion: Boolean = false,
    /** Press feedback. */
    val pressMs: Int = 90,
    val quickMs: Int = 150,
    val standardMs: Int = 240,
    /** A sheet rising or leaving. */
    val sheetMs: Int = 280,
    /** A plate seating on the day bar: fast, then settling, with one haptic. */
    val revealMs: Int = 320,
    /** Onboarding: a step sliding in or out (docs/onboarding/PLAN.md §7). */
    val stepMs: Int = 280,
    /** Delay between consecutive items of a staggered entrance. */
    val staggerStepMs: Int = 30,
    /** A number counting up, a ring closing on the reward sheet. */
    val countUpMs: Int = 900,
    val ceremonyMs: Int = 1_600,
    val ceremonySkippableAfterMs: Long = 400,
    /** One breath in, or one breath out, on the pause screen's ring (slow on purpose; static under reduce-motion). */
    val breatheMs: Int = 4_000,
    /** How long a SystemToast stays up (reading time, so not shortened by reduce-motion). */
    val toastHoldMs: Long = 3_000,
    val quickEasing: Easing = EaseOut,
    val standardEasing: Easing = EaseOut,
    val revealEasing: Easing = PlateSeat,
    val exitEasing: Easing = EaseIn,
) {
    /** Duration to use for an animation of [ms], or 0 when motion is reduced. */
    fun duration(ms: Int): Int = if (reduceMotion) 0 else ms
}

/** iOS-like ease-out: quick start, long gentle settle. */
private val EaseOut: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EaseIn: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
private val PlateSeat: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

val LocalAwakenMotion =staticCompositionLocalOf { AwakenMotion() }

/** System "Remove animations" (animator duration scale 0). Combined with the in-app toggle by [AwakenTheme]. */
@Composable
fun systemReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
