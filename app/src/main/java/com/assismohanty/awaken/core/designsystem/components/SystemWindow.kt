package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * The card (DESIGN_SYSTEM §3): a 22dp surface in the card colour with no border. Light cards carry one soft shadow, dark
 * cards tone only. 16dp padding and an optional title row in 17sp. A [flat] card (inside a sheet or another card) never
 * has a shadow. When [onClick] is set the whole card is the
 * target and turns the pressed colour while held. On first composition it fades in and rises 8dp; reduce-motion shows it.
 *
 * [accent] and [tint] are kept for callers written before v2 and are ignored: colour never fills a card.
 */
@Composable
fun AwakenCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    @Suppress("UNUSED_PARAMETER") accent: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") tint: Color? = null,
    reveal: Boolean = true,
    flat: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.card),
    /** Gap between children; list cards pass [Spacing.zero] because rows carry their own padding. */
    spacing: Dp = Spacing.sm,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AwakenTheme.colors
    val motion = AwakenTheme.motion
    val shape = AwakenTheme.shapes.window
    val animate = reveal && !motion.reduceMotion
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) progress.animateTo(1f, tween(motion.revealMs, easing = motion.standardEasing))
    }
    val rise = with(LocalDensity.current) { AwakenTheme.dimens.staggerRise.toPx() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        if (pressed) colors.surfaceOverlay else colors.surfaceElevated,
        tween(motion.duration(motion.quickMs)),
        label = "cardPress",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = if (animate) progress.value.coerceAtLeast(MIN_REVEAL_ALPHA) else 1f
                translationY = (1f - progress.value) * rise
            }
            .then(if (flat || colors.isDark) Modifier else Modifier.shadow(CARD_ELEVATION, shape, ambientColor = colors.textPrimary.copy(alpha = AMBIENT), spotColor = colors.textPrimary.copy(alpha = SPOT)))
            .clip(shape)
            .background(fill)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (title != null || headerTrailing != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(
                        text = title,
                        style = AwakenTheme.type.windowTitle,
                        color = colors.textPrimary,
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

/** The v1.0 name for [AwakenCard], kept so screens not yet redesigned compile unchanged. [accent] is ignored. */
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
private val CARD_ELEVATION = 6.dp
private const val AMBIENT = 0.04f
private const val SPOT = 0.16f

@ComponentPreview
@Composable
private fun AwakenCardPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            AwakenCard(title = "Today's plan", reveal = false) {
                Text("Quests appear here.", style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
            }
            AwakenCard(title = "Strength", reveal = false, onClick = {}) {
                Text("+2 levels this month", style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
            }
        }
    }
}
