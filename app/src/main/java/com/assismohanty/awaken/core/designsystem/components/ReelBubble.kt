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
import com.assismohanty.awaken.reelcounter.core.lumi.LumiRules
import com.assismohanty.awaken.reelcounter.core.lumi.LumiState
import com.assismohanty.awaken.reelcounter.core.lumi.lumiStage

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
    const val GLOW_MAX = 0.34f
}

/**
 * The Reel Counter bubble that floats over short-video screens, top middle by default: a frosted-glass pill with Lumi
 * at its current stage and today's reel count beside it. A soft glow behind Lumi fades as it tires. The rim turns
 * critical once a daily limit is reached ([over]). Lumi speaks the description ("Lumi, Getting tired, 42 reels today"),
 * so the number is hidden from accessibility.
 */
@Composable
fun ReelBubble(count: Int, lumi: LumiState, over: Boolean, modifier: Modifier = Modifier) {
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
    // Fully charged glows warm; each stage lowers it until a switched-off Lumi has none.
    val glow = colors.reward.copy(
        alpha = Glass.GLOW_MAX * (LumiRules.LAST_STAGE - lumi.stage) / (LumiRules.LAST_STAGE - LumiRules.FIRST_STAGE),
    )

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
        Box(
            modifier = Modifier
                .size(LumiSizes.Bubble)
                .drawBehind { drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), radius = size.minDimension * 0.85f)) },
        ) {
            LumiView(state = lumi, size = LumiSizes.Bubble, reelsToday = count)
        }
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
            .background(Brush.linearGradient(listOf(Color(0xFF3B82C4), Color(0xFFE0689A), Color(0xFFF4B942))))
            .padding(Spacing.md),
    ) { content() }
}

@ComponentPreview
@Composable
private fun ReelBubblePreview() {
    AwakenTheme(reduceMotion = true) {
        GlassBackdrop {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ReelBubble(count = 7, lumi = lumiStage(7, null), over = false)
                ReelBubble(count = 23, lumi = lumiStage(23, 50), over = false)
                ReelBubble(count = 57, lumi = lumiStage(57, 55), over = true)
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
                        counts.forEach { ReelBubble(count = it, lumi = lumiStage(it, null), over = false) }
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
            ReelBubble(count = 3, lumi = lumiStage(3, null), over = false)
            ReelBubble(count = 61, lumi = lumiStage(61, null), over = false)
        }
    }
}
