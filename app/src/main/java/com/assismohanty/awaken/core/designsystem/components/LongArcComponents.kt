package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.core.designsystem.charts.CalendarCell
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing

/** How one stage of a Dungeon stands. */
enum class StageMark { DONE, TODAY, UPCOMING, FAILED }

/**
 * A Dungeon's stages as a row of cells (DESIGN_SYSTEM §9.1): done filled, today ringed, upcoming faint, failed outlined.
 * One TalkBack description for the whole row, so the caller says where the run stands in words.
 */
@Composable
fun DungeonStageTrack(stages: List<StageMark>, description: String, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stages.forEachIndexed { index, stage ->
            val cell = Modifier.size(dimens.heroDot * CELL_SCALE).clearAndSetSemantics { }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                when (stage) {
                    StageMark.DONE -> Column(cell.background(colors.accent, CircleShape)) {}
                    StageMark.TODAY -> Column(cell.border(dimens.accentWidth, colors.accent, CircleShape)) {}
                    StageMark.UPCOMING -> Column(cell.background(colors.divider, CircleShape)) {}
                    StageMark.FAILED -> Column(cell.border(dimens.hairline, colors.warning, CircleShape)) {}
                }
                Text((index + 1).toString(), style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

private const val CELL_SCALE = 4

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
private fun DungeonStageTrackPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DungeonStageTrack(listOf(StageMark.DONE, StageMark.TODAY, StageMark.UPCOMING), "Stage 2 of 3")
            DungeonStageTrack(listOf(StageMark.DONE, StageMark.DONE, StageMark.DONE, StageMark.DONE, StageMark.DONE), "Cleared")
            DungeonStageTrack(listOf(StageMark.DONE, StageMark.FAILED, StageMark.UPCOMING, StageMark.UPCOMING, StageMark.UPCOMING, StageMark.UPCOMING, StageMark.UPCOMING), "Failed at stage 2")
        }
    }
}

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
