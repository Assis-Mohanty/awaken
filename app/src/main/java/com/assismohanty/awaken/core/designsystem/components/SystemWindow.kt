package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The signature slab (DESIGN_SYSTEM §2.3): translucent `panel` fill, two opposite chamfers, a 1dp edge in [edgeColor] at
 * 70 % and a soft outer glow built from layered strokes (no blur, so it works on every API level). On first composition
 * the window scales 0.96 → 1 while its edge draws around the perimeter (`reveal`); with reduce-motion it just appears.
 */
@Composable
fun SystemWindow(
    title: String?,
    edgeColor: Color,
    modifier: Modifier = Modifier,
    reveal: Boolean = true,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = AwakenTheme.motion
    val shape = AwakenTheme.shapes.window
    val animate = reveal && !motion.reduceMotion
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) progress.animateTo(1f, tween(motion.revealMs, easing = motion.revealEasing))
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                val scale = REVEAL_START_SCALE + (1f - REVEAL_START_SCALE) * progress.value
                scaleX = scale
                scaleY = scale
                alpha = if (animate) progress.value.coerceAtLeast(MIN_REVEAL_ALPHA) else 1f
            }
            .windowEdge(shape, edgeColor, AwakenTheme.dimens.edgeWidth, AwakenTheme.dimens.glowWidth) { progress.value }
            .clip(shape)
            .background(AwakenTheme.colors.panel)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (title != null || headerTrailing != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(
                        text = title,
                        style = AwakenTheme.type.windowTitle,
                        color = edgeColor,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                }
                headerTrailing?.invoke(this)
            }
        }
        content()
    }
}

/**
 * Lit edge + glow around [shape]. [drawnFraction] (0..1) draws the edge progressively from the top-left chamfer, which is
 * the `reveal` animation; read inside the draw phase so animating it never recomposes.
 */
fun Modifier.windowEdge(
    shape: Shape,
    color: Color,
    edgeWidth: Dp,
    glowWidth: Dp,
    drawnFraction: () -> Float = { 1f },
): Modifier = drawWithCache {
    val outlinePath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val measure = PathMeasure().apply { setPath(outlinePath, forceClosed = true) }
    val edgePx = edgeWidth.toPx()
    val glowPx = glowWidth.toPx()
    val segment = Path()
    onDrawBehind {
        val fraction = drawnFraction().coerceIn(0f, 1f)
        val path = if (fraction >= 1f) {
            outlinePath
        } else {
            segment.reset()
            measure.getSegment(0f, measure.length * fraction, segment, startWithMoveTo = true)
            segment
        }
        // Glow: a few wide, faint strokes centred on the edge approximate an 8dp outer glow.
        for (step in GLOW_STEPS downTo 1) {
            drawPath(
                path = path,
                color = color.copy(alpha = AwakenAlpha.GLOW / GLOW_STEPS * fraction),
                style = Stroke(width = glowPx * 2 * step / GLOW_STEPS),
            )
        }
        drawPath(path = path, color = color.copy(alpha = AwakenAlpha.EDGE), style = Stroke(width = edgePx))
    }
}

private const val REVEAL_START_SCALE = 0.96f
private const val MIN_REVEAL_ALPHA = 0.001f
private const val GLOW_STEPS = 4

@ComponentPreview
@Composable
private fun SystemWindowPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SystemWindow(title = "DAILY DIRECTIVE", edgeColor = AwakenTheme.colors.rankC, reveal = false) {
                Text("Quests appear here.", style = AwakenTheme.type.body, color = AwakenTheme.colors.bone)
            }
            SystemWindow(title = "SYSTEM WARNING", edgeColor = AwakenTheme.colors.ember, reveal = false) {
                Text("You missed yesterday.", style = AwakenTheme.type.body, color = AwakenTheme.colors.bone)
            }
        }
    }
}
