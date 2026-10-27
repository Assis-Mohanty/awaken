package com.assismohanty.awaken.core.designsystem.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/*
 * Line charts for ChartKit (DESIGN_SYSTEM §8.5): smooth curves, a soft gradient under the line, dotted grid lines and a
 * marked latest point. Calm, minimal, data first.
 */

@Composable
internal fun TrendLineChart(spec: ChartSpec.TrendLine, modifier: Modifier) {
    val palette = AwakenTheme.palette
    val color = spec.color ?: palette.system
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(spec.height ?: AwakenTheme.dimens.lineChartHeight)
                .clearAndSetSemantics { contentDescription = spec.description },
        ) {
            val grid = palette.edge
            val dash = PathEffect.dashPathEffect(floatArrayOf(DOT_ON.dp.toPx(), DOT_OFF.dp.toPx()))
            repeat(GRID_LINES) { i ->
                val y = size.height * i / (GRID_LINES - 1)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }
            drawSmoothLine(spec.values, color, fill = true, markLast = true, inset = MARK_RADIUS.dp.toPx() * 2)
        }
        if (spec.labels.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                spec.labels.forEachIndexed { index, label ->
                    Text(
                        label,
                        style = AwakenTheme.type.caption,
                        color = palette.mist,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            spec.labels.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SparklineChart(spec: ChartSpec.Sparkline, modifier: Modifier) {
    val color = spec.color ?: AwakenTheme.palette.system
    Canvas(
        modifier = modifier
            .height(spec.height ?: SPARK_HEIGHT.dp)
            .clearAndSetSemantics { contentDescription = spec.description },
    ) {
        drawSmoothLine(spec.values, color, fill = false, markLast = true, inset = MARK_RADIUS.dp.toPx())
    }
}

@Composable
internal fun ComparisonChart(spec: ChartSpec.Comparison, modifier: Modifier) {
    val palette = AwakenTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spec.description },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        spec.rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label, style = AwakenTheme.type.label, color = palette.mist, modifier = Modifier.weight(1f))
                    Text(row.value, style = AwakenTheme.type.numericSmall, color = palette.bone)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AwakenTheme.dimens.xpBarHeight)
                        .clip(AwakenTheme.shapes.pill)
                        .background(palette.edge),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(row.fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(AwakenTheme.shapes.pill)
                            .background(row.color ?: palette.system),
                    )
                }
            }
        }
    }
}

/** A smooth curve through [values] scaled to the canvas, with an optional gradient fill and a marked last point. */
private fun DrawScope.drawSmoothLine(values: List<Float>, color: Color, fill: Boolean, markLast: Boolean, inset: Float) {
    if (values.isEmpty()) return
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val top = inset
    val bottom = size.height - inset
    val points = values.mapIndexed { i, v ->
        val x = if (values.size == 1) size.width / 2f else inset + (size.width - 2 * inset) * i / (values.size - 1)
        val y = bottom - (bottom - top) * ((v - min) / span)
        Offset(x, y)
    }
    val line = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val midX = (p0.x + p1.x) / 2f
            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
    }
    if (fill && points.size > 1) {
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = FILL_ALPHA), Color.Transparent)))
    }
    drawPath(line, color, style = Stroke(width = LINE_WIDTH.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (markLast) {
        val last = points.last()
        drawCircle(color.copy(alpha = HALO_ALPHA), radius = MARK_RADIUS.dp.toPx() * 2, center = last)
        drawCircle(color, radius = MARK_RADIUS.dp.toPx(), center = last)
    }
}

private const val GRID_LINES = 4
private const val DOT_ON = 2f
private const val DOT_OFF = 4f
private const val LINE_WIDTH = 2.5f
private const val MARK_RADIUS = 4.5f
private const val FILL_ALPHA = 0.28f
private const val HALO_ALPHA = 0.18f
private const val SPARK_HEIGHT = 36
