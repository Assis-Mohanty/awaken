package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

/**
 * A slow ring that grows and shrinks, one breath in and one out every two [breatheMs][com.assismohanty.awaken.core.designsystem.theme.AwakenMotion.breatheMs].
 * Under reduce-motion it is a still ring, so the number in the middle ([content]) carries the whole screen. One TalkBack
 * node described by [description].
 */
@Composable
fun BreathingRing(description: String, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val motion = AwakenTheme.motion
    // Under reduce-motion no animation exists at all, not even an idle one that burns frames for nothing.
    val scale = if (motion.reduceMotion) STILL else breathingScale(motion.breatheMs)
    Box(
        modifier = modifier.size(dimens.breathingRing).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = dimens.breathingRingStroke.toPx()
            val diameter = (size.minDimension - width) * scale
            drawCircle(colors.accentMuted, radius = size.minDimension / 2f - width / 2f, style = Stroke(width / 2f))
            drawCircle(colors.accent, radius = diameter / 2f, style = Stroke(width))
        }
        content()
    }
}

@Composable
private fun breathingScale(breatheMs: Int): Float {
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = SMALLEST,
        targetValue = LARGEST,
        animationSpec = infiniteRepeatable(tween(breatheMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathing scale",
    )
    return scale
}

private const val SMALLEST = 0.7f
private const val LARGEST = 1f
private const val STILL = 0.85f

@ComponentPreview
@Composable
private fun BreathingRingPreview() {
    AwakenTheme(reduceMotion = true) {
        BreathingRing(description = "Breathing guide") {
            Text("3", style = AwakenTheme.type.metricXL, color = AwakenTheme.colors.textPrimary)
        }
    }
}
