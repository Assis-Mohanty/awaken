package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.GateRank

/** One shard of the crystal in unit coordinates: its centre, half width and the y of its tip (the base is shared). */
private data class Shard(val cx: Float, val half: Float, val top: Float)

private const val BASE_Y = 0.92f
private const val SHOULDER = 0.28f
private const val FOOT = 0.6f
private const val SHARD_TILT = 12f

/** Which shards each stage shows, back to front. Stage 0 has none: only the seed outline. */
private val STAGE_SHARDS: List<List<Shard>> = listOf(
    emptyList(),
    listOf(Shard(0.5f, 0.10f, 0.70f)),
    listOf(Shard(0.5f, 0.14f, 0.55f)),
    listOf(Shard(0.30f, 0.09f, 0.65f), Shard(0.70f, 0.09f, 0.68f), Shard(0.5f, 0.15f, 0.40f)),
    listOf(Shard(0.30f, 0.11f, 0.50f), Shard(0.70f, 0.11f, 0.52f), Shard(0.5f, 0.16f, 0.25f)),
    listOf(Shard(0.15f, 0.08f, 0.60f), Shard(0.85f, 0.08f, 0.62f), Shard(0.30f, 0.12f, 0.35f), Shard(0.70f, 0.12f, 0.38f), Shard(0.5f, 0.17f, 0.10f)),
)

/**
 * The gate crystal (DESIGN_SYSTEM §9.1, GD §16.5): six stages from a seed outline to a full cluster, facets in [tint] and an
 * outline in `textPrimary` at low alpha. [cracked] adds two fracture lines (a collapsed run); [shard] draws one small
 * fragment (a logged run). Drawn as vectors, no image assets. Decorative unless a [description] is given.
 */
@Composable
fun GateCrystal(
    stage: Int,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    cracked: Boolean = false,
    shard: Boolean = false,
    description: String? = null,
) {
    val ink = AwakenTheme.colors.textPrimary
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier.clearAndSetSemantics { }
    Canvas(modifier = modifier.size(size).then(semantics)) {
        val outline = Stroke(width = OUTLINE.dp.toPx())
        if (shard) {
            rotate(SHARD_TILT, pivot = Offset(this.size.width / 2f, this.size.height * BASE_Y)) {
                drawShard(Shard(0.5f, 0.11f, 0.62f), tint, ink, outline)
            }
            return@Canvas
        }
        val shards = STAGE_SHARDS[stage.coerceIn(0, STAGE_SHARDS.lastIndex)]
        if (shards.isEmpty()) {
            drawShard(Shard(0.5f, 0.07f, 0.78f), tint.copy(alpha = SEED_ALPHA), ink.copy(alpha = OUTLINE_ALPHA), outline)
        }
        shards.forEach { drawShard(it, tint, ink, outline) }
        if (cracked && shards.isNotEmpty()) drawCracks(shards.last(), ink)
    }
}

private fun DrawScope.drawShard(shard: Shard, tint: Color, ink: Color, outline: Stroke) {
    val w = size.width
    val h = size.height
    val shoulder = shard.top + (BASE_Y - shard.top) * SHOULDER
    val cx = shard.cx * w
    val half = shard.half * w
    val body = Path().apply {
        moveTo(cx, shard.top * h)
        lineTo(cx + half, shoulder * h)
        lineTo(cx + half * FOOT, BASE_Y * h)
        lineTo(cx - half * FOOT, BASE_Y * h)
        lineTo(cx - half, shoulder * h)
        close()
    }
    drawPath(body, tint.copy(alpha = BODY_ALPHA))
    // The right-hand facet catches the light.
    val facet = Path().apply {
        moveTo(cx, shard.top * h)
        lineTo(cx + half, shoulder * h)
        lineTo(cx + half * FOOT, BASE_Y * h)
        lineTo(cx, BASE_Y * h)
        close()
    }
    drawPath(facet, tint.copy(alpha = FACET_ALPHA))
    drawPath(body, ink.copy(alpha = OUTLINE_ALPHA), style = outline)
}

private fun DrawScope.drawCracks(centre: Shard, ink: Color) {
    val w = size.width
    val h = size.height
    val line = Stroke(width = CRACK.dp.toPx())
    val colour = ink.copy(alpha = CRACK_ALPHA)
    val y0 = centre.top + (BASE_Y - centre.top) * 0.35f
    val first = Path().apply {
        moveTo(centre.cx * w - centre.half * w * 0.1f, y0 * h)
        lineTo(centre.cx * w + centre.half * w * 0.25f, (y0 + 0.10f) * h)
        lineTo(centre.cx * w - centre.half * w * 0.2f, (y0 + 0.2f) * h)
        lineTo(centre.cx * w + centre.half * w * 0.1f, (y0 + 0.3f) * h)
    }
    val second = Path().apply {
        moveTo(centre.cx * w + centre.half * w * 0.35f, (y0 + 0.05f) * h)
        lineTo(centre.cx * w + centre.half * w * 0.6f, (y0 + 0.15f) * h)
    }
    drawPath(first, colour, style = line)
    drawPath(second, colour, style = line)
}

