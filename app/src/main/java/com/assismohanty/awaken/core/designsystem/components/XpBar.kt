package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * XP bar (DESIGN_SYSTEM §3): a slim pill, `edge` track, accent fill fading to `aether` at its leading edge.
 * Animates changes with the `standard` motion; shows "1,240 / 1,434 XP" underneath.
 */
@Composable
fun XpBar(
    level: Int,
    xpIntoLevel: Long,
    xpForLevel: Long,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val colors = AwakenTheme.palette
    val motion = AwakenTheme.motion
    val dimens = AwakenTheme.dimens
    val target = if (xpForLevel <= 0) 1f else (xpIntoLevel.toFloat() / xpForLevel).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, tween(motion.duration(motion.standardMs), easing = motion.standardEasing), label = "xp")
    val into = formatCount(xpIntoLevel)
    val total = formatCount(xpForLevel)
    val description = lexString(R.string.cd_xp_bar, level, into, total)

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.xpBarHeight)
                .clip(AwakenTheme.shapes.xpBar)
                .drawBehind {
                    drawRect(colors.edge)
                    val fillWidth = size.width * fraction
                    if (fillWidth > 0f) {
                        // A soft accent gradient across the filled part, rounded at both ends.
                        drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(colors.system, colors.aether), startX = 0f, endX = fillWidth),
                            size = Size(fillWidth.coerceAtLeast(size.height), size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                },
        )
        if (showLabel) {
            Text(
                text = lexString(R.string.xp_progress, into, total),
                style = AwakenTheme.type.numericSmall,
                color = colors.mist,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@ComponentPreview
@Composable
private fun XpBarPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            XpBar(level = 24, xpIntoLevel = 1_240, xpForLevel = 1_434)
            XpBar(level = 1, xpIntoLevel = 0, xpForLevel = 112)
            XpBar(level = 100, xpIntoLevel = 0, xpForLevel = 0)
        }
    }
}
