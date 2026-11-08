package com.assismohanty.awaken.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.assismohanty.awaken.core.designsystem.preview.ComponentPreview
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.body.Muscle

/**
 * The injury step: a body map on the left lights up the muscles of every selected region, and the regions are the multi-select
 * cards on the right. Picking a card is the only way to change the selection; the figure is just a picture of it. The caller
 * decides what a pick clears ("No injuries").
 */
@Composable
fun BodyMapGrid(
    options: List<ChoiceItem>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    highlighted: Map<Muscle, Float>,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        BodyMap(
            intensity = highlighted,
            description = description,
            accent = AwakenTheme.colors.accent,
            figureHeight = AwakenTheme.dimens.onbBodyMapHeight,
            modifier = Modifier.weight(1f),
        )
        Column(modifier = Modifier.weight(BODY_MAP_OPTIONS_WEIGHT), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            options.forEach { option ->
                ImageChoiceCard(
                    label = option.label,
                    selected = option.key in selectedKeys,
                    onClick = { onToggle(option.key) },
                    multi = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Seven day chips, Monday to Sunday, any number selected. Each is a round letter with today marked underneath, and reads as
 * [dayDescription] ("Tuesday, today") to TalkBack; the 40dp circle has a 48dp touch target.
 *
 * @param selectedDays ISO numbers, 1 = Monday ... 7 = Sunday.
 */
@Composable
fun DayChips(
    selectedDays: Set<Int>,
    today: Int,
    onToggle: (Int) -> Unit,
    dayLetter: (Int) -> String,
    dayDescription: (day: Int, isToday: Boolean) -> String,
    todayLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..DAYS_IN_WEEK).forEach { day ->
            DayChip(
                letter = dayLetter(day),
                description = dayDescription(day, day == today),
                selected = day in selectedDays,
                isToday = day == today,
                todayLabel = todayLabel,
                onClick = { onToggle(day) },
            )
        }
    }
}

@Composable
private fun DayChip(letter: String, description: String, selected: Boolean, isToday: Boolean, todayLabel: String, onClick: () -> Unit) {
    val colors = AwakenTheme.colors
    val haptics = AwakenTheme.haptics
    Column(
        modifier = Modifier
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = {
                haptics.tick()
                onClick()
            })
            .minimumInteractiveComponentSize()
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .size(AwakenTheme.dimens.onbDayChip)
                .clip(AwakenTheme.shapes.pill)
                .background(if (selected) colors.accent else colors.surfaceOverlay),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = AwakenTheme.type.title, color = if (selected) colors.onAccent else colors.textPrimary)
        }
        // The label is announced with the day, so it is not read a second time on its own.
        Text(
            text = if (isToday) todayLabel else "",
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** A switch with its words, in a card: "Remind me on training days". Ticks when it changes. */
@Composable
fun OnboardingToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val haptics = AwakenTheme.haptics
    AwakenCard(modifier = modifier.fillMaxWidth(), reveal = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = {
                    haptics.tick()
                    onChange(it)
                }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(title, style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AwakenSwitch(checked = checked)
        }
    }
}

private const val DAYS_IN_WEEK = 7

/** The figure needs a little less room than the options beside it. */
private const val BODY_MAP_OPTIONS_WEIGHT = 1.25f

@ComponentPreview
@Composable
private fun DayChipsPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DayChips(
                selectedDays = setOf(1, 3, 5), today = 2, onToggle = {},
                dayLetter = { listOf("M", "T", "W", "T", "F", "S", "S")[it - 1] },
                dayDescription = { day, today -> listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[day - 1] + if (today) ", today" else "" },
                todayLabel = "Today",
            )
            OnboardingToggleRow(title = "Remind me on training days", checked = true, onChange = {})
            OnboardingToggleRow(title = "Remind me on training days", checked = false, onChange = {})
        }
    }
}

@ComponentPreview
@Composable
private fun BodyMapGridPreview() {
    AwakenTheme(reduceMotion = true) {
        Box(Modifier.padding(Spacing.gutter)) {
            BodyMapGrid(
                options = listOf(
                    ChoiceItem("none", "No injuries"), ChoiceItem("shoulders", "Shoulders"), ChoiceItem("back", "Back"),
                    ChoiceItem("wrist", "Wrist"), ChoiceItem("knee", "Knee"), ChoiceItem("hip", "Hip"),
                ),
                selectedKeys = setOf("shoulders", "knee"),
                onToggle = {},
                highlighted = mapOf(Muscle.SHOULDERS to 1f, Muscle.QUADRICEPS to 1f),
                description = "Body map",
            )
        }
    }
}
