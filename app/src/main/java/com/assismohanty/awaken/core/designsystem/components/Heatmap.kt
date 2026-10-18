package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenAlpha
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import java.time.LocalDate

@Immutable
data class HeatmapDay(
    val date: LocalDate,
    /** 0 (nothing) … 4 (fully cleared). */
    val intensity: Int,
    val restDay: Boolean = false,
)

/**
 * 18 weeks × 7 days (DESIGN_SYSTEM §3). Columns are weeks (oldest left), rows Monday → Sunday. Intensity uses `system`
 * alpha steps; rest days are outlined. Tap reports the day; TalkBack gets a summary.
 */
@Composable
fun Heatmap(
    days: List<HeatmapDay>,
    onDayClick: (HeatmapDay) -> Unit,
    modifier: Modifier = Modifier,
    weeks: Int = WEEKS,
) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val firstMonday = days.minOfOrNull { it.date }?.let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    val grid = days.associateBy { it.date }
    val clearedDays = days.count { it.intensity > 0 }
    val description = pluralStringResource(R.plurals.cd_heatmap, clearedDays, weeks, clearedDays)
    val cell = dimens.heatmapCell
    val gap = dimens.heatmapGap

    Canvas(
        modifier = modifier
            .size(width = (cell + gap) * weeks, height = (cell + gap) * DAYS_PER_WEEK)
            .semantics { contentDescription = description }
            .pointerInput(days) {
                detectTapGestures { tap ->
                    val step = (cell + gap).toPx()
                    val week = (tap.x / step).toInt()
                    val day = (tap.y / step).toInt()
                    val date = firstMonday?.plusDays(week * DAYS_PER_WEEK.toLong() + day)
                    date?.let { grid[it] }?.let(onDayClick)
                }
            },
    ) {
        val cellPx = cell.toPx()
        val step = (cell + gap).toPx()
        for (week in 0 until weeks) {
            for (day in 0 until DAYS_PER_WEEK) {
                val date = firstMonday?.plusDays(week * DAYS_PER_WEEK.toLong() + day)
                val entry = date?.let { grid[it] }
                val topLeft = Offset(week * step, day * step)
                val intensity = (entry?.intensity ?: 0).coerceIn(0, AwakenAlpha.HEATMAP_STEPS.lastIndex)
                if (entry?.restDay == true) {
                    drawRect(colors.system, topLeft, Size(cellPx, cellPx), style = Stroke(width = dimens.hairline.toPx()))
                } else {
                    drawRect(colors.system.copy(alpha = AwakenAlpha.HEATMAP_STEPS[intensity]), topLeft, Size(cellPx, cellPx))
                }
            }
        }
    }
}

const val WEEKS = 18
private const val DAYS_PER_WEEK = 7

@ComponentPreview
@Composable
private fun HeatmapPreview() {
    AwakenTheme {
        val start = LocalDate.of(2026, 6, 15) // a Monday
        val days = (0 until WEEKS * 7).map { i ->
            HeatmapDay(start.plusDays(i.toLong()), intensity = (i * 7 + i / 3) % 5, restDay = i % 13 == 6)
        }
        Heatmap(days = days, onDayClick = {}, modifier = Modifier.padding(Spacing.gutter))
    }
}
