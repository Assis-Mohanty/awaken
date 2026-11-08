package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank

/** Rank badge (design/components.md): the rank letter in a round ink badge. Ranks carry no colour of their own. */
@Composable
fun RankBadge(rank: Rank, size: Dp, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val description = lexString(R.string.cd_rank_crest, rank.name)
    Box(
        modifier = modifier
            .size(size)
            .clip(AwakenTheme.shapes.pill)
            .background(colors.accent)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.name,
            style = AwakenTheme.type.numeric.copy(fontSize = (size.value * LETTER_RATIO).sp, lineHeight = TextUnit.Unspecified),
            color = colors.onAccent,
        )
    }
}

private const val LETTER_RATIO = 0.55f

@ComponentPreview
@Composable
private fun RankBadgePreview() {
    AwakenTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Rank.entries.forEach { RankBadge(it, AwakenTheme.dimens.crestSmall) }
        }
    }
}
