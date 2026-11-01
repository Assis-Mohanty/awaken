package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.assismohanty.awaken.core.designsystem.charts.Segment
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import androidx.compose.material3.Text

/**
 * A ring filled to [progress] (0..1, clamped) in [color] over an `edge` track, with [content] in the middle. One TalkBack
 * node described by [description].
 */
@Composable
fun ProgressRing(
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = AwakenTheme.palette.system,
    size: Dp = AwakenTheme.dimens.calorieRing,
    stroke: Dp = AwakenTheme.dimens.calorieRingStroke,
    content: @Composable () -> Unit,
) {
    val track = AwakenTheme.palette.edge
    Box(modifier = modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(this.size.width - width, this.size.height - width)
            val topLeft = Offset(inset, inset)
            drawArc(track, startAngle = 0f, sweepAngle = FULL_TURN, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width))
            drawArc(
                color,
                startAngle = START_ANGLE,
                sweepAngle = FULL_TURN * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * The calorie ring's style split into arcs, one per segment by weight, with a small gap between arcs; an empty or
 * all-zero ring is just the track. Same size, stroke and track as [ProgressRing].
 */
@Composable
fun SegmentedRing(
    segments: List<Segment>,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = AwakenTheme.dimens.calorieRing,
    stroke: Dp = AwakenTheme.dimens.calorieRingStroke,
    content: @Composable () -> Unit,
) {
    val track = AwakenTheme.palette.edge
    val total = segments.sumOf { it.weight.toDouble() }.toFloat()
    Box(modifier = modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(this.size.width - width, this.size.height - width)
            val topLeft = Offset(inset, inset)
            drawArc(track, startAngle = 0f, sweepAngle = FULL_TURN, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width))
            val visible = segments.filter { it.weight > 0f }
            if (total > 0f && visible.isNotEmpty()) {
                val gap = if (visible.size > 1) SEGMENT_GAP_DEGREES else 0f
                var start = START_ANGLE
                visible.forEach { segment ->
                    val sweep = FULL_TURN * segment.weight / total
                    drawArc(
                        segment.color,
                        startAngle = start + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(MIN_ARC_DEGREES),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
        }
        content()
    }
}

/** A thin horizontal bar filled to [progress] (0..1, clamped) in [color] over an `edge` track. Decorative. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = AwakenTheme.palette.system) {
    val height = AwakenTheme.dimens.xpBarHeight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(AwakenTheme.shapes.pill)
            .background(AwakenTheme.palette.edge),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .background(color),
        )
    }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val SEGMENT_GAP_DEGREES = 4f
private const val MIN_ARC_DEGREES = 1f

@ComponentPreview
@Composable
private fun ProgressRingPreview() {
    AwakenTheme {
        Column(verticalArrangement = Arrangement.spacedBy(AwakenTheme.dimens.icon)) {
            ProgressRing(progress = 0.62f, description = "1,880 of 3,030 kcal eaten") {
                Text("1,150", style = AwakenTheme.type.numeric, color = AwakenTheme.palette.bone)
            }
            ProgressBar(progress = 0.4f)
            SegmentedRing(
                segments = listOf(Segment(54f, AwakenTheme.colors.critical), Segment(92f, AwakenTheme.colors.positive), Segment(31f, AwakenTheme.colors.textSecondary)),
                description = "2 h 57 min of screen time",
                size = AwakenTheme.dimens.tileRing,
                stroke = AwakenTheme.dimens.tileRingStroke,
            ) {
                Text("2h 57m", style = AwakenTheme.type.numericSmall, color = AwakenTheme.colors.textPrimary)
            }
        }
    }
}
