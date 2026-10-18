package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat

@Immutable
data class ShareCardModel(
    val name: String,
    val rank: Rank,
    val level: Int,
    val statLevels: Map<Stat, Float>,
    val radarMax: Float,
    /** Already-formatted week stats, e.g. "Quests" to "23". */
    val weekStats: List<Pair<String, String>>,
)

/**
 * Weekly share card layout (DESIGN_SYSTEM §3). Laid out at 360×450dp so a 3× capture yields 1080×1350px; the capture
 * (`GraphicsLayer.toImageBitmap()`) and `ACTION_SEND` arrive with the Status screen in Phase 9.
 */
@Composable
fun ShareCard(model: ShareCardModel, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val dimens = AwakenTheme.dimens
    GateBackground(rank = model.rank, modifier = modifier.size(dimens.shareCardWidth, dimens.shareCardHeight)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RankCrest(model.rank, dimens.crestSmall)
                Column {
                    Text(model.name, style = type.title, color = colors.bone)
                    Text(stringResource(R.string.level_short, model.level), style = type.numeric, color = colors.system)
                }
            }
            RadarChart(values = model.statLevels, maxValue = model.radarMax, modifier = Modifier.size(dimens.radarSize * SHARE_RADAR_SCALE))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                model.weekStats.forEach { (label, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, style = type.numeric, color = colors.bone)
                        Text(label, style = type.caption, color = colors.mist)
                    }
                }
            }
            Text(stringResource(R.string.app_name), style = type.windowTitle, color = colors.system)
        }
    }
}

private const val SHARE_RADAR_SCALE = 0.8f

@Preview(widthDp = 360, heightDp = 450)
@Composable
private fun ShareCardPreview() {
    AwakenTheme {
        ShareCard(
            ShareCardModel(
                name = "Assis", rank = Rank.C, level = 24,
                statLevels = mapOf(Stat.STR to 12f, Stat.INT to 9f, Stat.WIL to 6f, Stat.VIT to 10f, Stat.CHA to 4f),
                radarMax = 20f,
                weekStats = listOf("Quests" to "23", "Days cleared" to "6", "Best stat" to "STR"),
            ),
        )
    }
}
