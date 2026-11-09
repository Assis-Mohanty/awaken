package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import java.time.LocalDate

/** One day of the week strip: a short weekday name, the date number and what the day holds. [description] is read out in full. */
data class WeekStripDay(
    val date: LocalDate,
    val label: String,
    val number: String,
    val today: Boolean,
    val selected: Boolean,
    /** A workout was finished on this day: a dot under the number. */
    val done: Boolean,
    /** A rest day reads quieter than a workout day. */
    val rest: Boolean,
    val description: String,
)

/**
 * The week as seven tappable days (Gym home): weekday name over the date, today marked by a bar, the selected day filled
 * with the accent, finished days dotted, rest days quieter. Each cell is a 48dp-wide target and speaks its own [WeekStripDay.description].
 */
@Composable
fun WeekStrip(days: List<WeekStripDay>, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        days.forEach { day -> WeekStripCell(day, onClick = { onSelect(day.date) }, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun WeekStripCell(day: WeekStripDay, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val ink = when {
        day.selected -> colors.onAccent
        day.rest -> colors.textMuted
        else -> colors.textPrimary
    }
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget + Spacing.md)
            .clip(AwakenTheme.shapes.inner)
            .background(if (day.selected) colors.accent else colors.surfaceOverlay)
            .selectable(selected = day.selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = day.description }
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(day.label, style = AwakenTheme.type.micro, color = if (day.selected) colors.onAccent else colors.textSecondary, textAlign = TextAlign.Center)
        Text(day.number, style = AwakenTheme.type.numericSmall, color = ink, textAlign = TextAlign.Center)
        // Today wears a bar, a finished day a dot; both can show at once, so each keeps its own slot.
        Box(modifier = Modifier.size(width = Spacing.md, height = Spacing.xxs / 2).then(if (day.today) Modifier.background(if (day.selected) colors.onAccent else colors.accent) else Modifier))
        Box(modifier = Modifier.size(Spacing.xs / 2 + Spacing.xxs / 2).then(if (day.done) Modifier.clip(AwakenTheme.shapes.pill).background(if (day.selected) colors.onAccent else colors.positive) else Modifier))
    }
}

@ComponentPreview
@Composable
private fun WeekStripPreview() {
    AwakenTheme {
        val monday = LocalDate.of(2026, 11, 9)
        WeekStrip(
            days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").mapIndexed { i, label ->
                WeekStripDay(
                    date = monday.plusDays(i.toLong()), label = label, number = (9 + i).toString(), today = i == 1, selected = i == 1,
                    done = i == 0, rest = i == 2 || i == 5 || i == 6, description = "$label ${9 + i}",
                )
            },
            onSelect = {},
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}
