package com.assismohanty.awaken.core.designsystem.components

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Rank
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rank crest (DESIGN_SYSTEM §2.4): the rank letter on a soft disc of the rank colour. System mode keeps the hex frame.
 */
@Composable
fun RankCrest(rank: Rank, size: Dp, modifier: Modifier = Modifier) {
    val palette = AwakenTheme.palette
    val color = palette.rank(rank)
    val description = lexString(R.string.cd_rank_crest, rank.name)
    val edge = AwakenTheme.dimens.edgeWidth
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!palette.glow) {
                drawCircle(color.copy(alpha = DISC_ALPHA))
                return@Canvas
            }
            val radius = this.size.minDimension / 2f * CREST_INSET
            val hex = Path().apply {
                for (i in 0 until SIDES) {
                    val angle = Math.toRadians(ANGLE_OFFSET + i * (360.0 / SIDES))
                    val x = center.x + radius * cos(angle).toFloat()
                    val y = center.y + radius * sin(angle).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(hex, color.copy(alpha = AwakenAlpha.TINT))
            drawPath(hex, color, style = Stroke(width = edge.toPx() * EDGE_STROKE_FACTOR))
        }
        Text(
            text = rank.name,
            style = AwakenTheme.type.metricL.copy(fontSize = AwakenTheme.type.metricL.fontSize * (size / AwakenTheme.dimens.crestLarge)),
            color = color,
        )
    }
}

private const val DISC_ALPHA = 0.14f
private const val SIDES = 6
private const val ANGLE_OFFSET = -90.0
private const val CREST_INSET = 0.92f
private const val EDGE_STROKE_FACTOR = 1.5f

@ComponentPreview
@Composable
private fun RankCrestPreview() {
    AwakenTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Rank.entries.forEach { RankCrest(it, AwakenTheme.dimens.crestSmall) }
        }
    }
}
