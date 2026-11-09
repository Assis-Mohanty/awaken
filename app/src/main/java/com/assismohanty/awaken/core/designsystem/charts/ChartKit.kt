package com.assismohanty.awaken.core.designsystem.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.assismohanty.awaken.core.designsystem.components.Heatmap
import com.assismohanty.awaken.core.designsystem.components.HeatmapDay
import com.assismohanty.awaken.core.designsystem.components.ProgressRing
import com.assismohanty.awaken.core.designsystem.components.SegmentedRing
import com.assismohanty.awaken.core.designsystem.components.RadarChart
import com.assismohanty.awaken.core.designsystem.components.radarDescription
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.model.Stat

/**
 * ChartKit (DESIGN_SYSTEM §8.5): the one way screens draw data. A screen asks for a chart by [ChartSpec] and data; it never
 * touches Canvas or styling modifiers itself, so a redesign restyles every chart here. Colours come in as semantic tokens
 * from the caller; anything left null uses the chart's default.
 */
sealed interface ChartSpec {
    /** Stat pentagon; [previous] draws the dashed comparison polygon. */
    data class Radar(
        val values: Map<Stat, Float>,
        val maxValue: Float,
        val previous: Map<Stat, Float>? = null,
        val description: String? = null,
    ) : ChartSpec

    /** 18-week activity grid; tapping a day calls [onDayClick]. */
    data class Heatmap(val days: List<HeatmapDay>, val onDayClick: (HeatmapDay) -> Unit) : ChartSpec

    /** A ring filled to [progress] with the chart's `content` in the middle. One TalkBack node. */
    data class Ring(
        val progress: Float,
        val description: String,
        val color: Color? = null,
        val size: Dp? = null,
        val stroke: Dp? = null,
    ) : ChartSpec

    /** A full ring split into weighted coloured arcs (e.g. today's screen time by category), `content` in the middle. One TalkBack node. */
    data class SegmentedRing(
        val segments: List<Segment>,
        val description: String,
        val size: Dp? = null,
        val stroke: Dp? = null,
    ) : ChartSpec

    /** A horizontal bar filled to [progress] (0..1, clamped) over [track]. Decorative unless the caller adds semantics. */
    data class Progress(
        val progress: Float,
        val color: Color? = null,
        val track: Color? = null,
        val shape: BarShape = BarShape.PILL,
        val height: Dp? = null,
    ) : ChartSpec

    /** One bar split into weighted coloured segments (e.g. screen time by category, sleep stages). Hidden from TalkBack: callers list the numbers. */
    data class Segmented(
        val segments: List<Segment>,
        val track: Color? = null,
        val shape: BarShape? = BarShape.CHAMFER,
        val height: Dp? = null,
    ) : ChartSpec

    /** Vertical bars scaled to a fixed area, each with a label underneath (e.g. a 7-day trend). */
    data class Columns(
        /** Bar heights as fractions of the area, 0..1. */
        val fractions: List<Float>,
        val labels: List<String>,
        val color: Color,
        val description: String,
        val areaHeight: Dp? = null,
    ) : ChartSpec

    /** Bars filling a fixed height, each a base part with an optional highlighted part on top (e.g. total vs distracting minutes). */
    data class StackedColumns(val columns: List<StackedColumn>, val base: Color? = null, val highlight: Color? = null, val height: Dp? = null) : ChartSpec

    /** A grid of day cells, [columns] to a row (e.g. a program run). */
    data class CalendarGrid(val cells: List<CalendarCell>, val columns: Int, val description: String) : ChartSpec

    /** A smooth trend line with a soft gradient, dotted grid and the latest point marked; [labels] run under it. */
    data class TrendLine(
        val values: List<Float>,
        val labels: List<String>,
        val description: String,
        val color: Color? = null,
        val height: Dp? = null,
    ) : ChartSpec

    /** A tiny line for a card corner ("Daily steps"). */
    data class Sparkline(val values: List<Float>, val description: String, val color: Color? = null, val height: Dp? = null) : ChartSpec

    /** Labelled bars side by side for comparing a few values (insights: "7 h+ sleep vs less"). */
    data class Comparison(val rows: List<ComparisonRow>, val description: String) : ChartSpec

    /** A semicircle in equal bands (one per [labels] entry, lowest first) with a marker at [position] (0..1). One TalkBack node. */
    data class LevelGauge(val position: Float, val labels: List<String>, val description: String, val height: Dp? = null) : ChartSpec

    /**
     * A smooth density ([density], 0..1, evenly spaced) with the hunter's [marker] and a draggable [selected] pointer, both 0..1
     * along the curve. No y-axis and no peak value. TalkBack gets a slider: [selectedText] is its value, [onSelect] its setter.
     */
    data class DistributionCurve(
        val density: List<Float>,
        val marker: Float,
        val selected: Float,
        val selectedText: String,
        val description: String,
        val onSelect: (Float) -> Unit,
        val height: Dp? = null,
    ) : ChartSpec
}

enum class BarShape { SQUARE, CHAMFER, PILL }

data class Segment(val weight: Float, val color: Color)

/** [total] and [highlight] are fractions of the tallest column (0..1); [description] is that column's TalkBack text. */
data class StackedColumn(val total: Float, val highlight: Float, val description: String)

enum class CalendarCell { FILLED, CURRENT, PAST, UPCOMING, REST, BROKEN }

/** One bar of a [ChartSpec.Comparison]: [fraction] of the widest (0..1), [value] as shown text. */
data class ComparisonRow(val label: String, val value: String, val fraction: Float, val color: Color? = null)

