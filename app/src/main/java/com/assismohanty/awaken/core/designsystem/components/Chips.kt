package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

/**
 * Chip (DESIGN_SYSTEM §3): ink text on a 15% wash of [color], with an optional icon in [color]. The accent marks the
 * chip; the words stay ink so they read in both themes. [background] overrides the wash (a neutral chip). Colour is
 * never the only signal: every chip carries text.
 */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    background: Color? = null,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.chipHeight)
            .clip(AwakenTheme.shapes.pill)
            .background(background ?: color.copy(alpha = AwakenAlpha.TINT))
            .padding(horizontal = Spacing.xs + Spacing.xxs / 2, vertical = Spacing.xxs / 2)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSmall - Spacing.xxs / 2))
        Text(text = text, style = AwakenTheme.type.label, color = colors.textPrimary)
    }
}

/**
 * A selectable choice (onboarding answers, habit editor). [multiSelect] = checkbox semantics, else radio. Meets the
 * 48dp target. Selected is the primary ink with page-coloured text and a check, so it never relies on hue.
 * [color] tints only the optional [icon].
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AwakenTheme.colors.textPrimary,
    icon: ImageVector? = null,
    multiSelect: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val selection = if (multiSelect) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    val ink = when {
        !enabled -> colors.textMuted
        selected -> colors.onAccent
        else -> colors.textPrimary
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.touchTarget - Spacing.xxs)
            .clip(AwakenTheme.shapes.pill)
            .background(if (selected) colors.accent else colors.surfaceOverlay)
            .then(selection)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (selected) ink else color, modifier = Modifier.size(dimens.iconSmall))
        Text(text = text, style = AwakenTheme.type.label, color = ink)
        if (selected) Icon(AwakenGlyphs.Check, contentDescription = null, tint = ink, modifier = Modifier.size(dimens.iconSmall))
    }
}

/** Stat chip: the stat's glyph in its accent on a 15% wash, and its 3-letter code; TalkBack reads the full name. */
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
    Pill(text = difficulty.label(), color = AwakenTheme.colors.textPrimary, background = AwakenTheme.colors.surfaceOverlay, modifier = modifier)
}

/** Verified = Health Connect or phone usage; Timed = Focus Gate. Nothing for self-reported quests. */
@Composable
fun VerifiedBadge(source: VerificationSource, modifier: Modifier = Modifier) {
    val color = AwakenTheme.colors.positive
    when (source) {
        VerificationSource.SELF -> Unit
        VerificationSource.HEALTH ->
            Pill(lexString(R.string.badge_verified), color, modifier, icon = AwakenGlyphs.VerifiedSeal)
        VerificationSource.TIMER ->
            Pill(lexString(R.string.badge_timed), color, modifier, icon = AwakenGlyphs.Hourglass)
        VerificationSource.USAGE ->
            Pill(lexString(R.string.badge_verified), color, modifier, icon = AwakenGlyphs.VerifiedSeal)
    }
}

@ComponentPreview
@Composable
private fun ChipsPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { Stat.entries.take(3).forEach { StatChip(it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { Stat.entries.drop(3).forEach { StatChip(it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { Difficulty.entries.forEach { DifficultyChip(it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                VerifiedBadge(VerificationSource.HEALTH)
                VerifiedBadge(VerificationSource.TIMER)
                Pill(lexString(R.string.chip_main), AwakenTheme.colors.textPrimary, background = AwakenTheme.colors.surfaceOverlay)
            }
            ChoiceChip("Sometimes", selected = true, onClick = {})
            ChoiceChip("Often", selected = false, onClick = {})
        }
    }
}
