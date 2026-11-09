package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** Where an exercise stands in the workout. */
enum class SegmentState { DONE, CURRENT, TODO }

/** One thin segment per exercise: done in the accent, the current one in ink, the rest faint. [description] is read in place of the picture. */
@Composable
fun SegmentBar(segments: List<SegmentState>, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Spacing.xxs)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs / 2),
    ) {
        segments.forEach { state ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(AwakenTheme.shapes.pill)
                    .background(
                        when (state) {
                            SegmentState.DONE -> colors.accent
                            SegmentState.CURRENT -> colors.textPrimary
                            SegmentState.TODO -> colors.surfaceOverlay
                        },
                    ),
            )
        }
    }
}

/**
 * A number to log, large and easy to hit (coached workout): the [value] tapped opens the number pad, − and + step it. Every
 * control is at least 48dp and speaks its own description; [unit] sits above in a quiet caption.
 */
@Composable
fun BigNumber(
    value: String,
    unit: String,
    description: String,
    onClick: () -> Unit,
    onLess: () -> Unit,
    onMore: () -> Unit,
    lessDescription: String,
    moreDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(unit, style = AwakenTheme.type.caption, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepIcon(AwakenGlyphs.Minus, lessDescription, onLess)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget + Spacing.md)
                    .clip(AwakenTheme.shapes.inner)
                    .background(colors.surfaceOverlay)
                    .clickable(onClick = onClick)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                    }
                    .padding(horizontal = Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(value, style = AwakenTheme.type.metricL, color = colors.textPrimary, textAlign = TextAlign.Center)
            }
            StepIcon(AwakenGlyphs.Plus, moreDescription, onMore)
        }
    }
}

@Composable
private fun StepIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(AwakenTheme.dimens.touchTarget)) {
        Icon(icon, contentDescription = description, tint = AwakenTheme.colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.icon))
    }
}

@ComponentPreview
@Composable
private fun WorkoutComponentsPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SegmentBar(listOf(SegmentState.DONE, SegmentState.DONE, SegmentState.CURRENT, SegmentState.TODO, SegmentState.TODO), "Exercise 3 of 5")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                BigNumber("62.5", "kg", "Weight, 62.5 kilograms", {}, {}, {}, "Less weight", "More weight", Modifier.weight(1f))
                BigNumber("10", "reps", "Reps, 10", {}, {}, {}, "Fewer reps", "More reps", Modifier.weight(1f))
            }
        }
    }
}
