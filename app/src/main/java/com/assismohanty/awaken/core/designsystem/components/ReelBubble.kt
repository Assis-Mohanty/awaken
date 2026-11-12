package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
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
    val Width = 88.dp
    val Height = 40.dp
}

/**
 * The candle on the bubble melts as a daily limit gets close: eight pictures, from fresh (0) to burnt out (7).
 * With no limit set it stays fresh; at or past the limit it is burnt out; in between it steps evenly.
 */
object ReelMascot {
    const val PHASES = 8

    fun phase(count: Int, limit: Int?): Int {
        if (limit == null || limit <= 0) return 0
        if (count >= limit) return PHASES - 1
        return (count.coerceAtLeast(0) * (PHASES - 1) / limit).coerceIn(0, PHASES - 2)
    }

    @DrawableRes
    internal fun drawable(phase: Int): Int = PICTURES[phase.coerceIn(0, PHASES - 1)]

    private val PICTURES = intArrayOf(
        R.drawable.reel_mascot_1, R.drawable.reel_mascot_2, R.drawable.reel_mascot_3, R.drawable.reel_mascot_4,
        R.drawable.reel_mascot_5, R.drawable.reel_mascot_6, R.drawable.reel_mascot_7, R.drawable.reel_mascot_8,
    )
}

/** The glass is close to solid so the count stays readable over any video: a strong tint, a faint sheen, a firm rim. */
private object Glass {
    const val TINT_DARK = 0.86f
    const val TINT_LIGHT = 0.92f
    const val SHEEN_TOP_DARK = 0.10f
    const val SHEEN_TOP_LIGHT = 0.35f
    const val SHEEN_BOTTOM = 0.02f
}

private val MASCOT_WIDTH = 20.dp
private val MASCOT_HEIGHT = 34.dp

/**
 * The Reel Counter bubble that floats over short-video screens, top middle by default: a nearly solid pill with the
 * candle mascot on the left (melting as [phase] rises, see [ReelMascot]) and today's reel count, bold, on the right.
 * The rim turns critical once a daily limit is reached ([over]).
 */
@Composable
fun ReelBubble(count: Int, over: Boolean, modifier: Modifier = Modifier, phase: Int = 0) {
    val colors = AwakenTheme.colors
    val shape = AwakenTheme.shapes.pill
    val light = colors.surfaceOverlay.luminance() > 0.5f

    val tint = colors.surfaceOverlay.copy(alpha = if (light) Glass.TINT_LIGHT else Glass.TINT_DARK)
    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (light) Glass.SHEEN_TOP_LIGHT else Glass.SHEEN_TOP_DARK), Color.White.copy(alpha = Glass.SHEEN_BOTTOM)),
    )
    val rim: Brush = when {
        over -> SolidColor(colors.critical)
        light -> SolidColor(Color.Black.copy(alpha = 0.12f))
        else -> SolidColor(Color.White.copy(alpha = 0.22f))
    }
    Row(
        modifier = modifier
            .size(ReelBubbleSize.Width, ReelBubbleSize.Height)
            .clip(shape)
            .background(tint)
            .background(sheen)
            .border(AwakenTheme.dimens.accentWidth, rim, shape)
            .padding(start = Spacing.xs, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(ReelMascot.drawable(phase)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(MASCOT_WIDTH, MASCOT_HEIGHT),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            BasicText(
                text = if (count > MAX_SHOWN) "$MAX_SHOWN+" else count.toString(),
                style = AwakenTheme.type.numeric.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
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
                ReelBubble(count = 57, over = true, phase = ReelMascot.PHASES - 1)
            }
        }
    }
}

@ComponentPreview
@Composable
private fun ReelBubbleLadderPreview() {
    // A limit of 70: the candle melts a step every 10 reels and burns out at the limit.
    AwakenTheme(reduceMotion = true) {
        GlassBackdrop {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // Three per row: four do not fit the preview width, and a squeezed pill would hide its count.
                listOf(listOf(0, 10, 20), listOf(30, 40, 50), listOf(60, 70, 1_204)).forEach { counts ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        counts.forEach { ReelBubble(count = it, over = it >= 70, phase = ReelMascot.phase(it, 70)) }
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
