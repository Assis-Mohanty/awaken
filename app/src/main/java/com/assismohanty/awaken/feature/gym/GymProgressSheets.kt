package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.components.PlateDiagram
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.formatNumber
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.GymAnswers
import com.assismohanty.awaken.core.domain.GymPrefs
import com.assismohanty.awaken.engine.body.ExerciseHistory
import com.assismohanty.awaken.engine.body.HistoryMetric
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.domain.ExerciseProgression
import com.assismohanty.awaken.engine.body.ExerciseSession
import com.assismohanty.awaken.engine.body.PlateSetup
import java.time.LocalDate
import com.assismohanty.awaken.engine.body.HistoryRange
import com.assismohanty.awaken.engine.body.LoggedSet
import com.assismohanty.awaken.engine.body.PlateMath
import com.assismohanty.awaken.engine.body.PlatePlan
import com.assismohanty.awaken.engine.body.PlateUnit
import com.assismohanty.awaken.engine.body.ProgressionRules
import com.assismohanty.awaken.engine.body.RepRange

/** The plate calculator (GD §16.8): a target in kg or lb, the plates per side and a diagram. Workouts stay in kg. */
@Composable
internal fun PlatesSheet(plates: PlatesUi, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.ClosePlates) }, title = lexString(R.string.gym_plates_title)) { PlatesContent(plates, onEvent) }
}

@Composable
internal fun PlatesContent(plates: PlatesUi, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val unitLabel = unitText(plates.unit)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PlateUnit.entries.forEach { unit ->
            ChoiceChip(text = unitText(unit), selected = unit == plates.unit, onClick = { onEvent(GymEvent.PlateUnitPicked(unit)) })
        }
    }
    Text(lexString(R.string.gym_plates_target, unitLabel), style = AwakenTheme.type.label, color = colors.textSecondary)
    NumberField(
        value = plates.target,
        onValueChange = { onEvent(GymEvent.PlateTarget(it)) },
        description = lexString(R.string.gym_plates_target, unitLabel),
        key = plates.unit,
        suffix = unitLabel,
    )
    when (val plan = plates.plan) {
        null -> Text(lexString(R.string.gym_plates_enter), style = AwakenTheme.type.body, color = colors.textSecondary)
        is PlatePlan.BelowBar ->
            Text(lexString(R.string.gym_plates_below, formatNumber(plan.barWeight), unitLabel), style = AwakenTheme.type.body, color = colors.textSecondary)
        is PlatePlan.Exact -> PlateResult(plan.perSide, unitLabel)
        is PlatePlan.Short -> {
            PlateResult(plan.perSide, unitLabel)
            Text(
                lexString(R.string.gym_plates_short, formatNumber(plan.built), formatNumber(plan.shortBy), unitLabel),
                style = AwakenTheme.type.body,
                color = colors.textSecondary,
            )
        }
    }
    QuietButton(
        text = lexString(R.string.gym_plates_edit),
        onClick = {
            onEvent(GymEvent.ClosePlates)
            onEvent(GymEvent.OpenGymSettings)
        },
    )
}

@Composable
private fun PlateResult(perSide: List<Double>, unitLabel: String) {
    val labels = perSide.map { formatNumber(it) }
    val summary = if (labels.isEmpty()) {
        lexString(R.string.gym_plates_empty_bar)
    } else {
        lexString(R.string.gym_plates_exact, labels.joinToString(" + ") { "$it $unitLabel" })
    }
    PlateDiagram(platesPerSide = perSide, labels = labels, description = summary)
    Text(summary, style = AwakenTheme.type.title, color = AwakenTheme.colors.textPrimary)
}

@Composable
private fun unitText(unit: PlateUnit) = lexString(if (unit == PlateUnit.KG) R.string.gym_plates_unit_kg else R.string.gym_plates_unit_lb)

