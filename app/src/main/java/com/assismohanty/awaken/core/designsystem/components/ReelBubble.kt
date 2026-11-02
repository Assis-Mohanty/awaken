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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.reelcounter.core.lumi.LumiState
import com.assismohanty.awaken.reelcounter.core.lumi.lumiStage

/** The count shown inside the bubble stops at 999 so it always fits the pill. */
private const val MAX_SHOWN = 999

/** The bubble's fixed footprint, which the overlay window needs for dragging and snapping to an edge. */
object ReelBubbleSize {
    val Width = 100.dp
    val Height = 48.dp
}

/**
 * The Reel Counter bubble that floats over short-video screens: Lumi at its current stage with today's reel count
 * beside it. The outline turns critical once a daily limit is reached ([over]). Lumi speaks the description
 * ("Lumi, Getting tired, 42 reels today"), so the number is hidden from accessibility.
 */
@Composable
fun ReelBubble(count: Int, lumi: LumiState, over: Boolean, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val outline = if (over) colors.critical else colors.divider
    val outlineWidth = if (over) AwakenTheme.dimens.accentWidth else AwakenTheme.dimens.hairline
    Row(
        modifier = modifier
            .size(ReelBubbleSize.Width, ReelBubbleSize.Height)
            .clip(AwakenTheme.shapes.pill)
            .background(colors.surfaceOverlay)
            .border(outlineWidth, outline, AwakenTheme.shapes.pill)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LumiView(state = lumi, size = LumiSizes.Bubble, reelsToday = count)
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

@ComponentPreview
@Composable
private fun ReelBubblePreview() {
    AwakenTheme(reduceMotion = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ReelBubble(count = 7, lumi = lumiStage(7, null), over = false)
            ReelBubble(count = 23, lumi = lumiStage(23, 50), over = false)
            ReelBubble(count = 57, lumi = lumiStage(57, 55), over = true)
        }
    }
}

@ComponentPreview
@Composable
private fun ReelBubbleLadderPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(listOf(0, 12, 30), listOf(55, 90, 1_204)).forEach { counts ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    counts.forEach { ReelBubble(count = it, lumi = lumiStage(it, null), over = false) }
                }
            }
        }
    }
}
