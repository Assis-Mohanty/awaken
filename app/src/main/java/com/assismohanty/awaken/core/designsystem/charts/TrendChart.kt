package com.assismohanty.awaken.core.designsystem.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.components.SegmentedControl
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import kotlin.math.abs

/**
 * One period of a [TrendChart]: the [values] in order, [axisLabels] spread under the chart (first and last at the
 * edges), a callout line per point ([pointLabels], e.g. "Mon 27 · 3.4") and the [average] already formatted
 * ("Average 3.1"). Values come from the caller; the chart never computes a game number.
 */
@Immutable
data class TrendSeries(
    val values: List<Float>,
    val axisLabels: List<String>,
    val pointLabels: List<String>,
    val average: Float,
    val averageLabel: String,
)

/**
 * Trend chart (DESIGN_SYSTEM §3, §8.5): a Week / Month / Year segmented control over one clean line in [color], three
 * hairline gridlines, a dashed average line with its label, and axis labels below. Tapping a point shows a callout with
 * its label and a guide line; tapping again (or switching period) hides it. No gradient, no glow. One TalkBack node:
 * [description] for the selected period.
 */
@Composable
fun TrendChart(
    periods: List<String>,
    series: List<TrendSeries>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = AwakenTheme.colors.textPrimary,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val data = series.getOrNull(selected) ?: return
    var focus by remember(selected, data) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SegmentedControl(options = periods, selected = selected, onSelect = onSelect, modifier = Modifier.fillMaxWidth())
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(AwakenTheme.dimens.lineChartHeight + CALLOUT_ROOM)
                .semantics { contentDescription = description }
                .pointerInput(data) {
                    detectTapGestures { tap ->
                        val n = data.values.size
                        if (n == 0) return@detectTapGestures
                        val step = if (n > 1) size.width.toFloat() / (n - 1) else 0f
                        val nearest = (0 until n).minBy { abs(it * step - tap.x) }
                        focus = if (focus == nearest) null else nearest
                    }
                },
        ) {
            val values = data.values
            if (values.isEmpty()) return@Canvas
            val top = CALLOUT_ROOM.toPx()
            val bottom = size.height - DOT.toPx()
            val max = maxOf(values.max(), data.average)
            val min = minOf(values.min(), data.average, 0f)
            val span = (max - min).takeIf { it > 0f } ?: 1f
            fun y(v: Float) = bottom - (bottom - top) * ((v - min) / span)
            val step = if (values.size > 1) size.width / (values.size - 1) else 0f
            val points = values.mapIndexed { i, v -> Offset(if (values.size == 1) size.width / 2f else i * step, y(v)) }

            repeat(GRID_LINES) { i ->
                val gy = top + (bottom - top) * i / (GRID_LINES - 1)
                drawLine(colors.chartGrid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            val avgY = y(data.average)
            drawLine(
                colors.textSecondary,
                Offset(0f, avgY),
                Offset(size.width, avgY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx())),
            )
            val avgText = measurer.measure(data.averageLabel, type.caption.copy(color = colors.textSecondary))
            drawText(avgText, topLeft = Offset(size.width - avgText.size.width, avgY - avgText.size.height - 2.dp.toPx()))

            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    val midX = (p0.x + p1.x) / 2f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }
            drawPath(line, color, style = Stroke(width = LINE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val marked = focus ?: points.lastIndex
            drawCircle(colors.surfaceElevated, radius = DOT.toPx() + 2.dp.toPx(), center = points[marked])
            drawCircle(color, radius = DOT.toPx(), center = points[marked])

            focus?.let { i ->
                val p = points[i]
                drawLine(colors.divider, Offset(p.x, top), Offset(p.x, bottom), strokeWidth = 1.dp.toPx())
                val label = measurer.measure(data.pointLabels.getOrElse(i) { "" }, type.label.copy(color = colors.onAccent))
                val padH = Spacing.xs.toPx()
                val padV = Spacing.xxs.toPx()
                val w = label.size.width + padH * 2
                val h = label.size.height + padV * 2
                val x = (p.x - w / 2f).coerceIn(0f, size.width - w)
                drawRoundRect(colors.accent, Offset(x, 0f), Size(w, h), CornerRadius(h / 2f))
                drawText(label, topLeft = Offset(x + padH, padV))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            data.axisLabels.forEachIndexed { index, label ->
                Text(
                    label,
                    style = type.caption,
                    color = colors.textSecondary,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        data.axisLabels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.xxs / 2),
                )
            }
        }
    }
}

private const val GRID_LINES = 3
private val CALLOUT_ROOM = 32.dp
private val LINE = 2.dp
private val DOT = 4.dp
private val DASH_ON = 4.dp
private val DASH_OFF = 4.dp

@ComponentPreview
@Composable
private fun TrendChartPreview() {
    AwakenTheme {
        var period by remember { mutableIntStateOf(0) }
        TrendChart(
            periods = listOf("Week", "Month", "Year"),
            series = listOf(
                TrendSeries(
                    values = listOf(2f, 3f, 2.5f, 4f, 3.5f, 5f, 4.5f),
                    axisLabels = listOf("Mon", "Wed", "Fri", "Sun"),
                    pointLabels = listOf("Mon · 2", "Tue · 3", "Wed · 2.5", "Thu · 4", "Fri · 3.5", "Sat · 5", "Sun · 4.5"),
                    average = 3.5f,
                    averageLabel = "Average 3.5",
                ),
            ),
            selected = period,
            onSelect = { period = it },
            description = "Quests per day this week, average 3.5",
            color = AwakenTheme.colors.accent,
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}
