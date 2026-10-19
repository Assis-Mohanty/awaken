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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The signature slab (DESIGN_SYSTEM §2.3, premium black): `panel` fill, two opposite chamfers, a 1dp neutral `edge`
 * hairline, and a single corner bracket in [accent] (rank colour, or `ember` for warnings) on the top-start chamfer. The
 * title is set in [accent] too. On first composition the window scales 0.96 → 1 while its hairline draws around the
 * perimeter (`reveal`); with reduce-motion it just appears.
 */
@Composable
fun SystemWindow(
    title: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    reveal: Boolean = true,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = AwakenTheme.motion
    val shapes = AwakenTheme.shapes
    val dimens = AwakenTheme.dimens
    val colors = AwakenTheme.colors
    // A Vault theme replaces the rank accent; warnings stay ember (GD §9.2: themes never override the crest or alerts).
    val windowAccent = if (accent == colors.ember) accent else AwakenTheme.themeTint ?: accent
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
            .clip(shapes.window)
            .background(AwakenTheme.colors.panel)
            .windowEdge(
                shape = shapes.window,
                edgeColor = AwakenTheme.colors.edge,
                edgeWidth = dimens.edgeWidth,
                accent = windowAccent,
                accentWidth = dimens.accentWidth,
                chamfer = shapes.windowChamfer,
                bracketLength = dimens.bracketLength,
            ) { progress.value }
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (title != null || headerTrailing != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(
                        text = title,
                        style = AwakenTheme.type.windowTitle,
                        color = windowAccent,
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
 * Neutral hairline around [shape] plus the accent corner bracket on the top-start chamfer. [drawnFraction] (0..1) draws
 * the hairline progressively (the `reveal`); it is read in the draw phase, so animating it never recomposes.
 */
fun Modifier.windowEdge(
    shape: Shape,
    edgeColor: Color,
    edgeWidth: Dp,
    accent: Color,
    accentWidth: Dp,
    chamfer: Dp,
    bracketLength: Dp,
    drawnFraction: () -> Float = { 1f },
): Modifier = drawWithCache {
    val outlinePath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val measure = PathMeasure().apply { setPath(outlinePath, forceClosed = true) }
    val edgePx = edgeWidth.toPx()
    val accentPx = accentWidth.toPx()
    // Inset by half the stroke so the bracket sits on the hairline instead of being clipped.
    val inset = accentPx / 2f
    val c = chamfer.toPx()
    val arm = bracketLength.toPx()
    val bracket = Path().apply {
        moveTo(inset, c + arm)
        lineTo(inset, c)
        lineTo(c, inset)
        lineTo(c + arm, inset)
    }
    val segment = Path()
    onDrawWithContent {
        drawContent()
        val fraction = drawnFraction().coerceIn(0f, 1f)
        val edge = if (fraction >= 1f) {
            outlinePath
        } else {
            segment.reset()
            measure.getSegment(0f, measure.length * fraction, segment, startWithMoveTo = true)
            segment
        }
        drawPath(edge, edgeColor, style = Stroke(width = edgePx * 2f)) // half is clipped by the window shape
        drawPath(
            bracket,
            accent.copy(alpha = fraction),
            style = Stroke(width = accentPx, cap = StrokeCap.Square, join = StrokeJoin.Miter),
        )
    }
}

private const val REVEAL_START_SCALE = 0.96f
private const val MIN_REVEAL_ALPHA = 0.001f

@ComponentPreview
@Composable
private fun SystemWindowPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SystemWindow(title = "DAILY DIRECTIVE", accent = AwakenTheme.colors.rankC, reveal = false) {
                Text("Quests appear here.", style = AwakenTheme.type.body, color = AwakenTheme.colors.bone)
            }
            SystemWindow(title = "SYSTEM WARNING", accent = AwakenTheme.colors.ember, reveal = false) {
                Text("You missed yesterday.", style = AwakenTheme.type.body, color = AwakenTheme.colors.bone)
            }
        }
    }
}
