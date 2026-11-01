package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme

/** The count shown inside the bubble stops at 999 so it always fits the circle. */
private const val MAX_SHOWN = 999

/**
 * The Reel Counter bubble that floats over short-video screens: today's count in a small circle. With a daily limit,
 * a ring fills toward it and turns critical once the limit is reached. [progress] is null when the app has no limit.
 */
@Composable
fun ReelBubble(count: Int, progress: Float?, over: Boolean, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val size = AwakenTheme.dimens.avatar
    Box(
        modifier = modifier
            .size(size)
            .clip(AwakenTheme.shapes.pill)
            .background(colors.surfaceOverlay)
            .border(AwakenTheme.dimens.hairline, colors.divider, AwakenTheme.shapes.pill)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val label = if (count > MAX_SHOWN) "$MAX_SHOWN+" else count.toString()
        val numeral: @Composable () -> Unit = {
            BasicText(
                text = label,
                style = AwakenTheme.type.numericSmall.copy(color = colors.textPrimary),
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        if (progress == null) {
            numeral()
        } else {
            ProgressRing(
                progress = progress.coerceIn(0f, 1f),
                description = "",
                color = if (over) colors.critical else colors.accent,
                size = size,
                stroke = AwakenTheme.dimens.accentWidth * 2,
            ) { numeral() }
        }
    }
}

@ComponentPreview
@Composable
private fun ReelBubblePreview() {
    AwakenTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(AwakenTheme.dimens.icon)) {
            ReelBubble(count = 7, progress = null, over = false, description = "7 reels")
            ReelBubble(count = 23, progress = 0.46f, over = false, description = "23 of 50")
            ReelBubble(count = 57, progress = 1f, over = true, description = "57 of 55")
            ReelBubble(count = 1_204, progress = null, over = false, description = "1204 reels")
        }
    }
}
