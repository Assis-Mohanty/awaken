package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** The count shown inside the bubble stops at 999 so it always fits the pill. */
private const val MAX_SHOWN = 999

/** The bubble's fixed footprint, which the overlay window needs for dragging and snapping. */
object ReelBubbleSize {
    val Width = 100.dp
    val Height = 48.dp
}

/** How the frosted glass is built: a see-through tint, a top-lit sheen and a light-catching rim, per theme. */
private object Glass {
    const val TINT_DARK = 0.46f
    const val TINT_LIGHT = 0.58f
    const val SHEEN_TOP_DARK = 0.20f
    const val SHEEN_TOP_LIGHT = 0.60f
    const val SHEEN_BOTTOM = 0.03f
}

/**
 * The Reel Counter bubble that floats over short-video screens, top middle by default: a frosted-glass pill with today's
 * reel count. The rim turns critical once a daily limit is reached ([over]).
 */
@Composable
fun ReelBubble(count: Int, over: Boolean, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val shape = AwakenTheme.shapes.pill
    val light = colors.surfaceOverlay.luminance() > 0.5f

    val tint = colors.surfaceOverlay.copy(alpha = if (light) Glass.TINT_LIGHT else Glass.TINT_DARK)
    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (light) Glass.SHEEN_TOP_LIGHT else Glass.SHEEN_TOP_DARK), Color.White.copy(alpha = Glass.SHEEN_BOTTOM)),
    )
    val rim: Brush = when {
        over -> SolidColor(colors.critical)
        light -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.07f), Color.White.copy(alpha = 0.6f)))
        else -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.26f)))
    }
    val rimWidth = if (over) AwakenTheme.dimens.accentWidth else AwakenTheme.dimens.hairline
    Row(
        modifier = modifier
            .size(ReelBubbleSize.Width, ReelBubbleSize.Height)
            .clip(shape)
            .background(tint)
            .background(sheen)
            .border(rimWidth, rim, shape)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicText(
                text = if (count > MAX_SHOWN) "$MAX_SHOWN+" else count.toString(),
                style = AwakenTheme.type.numericSmall.copy(color = colors.textPrimary),
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** A busy, colourful stand-in for a video, so the previews show what the glass does over content. */
@Composable
private fun GlassBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(Brush.linearGradient(listOf(AwakenTheme.colors.chartSeries3, AwakenTheme.colors.chartSeries5, AwakenTheme.colors.chartSeries1)))
            .padding(Spacing.md),
    ) { content() }
}

@ComponentPreview
@Composable
private fun ReelBubblePreview() {
    AwakenTheme(reduceMotion = true) {
        GlassBackdrop {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ReelBubble(count = 7, over = false)
                ReelBubble(count = 23, over = false)
                ReelBubble(count = 57, over = true)
            }
        }
    }
}

@ComponentPreview
@Composable
private fun ReelBubbleLadderPreview() {
    AwakenTheme(reduceMotion = true) {
        GlassBackdrop {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(listOf(0, 12, 30), listOf(55, 90, 1_204)).forEach { counts ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        counts.forEach { ReelBubble(count = it, over = false) }
                    }
                }
            }
        }
    }
}

@ComponentPreview
@Composable
private fun ReelBubblePlainBackdropPreview() {
    AwakenTheme(reduceMotion = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ReelBubble(count = 3, over = false)
            ReelBubble(count = 61, over = false)
        }
    }
}
