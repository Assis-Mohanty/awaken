package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

/** Fully rounded pill (DESIGN_SYSTEM §2.3). Colour is never the only signal: every pill carries text. */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
) {
    val dimens = AwakenTheme.dimens
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.chipHeight)
            .border(dimens.hairline, color.copy(alpha = AwakenAlpha.EDGE), AwakenTheme.shapes.pill)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSmall))
        Text(text = text, style = AwakenTheme.type.label, color = color)
    }
}

/**
 * A selectable choice (onboarding answers, habit editor). [multiSelect] = checkbox semantics, else radio. Meets the
 * 48dp touch target; selection is shown by a filled tint and full-strength edge, never by colour alone (the check).
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AwakenTheme.colors.system,
    icon: ImageVector? = null,
    multiSelect: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val shape = AwakenTheme.shapes.button
    val selection = if (multiSelect) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.touchTarget)
            .background(if (selected) color.copy(alpha = AwakenAlpha.TINT) else Color.Transparent, shape)
            .border(dimens.hairline, if (selected) color else colors.edge, shape)
            .then(selection)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSmall))
        Text(text = text, style = AwakenTheme.type.label, color = if (enabled) colors.bone else colors.fog)
        if (selected) Icon(AwakenGlyphs.Check, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSmall))
    }
}

/** Stat chip: sigil + 3-letter code, with the full stat name for TalkBack. */
@Composable
fun StatChip(stat: Stat, modifier: Modifier = Modifier) {
    Pill(
        text = stat.name,
        color = AwakenTheme.colors.stat(stat),
        icon = AwakenGlyphs.stat(stat),
        description = stat.label(),
        modifier = modifier,
    )
}

@Composable
fun DifficultyChip(difficulty: Difficulty, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val color = when (difficulty) {
        Difficulty.EASY, Difficulty.NORMAL -> colors.mist
        Difficulty.HARD -> colors.bone
        Difficulty.EXTREME -> colors.crit
    }
    Pill(text = difficulty.label(), color = color, modifier = modifier)
}

/** Verified = Health Connect; Timed = Focus Gate. Nothing for self-reported quests. */
@Composable
fun VerifiedBadge(source: VerificationSource, modifier: Modifier = Modifier) {
    val color = AwakenTheme.colors.verified
    when (source) {
        VerificationSource.SELF -> Unit
        VerificationSource.HEALTH ->
            Pill(stringResource(R.string.badge_verified), color, modifier, icon = AwakenGlyphs.VerifiedSeal)
        VerificationSource.TIMER ->
            Pill(stringResource(R.string.badge_timed), color, modifier, icon = AwakenGlyphs.Hourglass)
    }
}

@ComponentPreview
@Composable
private fun ChipsPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { Stat.entries.forEach { StatChip(it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { Difficulty.entries.forEach { DifficultyChip(it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                VerifiedBadge(VerificationSource.HEALTH)
                VerifiedBadge(VerificationSource.TIMER)
                Pill(stringResource(R.string.chip_main), AwakenTheme.colors.system)
            }
        }
    }
}
