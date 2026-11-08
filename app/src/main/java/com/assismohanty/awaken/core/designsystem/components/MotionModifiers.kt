package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import kotlinx.coroutines.delay

/**
 * Press feedback (DESIGN_SYSTEM §2.5 `quick`): the control settles to 98 % while held. Static under reduce-motion.
 * Pass the same [interactionSource] the clickable uses.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val motion = AwakenTheme.motion
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduceMotion) PRESSED_SCALE else 1f,
        animationSpec = tween(motion.pressMs, easing = motion.quickEasing),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Staggered entrance (DESIGN_SYSTEM §2.5 `stagger`): on first composition the item rises 12dp and fades in, starting
 * [index] × 40 ms after the screen appears. Reduce-motion shows it in place at once.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int): Modifier {
    val motion = AwakenTheme.motion
    val rise = with(LocalDensity.current) { AwakenTheme.dimens.staggerRise.toPx() }
    val progress = remember { Animatable(if (motion.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(motion.staggerStepMs.toLong() * index.coerceAtMost(MAX_STAGGERED))
            progress.animateTo(1f, tween(motion.standardMs, easing = motion.standardEasing))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

private const val PRESSED_SCALE = 0.98f

/** Items past this index enter together, so long lists never feel slow. */
private const val MAX_STAGGERED = 6
