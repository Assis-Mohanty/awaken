package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/**
 * The building screen's ring: filled to [progress] (0..1) with [percentText] inside and the [line] of what is being set
 * underneath. The ring is one TalkBack node ([description]); the line is a live region so each new line is read out.
 */
@Composable
fun PlanRing(progress: Float, percentText: String, line: String, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        ProgressRing(
            progress = progress,
            description = description,
            color = colors.accent,
            size = AwakenTheme.dimens.onbPlanRing,
            stroke = AwakenTheme.dimens.heroRingStroke,
        ) {
            Text(percentText, style = AwakenTheme.type.metricL, color = colors.textPrimary)
        }
        LiveLine(line)
    }
}

/** A line of text that TalkBack announces whenever it changes. */
@Composable
private fun LiveLine(text: String) {
    Text(
        text = text,
        style = AwakenTheme.type.title,
        color = AwakenTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * One setting of the plan as a row: a leading [icon], the [title], its [value] and an optional [note] under it, and a chevron
 * when it can be edited. Tapping opens [content] (the editor) under the row. Put the rows in one card.
 */
@Composable
fun PlanRow(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    note: String? = null,
    editLabel: String? = null,
    onEditToggle: (() -> Unit)? = null,
    expanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        ListRow(
            title = title,
            icon = icon,
            secondary = listOfNotNull(value, note).joinToString(" · "),
            onClick = onEditToggle,
            trailing = if (onEditToggle != null && editLabel != null) {
                {
                    Icon(
                        if (expanded) AwakenGlyphs.ChevronDown else AwakenGlyphs.ChevronRight,
                        contentDescription = editLabel,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(AwakenTheme.dimens.iconSmall),
                    )
                }
            } else {
                null
            },
        )
        if (expanded) {
            Column(modifier = Modifier.padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
        }
    }
}

/** A minus, the current value and a plus: for goals that move in fixed steps. */
@Composable
fun StepperRow(
    valueText: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    canLess: Boolean,
    canMore: Boolean,
    lessDescription: String,
    moreDescription: String,
    modifier: Modifier = Modifier,
) {
    val haptics = AwakenTheme.haptics
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SecondaryButton(
            text = "−",
            onClick = { haptics.tick(); onLess() },
            enabled = canLess,
            modifier = Modifier.size(AwakenTheme.dimens.touchTarget).clearAndSetSemantics { contentDescription = lessDescription },
        )
        Text(valueText, style = AwakenTheme.type.title, color = AwakenTheme.colors.textPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        SecondaryButton(
            text = "+",
            onClick = { haptics.tick(); onMore() },
            enabled = canMore,
            modifier = Modifier.size(AwakenTheme.dimens.touchTarget).clearAndSetSemantics { contentDescription = moreDescription },
        )
    }
}

@ComponentPreview
@Composable
private fun PlanRingPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(Modifier.padding(Spacing.gutter)) {
            PlanRing(progress = 0.62f, percentText = "62%", line = "Setting your step goal: 8,000", description = "Building your plan, 62 percent")
        }
    }
}

@ComponentPreview
@Composable
private fun PlanSummaryCardPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            PlanRow(title = "Step goal", value = "8,000 steps", icon = AwakenGlyphs.Strength, editLabel = "Edit", onEditToggle = {}, expanded = true) {
                StepperRow("8,000", onLess = {}, onMore = {}, canLess = true, canMore = true, lessDescription = "Less", moreDescription = "More")
            }
            PlanRow(title = "App limits", value = "Off", icon = AwakenGlyphs.Phone, note = "Needs usage access")
            }
        }
    }
}
