package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The comeback moment (GD §7.3): the page dims and a plain card says what happened and what to do, with a reject haptic.
 * A small coral icon marks it; no red fill, no glow. Stern, never insulting. Reduce-motion: shown without the fade.
 */
@Composable
fun SystemWarning(body: String, detail: String, actionLabel: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.palette
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
            title = lexString(R.string.system_title_system_warning),
            accent = colors.ember,
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter)
                .alpha(fade.value),
        ) {
            IconCircle(AwakenGlyphs.Flame, colors.ember)
            Text(body, style = AwakenTheme.type.title, color = colors.bone)
            Text(detail, style = AwakenTheme.type.body, color = colors.mist)
            PrimaryButton(
                text = actionLabel,
                onClick = onDismiss,
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
            body = "You missed yesterday. One quest gets you back on track.",
            detail = "Your streak reset. Finish the comeback quest today to get back on track.",
            actionLabel = "Show me",
            onDismiss = {},
        )
    }
}