/** Gym settings (GD §16.6, §16.8): bar weight and owned plates per unit, and the default rep range. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GymSettingsSheet(prefs: GymPrefs, answers: GymAnswers, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseGymSettings) }, title = lexString(R.string.gym_settings_title)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GymSettingsContent(prefs, answers, onEvent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GymSettingsContent(prefs: GymPrefs, answers: GymAnswers, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    GymAnswersContent(answers, prefs.unit, onEvent)
    PlateUnit.entries.forEach { unit ->
        val label = unitText(unit)
        val setup = prefs.setup(unit)
        Text(lexString(R.string.gym_settings_bar, label), style = AwakenTheme.type.label, color = colors.textPrimary)
        NumberField(
            value = setup.barWeight,
            onValueChange = { if (it > 0.0) onEvent(GymEvent.SetBar(unit, it)) },
            description = lexString(R.string.gym_settings_bar, label),
            key = unit,
            suffix = label,
        )
        Text(lexString(R.string.gym_settings_plates, label), style = AwakenTheme.type.label, color = colors.textPrimary)
        val candidates = (PlateMath.defaults(unit).plates + setup.plates).distinct().sortedDescending()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            candidates.forEach { plate ->
                val owned = plate in setup.plates
                ChoiceChip(
                    text = formatNumber(plate),
                    selected = owned,
                    onClick = { onEvent(GymEvent.SetPlateOwned(unit, plate, !owned)) },
                    multiSelect = true,
                )
            }
        }
        AddPlateRow(unit, label, onEvent)
    }
    Text(lexString(R.string.gym_settings_range), style = AwakenTheme.type.label, color = colors.textPrimary)
    RangeSteppers(prefs.repRange, onChange = { onEvent(GymEvent.SetDefaultRepRange(it)) })
    Text(lexString(R.string.gym_settings_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
}

@Composable
private fun AddPlateRow(unit: PlateUnit, label: String, onEvent: (GymEvent) -> Unit) {
    var plate by remember(unit) { mutableDoubleStateOf(0.0) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        NumberField(value = plate, onValueChange = { plate = it }, description = lexString(R.string.gym_settings_plates, label), key = unit, suffix = label)
        SecondaryButton(
            text = lexString(R.string.pomodoro_plus),
            onClick = {
                if (plate > 0.0) onEvent(GymEvent.SetPlateOwned(unit, plate, true))
                plate = 0.0
            },
            enabled = plate > 0.0,
        )
    }
}

/** Two − / + pairs for a rep range's minimum and maximum, kept inside 1 to 30 with min ≤ max. */
@Composable
private fun RangeSteppers(range: RepRange, onChange: (RepRange) -> Unit) {
    val colors = AwakenTheme.colors
    val bounds = ProgressionRules.REP_BOUNDS
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        StepButton(lexString(R.string.pomodoro_minus), lexString(R.string.gym_settings_min_less), range.min > bounds.first) { onChange(range.copy(min = range.min - 1)) }
        StepButton(lexString(R.string.pomodoro_plus), lexString(R.string.gym_settings_min_more), range.min < range.max) { onChange(range.copy(min = range.min + 1)) }
        Text(
            lexString(R.string.gym_settings_range_value, range.min, range.max),
            style = AwakenTheme.type.numeric,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        StepButton(lexString(R.string.pomodoro_minus), lexString(R.string.gym_settings_max_less), range.max > range.min) { onChange(range.copy(max = range.max - 1)) }
        StepButton(lexString(R.string.pomodoro_plus), lexString(R.string.gym_settings_max_more), range.max < bounds.last) { onChange(range.copy(max = range.max + 1)) }
    }
}

@Composable
private fun StepButton(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    SecondaryButton(text = text, onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description })
}

/** One exercise's history (GD §16.9): range chips, three trend cards, the session list and the hint overrides. */
@Composable
internal fun HistorySheet(history: HistoryUi, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseHistory) }, title = lexString(R.string.gym_history_screen_title, history.name)) { HistoryContent(history, onEvent) }
}

@Composable
internal fun HistoryContent(history: HistoryUi, onEvent: (GymEvent) -> Unit) {
val colors = AwakenTheme.colors
    FlowRowChips(history.range, onEvent)
    if (history.sessions.isEmpty()) {
        Text(lexString(R.string.gym_history_none), style = AwakenTheme.type.body, color = colors.textSecondary)
    }
    history.cards.forEach { card -> TrendCard(card, history.range) }
    if (history.sessions.isNotEmpty()) {
        Text(lexString(R.string.gym_history_sessions), style = AwakenTheme.type.label, color = colors.textPrimary)
        history.sessions.forEach { session ->
            Text(
                lexString(R.string.gym_history_session_line, formatDate(session.date), sessionText(session.sets)),
                style = AwakenTheme.type.caption,
                color = colors.textSecondary,
            )
        }
    }
    ExercisePrefs(history, onEvent)
}

