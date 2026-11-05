package com.assismohanty.awaken.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The onboarding flow is always light (docs/onboarding/PLAN.md Q5), whatever the phone or the Appearance setting says. It
 * keeps the reduce-motion and haptics choices already in effect, and turns the status-bar icons dark while it is on screen.
 */
@Composable
fun OnboardingTheme(content: @Composable () -> Unit) {
    val reduceMotion = AwakenTheme.motion.reduceMotion
    val haptics = AwakenTheme.haptics
    val view = LocalView.current
    val window = view.context.findActivity()?.window
    if (window != null && !view.isInEditMode) {
        DisposableEffect(window) {
            val controller = WindowCompat.getInsetsController(window, view)
            val before = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = true
            onDispose { controller.isAppearanceLightStatusBars = before }
        }
    }
    AwakenTheme(darkTheme = false, reduceMotion = reduceMotion) {
        CompositionLocalProvider(LocalHaptics provides haptics, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
