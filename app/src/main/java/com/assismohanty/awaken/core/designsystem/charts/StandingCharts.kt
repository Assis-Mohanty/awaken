package com.assismohanty.awaken.core.designsystem.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

/*
 * Standing charts for ChartKit (GD §18.3): a five-band level gauge and a distribution curve with a draggable pointer.
 * The Plates palette has no hue, so bands step up in ink weight and the markers use the primary ink.
 */

@Composable
internal fun LevelGaugeChart(spec: ChartSpec.LevelGauge, modifier: Modifier) {
    val palette = AwakenTheme.palette
    val bands = spec.labels.size.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spec.description }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(spec.height ?: GAUGE_HEIGHT.dp)) {
            val stroke = GAUGE_STROKE.dp.toPx()
            val radius = (size.width / 2f - stroke).coerceAtMost(size.height - stroke)
            val center = Offset(size.width / 2f, size.height - stroke / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val gap = GAP_DEGREES
            val sweep = 180f / bands
            repeat(bands) { i ->
                val alpha = BAND_ALPHA_MIN + (1f - BAND_ALPHA_MIN) * i / (bands - 1).coerceAtLeast(1)
                drawArc(
                    color = palette.bone.copy(alpha = alpha),
                    startAngle = 180f + i * sweep + gap / 2f,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
            val angle = Math.toRadians(180.0 + 180.0 * spec.position.coerceIn(0f, 1f))
            val marker = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
            drawCircle(palette.void, radius = stroke * MARKER_HALO, center = marker)
            drawCircle(palette.system, radius = stroke * MARKER_DOT, center = marker)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            spec.labels.forEach { label ->
                Text(label, style = AwakenTheme.type.caption, color = palette.mist, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun DistributionCurveChart(spec: ChartSpec.DistributionCurve, modifier: Modifier) {
    val palette = AwakenTheme.palette
    val values = spec.density
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(spec.height ?: CURVE_HEIGHT.dp)
            .pointerInput(values) {
                detectTapGestures { spec.onSelect((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(values) {
                detectHorizontalDragGestures { change, _ -> spec.onSelect((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .clearAndSetSemantics {
                contentDescription = spec.description
                stateDescription = spec.selectedText
                progressBarRangeInfo = ProgressBarRangeInfo(spec.selected, 0f..1f, SLIDER_STEPS)
                setProgress { target ->
                    spec.onSelect(target.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        if (values.size < 2) return@Canvas
        val pad = PAD.dp.toPx()
        val top = pad
        val bottom = size.height - pad
        fun x(fraction: Float) = pad + (size.width - 2 * pad) * fraction
        fun y(density: Float) = bottom - (bottom - top) * density.coerceIn(0f, 1f)
        val line = Path().apply {
            values.forEachIndexed { i, d -> val px = x(i / values.lastIndex.toFloat()); if (i == 0) moveTo(px, y(d)) else lineTo(px, y(d)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(1f), bottom)
            lineTo(x(0f), bottom)
            close()
        }
        drawPath(area, palette.bone.copy(alpha = FILL_ALPHA))
        drawLine(palette.edge, Offset(x(0f), bottom), Offset(x(1f), bottom), strokeWidth = 1.dp.toPx())
        drawPath(line, palette.bone, style = Stroke(width = LINE_WIDTH.dp.toPx(), cap = StrokeCap.Round))
        val dash = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.dp.toPx(), DASH_OFF.dp.toPx()))
        // Where the hunter stands: a solid line. The pointer being dragged: a dashed line and a dot on the curve.
        drawLine(palette.system, Offset(x(spec.marker), top), Offset(x(spec.marker), bottom), strokeWidth = 2.dp.toPx())
        val pointer = x(spec.selected)
        drawLine(palette.mist, Offset(pointer, top), Offset(pointer, bottom), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        val index = (spec.selected * values.lastIndex).toInt().coerceIn(0, values.lastIndex)
        drawCircle(palette.void, radius = DOT_HALO.dp.toPx(), center = Offset(pointer, y(values[index])))
        drawCircle(palette.system, radius = DOT.dp.toPx(), center = Offset(pointer, y(values[index])))
    }
}

private const val GAUGE_HEIGHT = 96f
private const val GAUGE_STROKE = 14f
private const val GAP_DEGREES = 3f
private const val BAND_ALPHA_MIN = 0.22f
private const val MARKER_HALO = 0.9f
private const val MARKER_DOT = 0.55f
private const val CURVE_HEIGHT = 120f
private const val PAD = 8f
private const val FILL_ALPHA = 0.12f
private const val LINE_WIDTH = 2f
private const val DASH_ON = 3f
private const val DASH_OFF = 3f
private const val DOT = 4.5f
private const val DOT_HALO = 7f
private const val SLIDER_STEPS = 98
