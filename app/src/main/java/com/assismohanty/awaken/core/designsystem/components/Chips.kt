package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

/**
 * Soft pill (DESIGN_SYSTEM §3): [color] text on a light wash of itself, or on [background] when given. Colour is never
 * the only signal: every pill carries text.
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
    val dimens = AwakenTheme.dimens
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.chipHeight)
            .clip(AwakenTheme.shapes.pill)
            .background(background ?: color.copy(alpha = PILL_WASH))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
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
 * 48dp touch target; selection shows as an accent wash, a stronger edge and a check, never by colour alone.
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AwakenTheme.palette.system,
    icon: ImageVector? = null,
    multiSelect: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    val shape = AwakenTheme.shapes.pill
    val selection = if (multiSelect) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.touchTarget)
            .clip(shape)
            .background(if (selected) color.copy(alpha = PILL_WASH) else palette.panel)
            .border(if (selected) dimens.accentWidth else dimens.hairline, if (selected) color else palette.edge, shape)
            .then(selection)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSmall))
        Text(text = text, style = AwakenTheme.type.label, color = if (enabled) palette.bone else palette.fog)
        if (selected) Icon(AwakenGlyphs.Check, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSmall))
    }
}

/** Stat chip: the stat's pastel with its sigil and 3-letter code; TalkBack reads the full name. */
@Composable
fun StatChip(stat: Stat, modifier: Modifier = Modifier) {
    Pill(
        text = stat.name,
        color = AwakenTheme.palette.stat(stat),
        background = AwakenTheme.palette.statTint(stat),
        icon = AwakenGlyphs.stat(stat),
        description = stat.label(),
        modifier = modifier,
    )
}

@Composable
fun DifficultyChip(difficulty: Difficulty, modifier: Modifier = Modifier) {
    val palette = AwakenTheme.palette
    val color = when (difficulty) {
        Difficulty.EASY, Difficulty.NORMAL -> palette.mist
        Difficulty.HARD -> palette.bone
        Difficulty.EXTREME -> palette.crit
    }
    Pill(text = difficulty.label(), color = color, background = palette.panelRaised, modifier = modifier)
}

/** Verified = Health Connect or phone usage; Timed = Focus Gate. Nothing for self-reported quests. */
@Composable
fun VerifiedBadge(source: VerificationSource, modifier: Modifier = Modifier) {
    val color = AwakenTheme.palette.verified
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

/** Light enough to read the pill's own colour on top (≥ 4.5:1 in every palette). */
private const val PILL_WASH = 0.12f

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
                Pill(lexString(R.string.chip_main), AwakenTheme.palette.system)
            }
            ChoiceChip("Sometimes", selected = true, onClick = {})
            ChoiceChip("Often", selected = false, onClick = {})
        }
    }
}
