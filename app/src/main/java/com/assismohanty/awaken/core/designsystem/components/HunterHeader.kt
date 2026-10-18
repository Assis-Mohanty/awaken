package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank

@Immutable
data class HunterHeaderModel(
    val name: String,
    /** Equipped title, already localised; null when none is equipped. */
    val title: String?,
    val level: Int,
    val rank: Rank,
    val xpIntoLevel: Long,
    val xpForLevel: Long,
    val streak: Int,
    val gold: Long,
)

/** Crest, name, title, level, XP bar, streak flame and gold (DESIGN_SYSTEM §3, §4.2). [large] is the Status variant. */
@Composable
fun HunterHeader(model: HunterHeaderModel, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val dimens = AwakenTheme.dimens
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RankCrest(model.rank, if (large) dimens.crestLarge else dimens.crestSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, style = type.title, color = colors.bone, maxLines = 1, overflow = TextOverflow.Ellipsis)
                model.title?.let { Text(it, style = type.label, color = colors.mist) }
            }
            Counter(
                icon = AwakenGlyphs.Flame,
                value = formatCount(model.streak),
                tint = colors.crit,
                description = pluralStringResource(R.plurals.cd_streak, model.streak, model.streak),
            )
            Counter(
                icon = AwakenGlyphs.Coin,
                value = formatCount(model.gold),
                tint = colors.crit,
                description = stringResource(R.string.cd_gold, formatCount(model.gold)),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(R.string.level_short, model.level),
                style = if (large) type.displayXL else type.display,
                color = colors.bone,
            )
            XpBar(
                level = model.level,
                xpIntoLevel = model.xpIntoLevel,
                xpForLevel = model.xpForLevel,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = Spacing.xs),
            )
        }
    }
}

@Composable
private fun Counter(icon: ImageVector, value: String, tint: Color, description: String) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
        Text(value, style = AwakenTheme.type.numeric, color = AwakenTheme.colors.bone)
    }
}

internal val previewHunter = HunterHeaderModel(
    name = "Assis", title = "Unbroken", level = 24, rank = Rank.C, xpIntoLevel = 1_240, xpForLevel = 1_434, streak = 12, gold = 1_920,
)

@ComponentPreview
@Composable
private fun HunterHeaderPreview() {
    AwakenTheme {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            HunterHeader(previewHunter)
            HunterHeader(previewHunter.copy(name = "A very long hunter name", title = null, rank = Rank.S), large = true)
        }
    }
}
