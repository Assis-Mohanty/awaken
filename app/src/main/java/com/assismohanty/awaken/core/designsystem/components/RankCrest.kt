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
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank

/** Rank crest (DESIGN_SYSTEM §2.4): the rank letter in ink on a 15% disc of the rank colour. */
@Composable
fun RankCrest(rank: Rank, size: Dp, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val description = lexString(R.string.cd_rank_crest, rank.name)
    Box(
        modifier = modifier
            .size(size)
            .clip(AwakenTheme.shapes.pill)
            .background(colors.rank(rank).copy(alpha = AwakenAlpha.TINT))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.name,
            style = AwakenTheme.type.metricL.copy(fontSize = AwakenTheme.type.metricL.fontSize * (size / AwakenTheme.dimens.crestLarge)),
            color = colors.textPrimary,
        )
    }
}

@ComponentPreview
@Composable
private fun RankCrestPreview() {
    AwakenTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Rank.entries.forEach { RankCrest(it, AwakenTheme.dimens.crestSmall) }
        }
    }
}