@Composable
private fun FlowRowChips(selected: HistoryRange, onEvent: (GymEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HistoryRange.entries.forEach { range ->
            ChoiceChip(text = rangeText(range), selected = range == selected, onClick = { onEvent(GymEvent.PickHistoryRange(range)) })
        }
    }
}

@Composable
private fun rangeText(range: HistoryRange) = lexString(
    when (range) {
        HistoryRange.MONTH -> R.string.gym_range_month
        HistoryRange.THREE_MONTHS -> R.string.gym_range_3months
        HistoryRange.YEAR -> R.string.gym_range_year
        HistoryRange.ALL -> R.string.gym_range_all
    },
)

@Composable
private fun TrendCard(card: TrendCardUi, range: HistoryRange) {
    val colors = AwakenTheme.colors
    val name = lexString(
        when (card.metric) {
            HistoryMetric.TOP_WEIGHT -> R.string.gym_metric_top
            HistoryMetric.E1RM -> R.string.gym_metric_e1rm
            HistoryMetric.VOLUME -> R.string.gym_metric_volume
        },
    )
    Text(name, style = AwakenTheme.type.label, color = colors.textPrimary)
    if (card.points.size < ExerciseHistory.MIN_POINTS) {
        Text(lexString(R.string.gym_trend_empty), style = AwakenTheme.type.caption, color = colors.textSecondary)
        return
    }
    val first = card.points.first()
    val last = card.points.last()
    val best = card.points.maxOf { it.value }
    Chart(
        ChartSpec.TrendLine(
            values = card.points.map { it.value.toFloat() },
            labels = listOf(formatDate(first.date), formatDate(last.date)),
            description = lexString(R.string.gym_trend_cd, name, formatNumber(first.value), formatNumber(last.value), rangeText(range), formatNumber(best)),
            color = colors.accent,
        ),
    )
}

/** The exercise's own rep range and step-up (GD §16.6): shows what applies and lets the hunter change or reset it. */
@Composable
private fun ExercisePrefs(history: HistoryUi, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val progression = history.progression
    Text(lexString(R.string.gym_pref_title), style = AwakenTheme.type.label, color = colors.textPrimary)
    Text(lexString(R.string.gym_pref_range), style = AwakenTheme.type.caption, color = colors.textSecondary)
    RangeSteppers(progression.range, onChange = { onEvent(GymEvent.SetExerciseRange(it)) })
    Text(lexString(R.string.gym_pref_increment), style = AwakenTheme.type.caption, color = colors.textSecondary)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        val step = ProgressionRules.INCREMENT_STEP_KG
        StepButton(lexString(R.string.pomodoro_minus), lexString(R.string.gym_pref_less), progression.incrementKg - step >= ProgressionRules.MIN_INCREMENT_KG) {
            onEvent(GymEvent.SetExerciseIncrement(progression.incrementKg - step))
        }
        Text(
            lexString(R.string.gym_pref_increment_value, formatNumber(progression.incrementKg)),
            style = AwakenTheme.type.numeric,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        StepButton(lexString(R.string.pomodoro_plus), lexString(R.string.gym_pref_more), progression.incrementKg + step <= ProgressionRules.MAX_INCREMENT_KG) {
            onEvent(GymEvent.SetExerciseIncrement(progression.incrementKg + step))
        }
    }
    if (progression.customRange || progression.customIncrement) {
        QuietButton(text = lexString(R.string.gym_pref_reset), onClick = { onEvent(GymEvent.ResetExercisePrefs) })
    }
}

