package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.model.Rank

/**
 * App background (DESIGN_SYSTEM §2.1, premium black): true-black `void`, a faint `depth` lift and a ~5 % haze tinted by
 * the hunter's current rank (or `ember` while the Penalty Zone is active). Tint changes crossfade.
 */
@Composable
fun GateBackground(
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val glow by animateColorAsState(tint, tween(motion.duration(motion.revealMs)), label = "gateGlow")
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(colors.void)
                val centre = Offset(size.width / 2f, size.height * GLOW_CENTRE_Y)
                val radius = maxOf(size.width, size.height) * DEPTH_RADIUS
                drawRect(Brush.radialGradient(listOf(colors.depth, colors.void), center = centre, radius = radius))
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = AwakenAlpha.HAZE), Color.Transparent),
                        center = centre,
                        radius = size.width * GLOW_RADIUS,
                    ),
                )
            },
        content = content,
    )
}

@Composable
fun GateBackground(rank: Rank, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) =
    GateBackground(tint = AwakenTheme.colors.rank(rank), modifier = modifier, content = content)

private const val GLOW_CENTRE_Y = 0.18f
private const val DEPTH_RADIUS = 0.85f
private const val GLOW_RADIUS = 0.75f

@PhonePreviews
@Composable
private fun GateBackgroundPreview() {
    AwakenTheme { GateBackground(rank = Rank.C) }
}
