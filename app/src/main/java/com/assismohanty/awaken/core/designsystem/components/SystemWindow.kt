package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The card (DESIGN_SYSTEM §3): a soft rounded surface with a warm hairline, generous padding and an optional title row.
 * [tint] washes the card in a soft colour (a stat's pastel); [accent] colours the title only in System mode, where the
 * card also gets an accent edge and glow. On first composition it fades in and rises a little; reduce-motion shows it.
 */
@Composable
fun AwakenCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = AwakenTheme.palette.system,
    tint: Color? = null,
    reveal: Boolean = true,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = AwakenTheme.palette
    val motion = AwakenTheme.motion
    val shape = AwakenTheme.shapes.window
    val dimens = AwakenTheme.dimens
    val animate = reveal && !motion.reduceMotion
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) progress.animateTo(1f, tween(motion.revealMs, easing = motion.standardEasing))
    }
    val rise = with(LocalDensity.current) { dimens.staggerRise.toPx() }
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = if (animate) progress.value.coerceAtLeast(MIN_REVEAL_ALPHA) else 1f
                translationY = (1f - progress.value) * rise
            }
            .then(
                if (palette.glow) {
                    Modifier.shadow(dimens.glow, shape, clip = false, ambientColor = accent, spotColor = accent)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(tint ?: palette.panel)
            .border(
                width = dimens.hairline,
                color = if (palette.glow) accent.copy(alpha = AwakenAlpha.GLOW) else if (tint != null) Color.Transparent else palette.edge,
                shape = shape,
            )
            .padding(Spacing.card),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (title != null || headerTrailing != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(
                        text = title,
                        style = AwakenTheme.type.windowTitle,
                        color = if (palette.glow) accent else palette.bone,
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
 * The v1.0 name for [AwakenCard], kept so screens not yet redesigned compile unchanged. [accent] is the card's meaning
 * colour (rank, or `critical` for warnings).
 */
@Composable
fun SystemWindow(
    title: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    reveal: Boolean = true,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = AwakenCard(modifier = modifier, title = title, accent = accent, reveal = reveal, headerTrailing = headerTrailing, content = content)

private const val MIN_REVEAL_ALPHA = 0.001f

@ComponentPreview
@Composable
private fun AwakenCardPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AwakenCard(title = "Today's plan", reveal = false) {
                Text("Quests appear here.", style = AwakenTheme.type.body, color = AwakenTheme.palette.mist)
            }
            AwakenCard(title = "Strength", tint = AwakenTheme.palette.tintStr, reveal = false) {
                Text("+2 levels this month", style = AwakenTheme.type.body, color = AwakenTheme.palette.statStr)
            }
        }
    }
}
