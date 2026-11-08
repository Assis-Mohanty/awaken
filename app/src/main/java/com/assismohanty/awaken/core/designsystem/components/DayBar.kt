package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat

/** One quest on the bar: its stat (colour), what it is worth (width) and whether it is done (loaded or faint). */
data class DayBarPlate(val stat: Stat, val weight: Int, val done: Boolean)

/**
 * The day as a loaded bar (design/components.md, DayBar): a collar, then one soft-rimmed plate per quest. A plate's width is
 * its reward and its colour is its stat; finished quests sit next to the collar, open ones wait as faint tone plates. An
 * empty day is a bare bar. [description] is read out in place of the picture ("2 of 6 done").
 */
@Composable
fun DayBar(plates: List<DayBarPlate>, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val ordered = plates.filter { it.done } + plates.filterNot { it.done }
    val fills = ordered.map { if (it.done) colors.stat(it.stat) else colors.surfaceOverlay }
    val edges = ordered.map { if (it.done) colors.statEdge(it.stat) else Color.Transparent }
    val rail = colors.divider
    val collar = colors.accent
    val rim = colors.surface.copy(alpha = RIM_ALPHA)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.dayBarHeight)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val barHeight = size.height
        val railHeight = dimens.dayBarRail.toPx()
        val collarWidth = dimens.dayBarCollar.toPx()
        val gap = dimens.dayBarPlateGap.toPx()
        val radius = CornerRadius(dimens.dayBarPlateRadius.toPx())
        val plateHeight = barHeight * PLATE_HEIGHT_SHARE
        val plateTop = (barHeight - plateHeight) / 2f
        // The rail, then the collar that the plates seat against.
        drawRoundRect(rail, Offset(0f, (barHeight - railHeight) / 2f), Size(size.width, railHeight), CornerRadius(railHeight / 2f))
        val collarHeight = barHeight * COLLAR_HEIGHT_SHARE
        drawRoundRect(collar, Offset(0f, (barHeight - collarHeight) / 2f), Size(collarWidth, collarHeight), CornerRadius(collarWidth / 2f))
        if (ordered.isEmpty()) return@Canvas
        val sleeve = size.width * SLEEVE_SHARE - collarWidth - gap
        val total = ordered.sumOf { it.weight.coerceAtLeast(1) }.toFloat()
        val minWidth = dimens.dayBarPlateMin.toPx()
        var x = collarWidth + gap
        ordered.forEachIndexed { index, plate ->
            val share = plate.weight.coerceAtLeast(1) / total
            val width = (sleeve * share - gap).coerceAtLeast(minWidth)
            drawRoundRect(fills[index], Offset(x, plateTop), Size(width, plateHeight), radius)
            if (plate.done) {
                // The soft rim, and the edge that lets the white Charisma plate show on chalk.
                val inset = RIM_INSET.dp.toPx()
                drawRoundRect(rim, Offset(x + inset, plateTop + inset), Size(width - 2 * inset, plateHeight - 2 * inset), radius, style = Stroke(RIM_STROKE.dp.toPx()))
                drawRoundRect(edges[index], Offset(x, plateTop), Size(width, plateHeight), radius, style = Stroke(EDGE_STROKE.dp.toPx()))
            }
            x += width + gap
        }
    }
}

private const val PLATE_HEIGHT_SHARE = 0.88f
private const val COLLAR_HEIGHT_SHARE = 0.5f
private const val SLEEVE_SHARE = 0.8f
private const val RIM_ALPHA = 0.35f
private const val RIM_INSET = 3
private const val RIM_STROKE = 1.5f
private const val EDGE_STROKE = 1f

@ComponentPreview
@Composable
private fun DayBarPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val all = listOf(
                DayBarPlate(Stat.VIT, 24, true), DayBarPlate(Stat.INT, 36, true), DayBarPlate(Stat.WIL, 30, false),
                DayBarPlate(Stat.STR, 40, false), DayBarPlate(Stat.CHA, 20, false), DayBarPlate(Stat.INT, 24, false),
            )
            DayBar(all.map { it.copy(done = false) }, "0 of 6 done")
            DayBar(all, "2 of 6 done")
            DayBar(all.map { it.copy(done = true) }, "6 of 6 done")
            DayBar(emptyList(), "No quests today")
        }
    }
}
