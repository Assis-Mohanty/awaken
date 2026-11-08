package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.ConsistencyScore
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat

/**
 * The top of Progress: a large level ring, the rank badge, the XP as one big number over a thin bar, and the streak line with
 * the 28-day consistency under it. Ring beside the details; stacked at large text.
 */
@Composable
fun LevelHero(
    level: Int,
    rank: Rank,
    xpIntoLevel: Long,
    xpForLevel: Long,
    streak: Int,
    best: Int,
    modifier: Modifier = Modifier,
    consistency: ConsistencyScore? = null,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val dimens = AwakenTheme.dimens
    val description = lexString(R.string.cd_level_module, level, rank.name, formatCount(xpIntoLevel), formatCount(xpForLevel))
    val fraction = if (xpForLevel <= 0) 1f else (xpIntoLevel.toFloat() / xpForLevel).coerceIn(0f, 1f)
    val ring: @Composable () -> Unit = {
        ProgressRing(
            progress = fraction,
            description = level.toString(),
            color = colors.accent,
            size = dimens.levelRingHero,
            stroke = dimens.levelRingHeroStroke,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(level.toString(), style = type.metricXL, color = colors.textPrimary)
                Text(lexString(R.string.level_word), style = type.caption, color = colors.textSecondary)
            }
        }
    }
    val details: @Composable (Modifier) -> Unit = { detailsModifier ->
        Column(modifier = detailsModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(lexString(R.string.rank_word), style = type.caption, color = colors.textSecondary)
                RankBadge(rank, dimens.crestInline)
            }
            Text(
                buildAnnotatedString {
                    withStyle(type.metricL.toSpanStyle()) { append(formatCount(xpIntoLevel)) }
                    withStyle(type.caption.toSpanStyle().copy(color = colors.textSecondary)) {
                        append(" " + lexString(R.string.xp_of_total, formatCount(xpForLevel)))
                    }
                },
                color = colors.textPrimary,
            )
            ProgressBar(progress = fraction)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(AwakenGlyphs.Flame, contentDescription = null, tint = colors.accent, modifier = Modifier.size(dimens.iconSmall))
                Text(lexPlural(R.plurals.streak_days_inline, streak, streak), style = type.label, color = colors.textPrimary)
                Text(lexString(R.string.best_streak_label, best), style = type.caption, color = colors.textSecondary)
            }
            consistency?.let { ConsistencyMeter(it, Modifier.padding(top = Spacing.xs)) }
        }
    }
    AwakenCard(modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, reveal = false) {
        if (isLargeText()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ring()
                details(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                ring()
                details(Modifier.weight(1f))
            }
        }
    }
}

/** A tile that opens another screen: an icon, a name and one line about what is in it. Two per row on Progress. */
@Composable
fun ShortcutTile(icon: ImageVector, title: String, note: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    AwakenCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$title, $note" },
        reveal = false,
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.icon))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
                Text(note, style = AwakenTheme.type.caption, color = colors.textSecondary, maxLines = 2)
            }
        }
    }
}

/** The five stats as a key under the radar: the marker and the name, so colour never stands alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Stat.entries.forEach { stat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StatMarker(stat)
                Text(stat.label(), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
            }
        }
    }
}

@PhonePreviews
@Composable
private fun ProgressComponentsPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            LevelHero(level = 24, rank = Rank.C, xpIntoLevel = 1_240, xpForLevel = 1_434, streak = 12, best = 18)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                ShortcutTile(AwakenGlyphs.Calendar, "Weekly review", "Open now", {}, Modifier.weight(1f))
                ShortcutTile(AwakenGlyphs.Coin, "Store", "1,240 gold", {}, Modifier.weight(1f))
            }
            StatLegend()
        }
    }
}