@Composable
fun Chart(spec: ChartSpec, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    when (spec) {
        is ChartSpec.Radar -> RadarChart(
            values = spec.values,
            maxValue = spec.maxValue,
            modifier = modifier,
            previous = spec.previous,
            description = spec.description ?: radarDescription(spec.values),
        )
        is ChartSpec.Heatmap -> Heatmap(days = spec.days, onDayClick = spec.onDayClick, modifier = modifier)
        is ChartSpec.Ring -> ProgressRing(
            progress = spec.progress,
            description = spec.description,
            modifier = modifier,
            color = spec.color ?: AwakenTheme.palette.system,
            size = spec.size ?: AwakenTheme.dimens.calorieRing,
            stroke = spec.stroke ?: AwakenTheme.dimens.calorieRingStroke,
            content = content,
        )
        is ChartSpec.SegmentedRing -> SegmentedRing(
            segments = spec.segments,
            description = spec.description,
            modifier = modifier,
            size = spec.size ?: AwakenTheme.dimens.calorieRing,
            stroke = spec.stroke ?: AwakenTheme.dimens.calorieRingStroke,
            content = content,
        )
        is ChartSpec.Progress -> ProgressBarChart(spec, modifier)
        is ChartSpec.Segmented -> SegmentedChart(spec, modifier)
        is ChartSpec.Columns -> ColumnsChart(spec, modifier)
        is ChartSpec.StackedColumns -> StackedColumnsChart(spec, modifier)
        is ChartSpec.CalendarGrid -> CalendarGridChart(spec, modifier)
        is ChartSpec.TrendLine -> TrendLineChart(spec, modifier)
        is ChartSpec.Sparkline -> SparklineChart(spec, modifier)
        is ChartSpec.Comparison -> ComparisonChart(spec, modifier)
        is ChartSpec.LevelGauge -> LevelGaugeChart(spec, modifier)
        is ChartSpec.DistributionCurve -> DistributionCurveChart(spec, modifier)
    }
}

@Composable
private fun BarShape.toShape() = when (this) {
    BarShape.SQUARE -> null
    BarShape.CHAMFER -> AwakenTheme.shapes.xpBar
    BarShape.PILL -> AwakenTheme.shapes.pill
}

@Composable
private fun ProgressBarChart(spec: ChartSpec.Progress, modifier: Modifier) {
    val height = spec.height ?: AwakenTheme.dimens.xpBarHeight
    val shape = spec.shape.toShape()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .background(spec.track ?: AwakenTheme.palette.edge),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(spec.progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(spec.color ?: AwakenTheme.palette.system),
        )
    }
}

@Composable
private fun SegmentedChart(spec: ChartSpec.Segmented, modifier: Modifier) {
    val height = spec.height ?: AwakenTheme.dimens.xpBarHeight
    val shape = spec.shape?.toShape()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .then(if (spec.track != null) Modifier.background(spec.track) else Modifier)
            .clearAndSetSemantics { },
    ) {
        spec.segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(segment.weight)
                    .fillMaxHeight()
                    .background(segment.color),
            )
        }
    }
}

@Composable
private fun ColumnsChart(spec: ChartSpec.Columns, modifier: Modifier) {
    val area = spec.areaHeight ?: AwakenTheme.dimens.columnChartHeight
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spec.description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.Bottom,
    ) {
        spec.fractions.forEachIndexed { index, fraction ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(area * fraction)
                        .clip(AwakenTheme.shapes.columnBar)
                        .background(spec.color),
                )
                Text(spec.labels.getOrElse(index) { "" }, style = AwakenTheme.type.caption, color = AwakenTheme.palette.mist)
            }
        }
    }
}

@Composable
private fun StackedColumnsChart(spec: ChartSpec.StackedColumns, modifier: Modifier) {
    val base = spec.base ?: AwakenTheme.palette.edge
    val highlight = spec.highlight ?: AwakenTheme.palette.ember
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(spec.height ?: AwakenTheme.dimens.trendBarHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        spec.columns.forEach { column ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics(mergeDescendants = true) { contentDescription = column.description },
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Empty space on top, so every bar rises from the baseline with the highlighted part at its foot.
                Spacer(modifier = Modifier.weight((1f - column.total).coerceAtLeast(MIN_WEIGHT)))
                Box(modifier = Modifier.fillMaxWidth().weight((column.total - column.highlight).coerceAtLeast(MIN_WEIGHT), fill = true).clip(AwakenTheme.shapes.columnBar).background(base))
                if (column.highlight > 0f) {
                    Box(modifier = Modifier.fillMaxWidth().weight(column.highlight, fill = true).background(highlight))
                }
            }
        }
    }
}

@Composable
private fun CalendarGridChart(spec: ChartSpec.CalendarGrid, modifier: Modifier) {
    val palette = AwakenTheme.palette
    val dimens = AwakenTheme.dimens
    val shape = AwakenTheme.shapes.tile
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spec.description },
    ) {
        spec.cells.chunked(spec.columns).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), modifier = Modifier.fillMaxWidth()) {
                week.forEach { state ->
                    val cell = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                    Box(
                        modifier = when (state) {
                            CalendarCell.FILLED -> cell.background(palette.system, shape)
                            CalendarCell.CURRENT -> cell.border(dimens.accentWidth, palette.crit, shape)
                            CalendarCell.PAST -> cell.border(dimens.hairline, palette.fog, shape)
                            CalendarCell.UPCOMING -> cell.background(palette.edge, shape)
                            CalendarCell.REST -> cell.background(palette.fog.copy(alpha = REST_ALPHA), shape)
                            CalendarCell.BROKEN -> cell.border(dimens.hairline, palette.crit, shape)
                        },
                    )
                }
            }
        }
    }
}

/** Keeps a zero-height part from collapsing the weights. */
private const val MIN_WEIGHT = 0.001f
private const val REST_ALPHA = 0.45f
