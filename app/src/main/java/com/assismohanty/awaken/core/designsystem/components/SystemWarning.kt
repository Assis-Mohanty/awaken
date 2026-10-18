package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The Penalty Zone moment (GD §7.3, DESIGN_SYSTEM §2.1 `ember`): the void dims, an ember-edged "SYSTEM WARNING" window
 * types its line on, with a reject haptic. Stern, never insulting. Reduce-motion: static window, no type-on.
 */
@Composable
fun SystemWarning(body: String, detail: String, actionLabel: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val haptics = AwakenTheme.haptics
    val fade = remember { Animatable(if (motion.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        haptics.reject()
        fade.animateTo(1f, tween(motion.duration(motion.revealMs), easing = motion.revealEasing))
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.void.copy(alpha = AwakenAlpha.DIM_SCRIM))
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        SystemWindow(
            title = stringResource(R.string.system_title_system_warning),
            accent = colors.ember,
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter)
                .alpha(fade.value),
        ) {
            TypeOnText(text = body, style = AwakenTheme.type.title, color = colors.bone, animate = !motion.reduceMotion)
            Text(detail, style = AwakenTheme.type.body, color = colors.mist)
            PrimaryButton(
                text = actionLabel,
                onClick = onDismiss,
                color = colors.ember,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        }
    }
}

/** [SystemWarning] in its own full-screen window, like [LevelUpDialog]: covers everything, Back acknowledges it. */
@Composable
fun SystemWarningDialog(body: String, detail: String, actionLabel: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        SystemWarning(body = body, detail = detail, actionLabel = actionLabel, onDismiss = onDismiss)
    }
}

@ComponentPreview
@Composable
private fun SystemWarningPreview() {
    AwakenTheme {
        SystemWarning(
            body = "You missed yesterday. One quest stands between you and redemption.",
            detail = "Your streak reset. Clear the penalty quest today to mark your comeback.",
            actionLabel = "Face it",
            onDismiss = {},
        )
    }
}
