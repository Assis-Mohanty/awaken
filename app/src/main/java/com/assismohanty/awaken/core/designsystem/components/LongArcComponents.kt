package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.core.designsystem.charts.CalendarCell
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** How one day of a program run stands. */
enum class ProgramDayMark { KEPT, REST, BROKEN, TODAY, UPCOMING }

/**
 * A program's days as a calendar grid (DESIGN_SYSTEM §9.1): kept filled, rest muted, broken outlined, today ringed,
 * upcoming faint. [description] is the one TalkBack summary ("Day 12 of 66: 10 kept, 1 rest, 1 broken").
 */
@Composable
fun ProgramCalendar(days: List<ProgramDayMark>, description: String, modifier: Modifier = Modifier, columns: Int = CALENDAR_COLUMNS) {
    Chart(
        ChartSpec.CalendarGrid(
            cells = days.map {
                when (it) {
                    ProgramDayMark.KEPT -> CalendarCell.FILLED
                    ProgramDayMark.REST -> CalendarCell.REST
                    ProgramDayMark.BROKEN -> CalendarCell.BROKEN
                    ProgramDayMark.TODAY -> CalendarCell.CURRENT
                    ProgramDayMark.UPCOMING -> CalendarCell.UPCOMING
                }
            },
            columns = columns,
            description = description,
        ),
        modifier = modifier,
    )
}

private const val CALENDAR_COLUMNS = 10

@ComponentPreview
@Composable
private fun ProgramCalendarPreview() {
    val kept = List(9) { ProgramDayMark.KEPT }
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ProgramCalendar(listOf(ProgramDayMark.TODAY) + List(29) { ProgramDayMark.UPCOMING }, "Day 1 of 30")
            ProgramCalendar(
                kept + listOf(ProgramDayMark.BROKEN, ProgramDayMark.REST, ProgramDayMark.KEPT, ProgramDayMark.TODAY) + List(17) { ProgramDayMark.UPCOMING },
                "Day 14 of 30",
            )
            ProgramCalendar(List(30) { if (it == 7) ProgramDayMark.BROKEN else ProgramDayMark.KEPT }, "Finished")
        }
    }
}
