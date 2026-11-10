package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Water that pours down the screen from the top as [progress] goes from 0 to 1, with two soft waves on its leading edge:
 * the whole timer of the pause screen, with no number. Draw it first in a full-size box and put the content on top. At 1
 * the water covers everything. The waves drift slowly; under reduce-motion they are still, and the caller moves
 * [progress] in whole steps. One TalkBack node.
 */
@Composable
fun WaterFill(progress: Float, description: String, modifier: Modifier = Modifier) {
    val reduceMotion = AwakenTheme.motion.reduceMotion
    val frontPhase = if (reduceMotion) remember { mutableFloatStateOf(STILL_FRONT) } else wavePhase(FRONT_MS, forward = true)
    val backPhase = if (reduceMotion) remember { mutableFloatStateOf(STILL_BACK) } else wavePhase(BACK_MS, forward = false)
    val water = AwakenTheme.palette.statInt
    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics { contentDescription = description }) {
        val amplitude = size.width * AMPLITUDE
        val p = progress.coerceIn(0f, 1f)
        // The wave's centre line: just above the top at 0 (nothing shows), just below the bottom edge at 1 (everything is covered).
        val front = p * (size.height + 2f * amplitude) - amplitude
        val back = front + amplitude * BACK_DROP * p
        if (p > 0f) {
            wave(back, amplitude, BACK_CYCLES, backPhase.value, water, BACK_TOP_ALPHA, BACK_BOTTOM_ALPHA)
            wave(front, amplitude, FRONT_CYCLES, frontPhase.value, water, FRONT_TOP_ALPHA, FRONT_BOTTOM_ALPHA)
        }
    }
}

/** Everything above a sine line, so the water hangs from the top and its wavy edge faces down. */
private fun DrawScope.wave(centre: Float, amplitude: Float, cycles: Float, phase: Float, colour: Color, topAlpha: Float, bottomAlpha: Float) {
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        var x = size.width
        while (x > 0f) {
            lineTo(x, centre + amplitude * sin(x / size.width * cycles * TWO_PI + phase))
            x -= STEP
        }
        lineTo(0f, centre + amplitude * sin(phase))
        close()
    }
    val brush = Brush.verticalGradient(
        colors = listOf(colour.copy(alpha = topAlpha), colour.copy(alpha = bottomAlpha)),
        startY = 0f,
        endY = size.height,
    )
    drawPath(path, brush)
}

@Composable
private fun wavePhase(durationMs: Int, forward: Boolean): State<Float> {
    val transition = rememberInfiniteTransition(label = "water")
    return transition.animateFloat(
        initialValue = if (forward) 0f else TWO_PI,
        targetValue = if (forward) TWO_PI else 0f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "wave phase",
    )
}

private const val TWO_PI = (2.0 * PI).toFloat()
private const val STEP = 12f
private const val AMPLITUDE = 0.03f
private const val BACK_DROP = 0.9f
private const val FRONT_CYCLES = 1.4f
private const val BACK_CYCLES = 1.0f
private const val FRONT_MS = 3_200
private const val BACK_MS = 4_800
private const val STILL_FRONT = 1.0f
private const val STILL_BACK = 3.0f
private const val FRONT_TOP_ALPHA = 0.5f
private const val FRONT_BOTTOM_ALPHA = 0.8f
private const val BACK_TOP_ALPHA = 0.28f
private const val BACK_BOTTOM_ALPHA = 0.5f

@ComponentPreview
@Composable
private fun WaterFillPreview() {
    AwakenTheme(reduceMotion = true) {
        Box(modifier = Modifier.fillMaxSize()) { WaterFill(progress = 0.55f, description = "Pause") }
    }
}