/** "80 kg × 5, 5, 4 · 82.5 kg × 3": consecutive sets at one weight share a group. */
@Composable
internal fun sessionText(sets: List<LoggedSet>): String {
    val groups = mutableListOf<Pair<Double, MutableList<Int>>>()
    sets.filter { it.done && it.reps > 0 }.forEach { set ->
        val tail = groups.lastOrNull()
        if (tail != null && tail.first == set.weightKg) tail.second += set.reps else groups += set.weightKg to mutableListOf(set.reps)
    }
    val parts = mutableListOf<String>()
    for ((weight, reps) in groups) {
        parts += if (weight <= 0.0) {
            lexString(R.string.gym_history_reps_only, reps.joinToString(", "))
        } else {
            lexString(R.string.gym_history_set_group, formatNumber(weight), reps.joinToString(", "))
        }
    }
    return parts.joinToString(" · ")
}

// Previews

private val previewKg = PlateMath.defaults(PlateUnit.KG)

@Composable
private fun SheetPreview(content: @Composable () -> Unit) {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
    }
}

@PhonePreviews
@Composable
private fun PlatesExactPreview() = SheetPreview { PlatesContent(PlatesUi(100.0, PlateUnit.KG, previewKg, PlateMath.plan(100.0, previewKg)), {}) }

@PhonePreviews
@Composable
private fun PlatesShortPreview() = SheetPreview { PlatesContent(PlatesUi(61.0, PlateUnit.KG, previewKg, PlateMath.plan(61.0, previewKg)), {}) }

@PhonePreviews
@Composable
private fun PlatesBelowBarPreview() = SheetPreview { PlatesContent(PlatesUi(15.0, PlateUnit.KG, previewKg, PlateMath.plan(15.0, previewKg)), {}) }

@PhonePreviews
@Composable
private fun PlatesPoundsPreview() {
    val lb = PlateMath.defaults(PlateUnit.LB)
    SheetPreview { PlatesContent(PlatesUi(225.0, PlateUnit.LB, lb, PlateMath.plan(225.0, lb)), {}) }
}

@PhonePreviews
@Composable
private fun PlatesEmptyPreview() = SheetPreview { PlatesContent(PlatesUi(0.0, PlateUnit.KG, previewKg, null), {}) }

@PhonePreviews
@Composable
private fun GymSettingsPreview() = SheetPreview {
    GymSettingsContent(GymPrefs(kg = PlateSetup(20.0, listOf(20.0, 10.0, 5.0, 2.5, 1.25)), repRange = com.assismohanty.awaken.engine.body.RepRange(6, 10)), previewAnswers(), {})
}

private fun previewHistory(sessions: List<ExerciseSession>, customRange: Boolean = false) = HistoryUi(
    exerciseId = "e0",
    name = "Barbell Bench Press",
    range = HistoryRange.ALL,
    cards = HistoryMetric.entries.map { metric ->
        TrendCardUi(metric, ExerciseHistory.series(sessions, metric, HistoryRange.ALL, LocalDate.of(2026, 11, 2)))
    },
    sessions = sessions.sortedByDescending { it.date },
    progression = ExerciseProgression(com.assismohanty.awaken.engine.body.RepRange(8, 12), 2.5, customRange, customRange),
    defaultRange = com.assismohanty.awaken.engine.body.RepRange(8, 12),
)

private val previewSessions = listOf(
    ExerciseSession(LocalDate.of(2026, 10, 5), listOf(LoggedSet(60.0, 10), LoggedSet(60.0, 9), LoggedSet(60.0, 8))),
    ExerciseSession(LocalDate.of(2026, 10, 12), listOf(LoggedSet(62.5, 10), LoggedSet(62.5, 9), LoggedSet(62.5, 8))),
    ExerciseSession(LocalDate.of(2026, 10, 19), listOf(LoggedSet(65.0, 8), LoggedSet(65.0, 8), LoggedSet(65.0, 7))),
    ExerciseSession(LocalDate.of(2026, 10, 26), listOf(LoggedSet(65.0, 10), LoggedSet(65.0, 9), LoggedSet(67.5, 5))),
)

@PhonePreviews
@Composable
private fun HistoryManyPreview() = SheetPreview { HistoryContent(previewHistory(previewSessions, customRange = true), {}) }

@PhonePreviews
@Composable
private fun HistoryOnePreview() = SheetPreview { HistoryContent(previewHistory(previewSessions.take(1)), {}) }

@PhonePreviews
@Composable
private fun HistoryNonePreview() = SheetPreview { HistoryContent(previewHistory(emptyList()), {}) }