private const val OUTLINE = 1f
private const val CRACK = 1.5f
private const val OUTLINE_ALPHA = 0.35f
private const val CRACK_ALPHA = 0.7f
private const val BODY_ALPHA = 0.4f
private const val FACET_ALPHA = 0.7f
private const val SEED_ALPHA = 0.15f

/** What the gallery needs to draw and announce one finished run. The caller decides every word and colour. */
data class CrystalCell(
    val key: String,
    val stage: Int,
    val tint: Color,
    val tier: GateRank,
    val cracked: Boolean,
    val shard: Boolean,
    val caption: String,
    val description: String,
)

/**
 * The gallery (DESIGN_SYSTEM §9.1): three columns on a phone, four when wide, each cell a crystal over a short caption.
 * Every cell is one TalkBack stop with the run in words ("Maths, 50 minutes, cleared, 3 November").
 */
@Composable
fun CrystalGalleryGrid(cells: List<CrystalCell>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= WIDE) 4 else 3
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            cells.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cell -> Cell(cell, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Cell(cell: CrystalCell, modifier: Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = cell.description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        GateCrystal(stage = cell.stage, tint = cell.tint, size = tierSize(cell.tier), cracked = cell.cracked, shard = cell.shard)
        Text(
            cell.caption,
            style = AwakenTheme.type.caption,
            color = AwakenTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private val WIDE = 600.dp

/** Four sizes, one per focus tier (GD §16.5): a longer session is a bigger crystal. */
private fun tierSize(tier: GateRank): Dp = when (tier) {
    GateRank.E -> 36.dp
    GateRank.D -> 46.dp
    GateRank.C -> 56.dp
    GateRank.B -> 66.dp
}

@ComponentPreview
@Composable
private fun GateCrystalStagesPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                (0..5).forEach { GateCrystal(stage = it, tint = AwakenTheme.colors.chartSeries2, size = 48.dp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GateCrystal(stage = 3, tint = AwakenTheme.colors.chartSeries1, size = 48.dp, cracked = true)
                GateCrystal(stage = 5, tint = AwakenTheme.colors.chartSeries3, size = 48.dp, cracked = true)
                GateCrystal(stage = 0, tint = AwakenTheme.colors.chartSeries4, size = 48.dp, cracked = true)
                GateCrystal(stage = 0, tint = AwakenTheme.colors.chartSeries5, size = 48.dp, shard = true)
                GateCrystal(stage = 0, tint = AwakenTheme.colors.chartSeries6, size = 48.dp, shard = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
                listOf(32.dp, 44.dp, 56.dp, 68.dp).forEach { GateCrystal(stage = 5, tint = AwakenTheme.colors.accent, size = it) }
            }
        }
    }
}

@ComponentPreview
@Composable
private fun CrystalGalleryGridPreview() {
    val colors = AwakenTheme.colors
    AwakenTheme(reduceMotion = true) {
        CrystalGalleryGrid(
            listOf(
                CrystalCell("1", 5, colors.chartSeries2, GateRank.C, cracked = false, shard = false, caption = "50 min", description = "Maths, 50 minutes, cleared, 3 November"),
                CrystalCell("2", 3, colors.chartSeries1, GateRank.D, cracked = true, shard = false, caption = "25 min", description = "Gym planning, 25 minutes, collapsed, 2 November"),
                CrystalCell("3", 0, colors.chartSeries3, GateRank.E, cracked = false, shard = true, caption = "9 min", description = "Reading, 9 minutes, logged, 1 November"),
                CrystalCell("4", 5, colors.chartSeries4, GateRank.B, cracked = false, shard = false, caption = "90 min", description = "Code, 90 minutes, cleared, 30 October"),
                CrystalCell("5", 5, colors.chartSeries5, GateRank.E, cracked = false, shard = false, caption = "15 min", description = "Chores, 15 minutes, cleared, 29 October"),
            ),
        )
    }
}

/**
 * The colour of a crystal (GD §16.5): the subject's series colour (`chartSeries1…6`, picked by the engine from the subject
 * id), or the stat's own colour when the run has no subject.
 */
@Composable
fun crystalTint(colourIndex: Int?, stat: com.assismohanty.awaken.engine.model.Stat): Color {
    val colors = AwakenTheme.colors
    return when (colourIndex) {
        0 -> colors.chartSeries1
        1 -> colors.chartSeries2
        2 -> colors.chartSeries3
        3 -> colors.chartSeries4
        4 -> colors.chartSeries5
        5 -> colors.chartSeries6
        else -> colors.stat(stat)
    }
}
