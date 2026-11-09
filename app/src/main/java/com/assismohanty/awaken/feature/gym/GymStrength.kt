package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.components.formatNumber
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.PersonalRecord
import com.assismohanty.awaken.engine.body.Benchmarks
import com.assismohanty.awaken.engine.body.LoggedSet
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.Standing
import com.assismohanty.awaken.engine.body.StrengthLevel
import java.time.LocalDate
import kotlin.math.roundToInt

/** The strength tracker (GD §18.3): best lifts by muscle, each row opening that exercise's history. */
@Composable
internal fun StrengthSheet(strength: StrengthUi, onEvent: (GymEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(GymEvent.CloseStrength) }, title = lexString(R.string.gym_strength_title)) { StrengthContent(strength, onEvent) }
}

@Composable
internal fun StrengthContent(strength: StrengthUi, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    if (strength.groups.isEmpty()) {
        Text(lexString(R.string.gym_strength_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        return
    }
    strength.groups.forEach { (muscle, rows) ->
        Text(
            muscle?.let { lexString(it.nameRes()) } ?: lexString(R.string.gym_strength_group_other),
            style = AwakenTheme.type.label,
            color = colors.textPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            rows.forEach { row ->
                val record = row.record
                val line = lexString(R.string.gym_strength_row, formatNumber(record.weightKg), record.reps, formatDate(record.gameDate))
                ListRow(
                    title = record.name,
                    secondary = row.level?.let { "$line · ${levelText(it)}" } ?: line,
                    onClick = { onEvent(GymEvent.OpenHistory(record.exerciseId, record.name)) },
                )
            }
        }
    }
}

/** The best result and "Where do I stand?" at the top of an exercise's history (GD §18.3). */
@Composable
internal fun StandingSection(ui: StandingUi, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Text(lexString(R.string.gym_best_title), style = AwakenTheme.type.label, color = colors.textPrimary)
    ui.bestSet?.let { set ->
        Text(
            if (set.weightKg > 0.0) lexString(R.string.gym_best_set, formatNumber(set.weightKg), set.reps) else lexString(R.string.gym_best_reps, set.reps),
            style = AwakenTheme.type.body,
            color = colors.textSecondary,
        )
    }
    ui.bestE1rmKg?.let { Text(lexString(R.string.gym_best_e1rm, formatNumber(it)), style = AwakenTheme.type.caption, color = colors.textSecondary) }
    val standing = ui.standing ?: return
    Text(lexString(R.string.gym_stand_title), style = AwakenTheme.type.label, color = colors.textPrimary)
    when (standing) {
        Standing.NeedsProfile -> {
            Text(lexString(R.string.gym_stand_needs_profile), style = AwakenTheme.type.body, color = colors.textSecondary)
            QuietButton(text = lexString(R.string.gym_stand_needs_profile_action), onClick = { onEvent(GymEvent.OpenGymSettings) })
        }
        is Standing.Standard -> {
            LevelGauge(standing.level, standing.gauge)
            Text(lexString(R.string.gym_stand_standard_caption), style = AwakenTheme.type.caption, color = colors.textSecondary)
            if (standing.ageNotSet) Text(lexString(R.string.gym_stand_age_not_set), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
        is Standing.Percentile -> {
            LevelGauge(standing.level, standing.gauge)
            val pointerKg = ui.pointerKg ?: 0.0
            val pointerPercentile = (ui.pointerPercentile ?: standing.percentile).roundToInt()
            Text(lexString(R.string.gym_stand_pointer, formatNumber(pointerKg.roundTo1()), pointerPercentile), style = AwakenTheme.type.numericSmall, color = colors.textPrimary)
            Chart(
                ChartSpec.DistributionCurve(
                    density = standing.curve.map { it.second.toFloat() },
                    marker = ui.marker,
                    selected = ui.pointer,
                    selectedText = lexString(R.string.gym_stand_pointer_value, formatNumber(pointerKg.roundTo1()), pointerPercentile),
                    description = lexString(R.string.gym_stand_curve_cd, standing.percentile.roundToInt()),
                    onSelect = { onEvent(GymEvent.MovePointer(it)) },
                ),
            )
            Text(lexString(R.string.gym_stand_percentile_caption, standing.population), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun LevelGauge(level: StrengthLevel, position: Float) {
    val labels = StrengthLevel.entries.map { levelText(it) }
    Chart(
        ChartSpec.LevelGauge(
            position = position,
            labels = labels,
            description = lexString(R.string.gym_stand_gauge_cd, levelText(level), labels.first(), labels.last()),
        ),
    )
}

@Composable
internal fun levelText(level: StrengthLevel): String = lexString(
    when (level) {
        StrengthLevel.BEGINNER -> R.string.gym_standing_level_beginner
        StrengthLevel.NOVICE -> R.string.gym_standing_level_novice
        StrengthLevel.INTERMEDIATE -> R.string.gym_standing_level_intermediate
        StrengthLevel.ADVANCED -> R.string.gym_standing_level_advanced
        StrengthLevel.ELITE -> R.string.gym_standing_level_elite
    },
)

// Previews

private val previewQuantiles = List(99) { 0.4 + it * 0.02 }

private fun previewPercentile(percentile: Double): Standing.Percentile {
    val ratio = Benchmarks.ratioAt(percentile, previewQuantiles)
    return Standing.Percentile(
        level = Benchmarks.level(percentile),
        percentile = percentile,
        ratio = ratio,
        quantiles = previewQuantiles,
        curve = Benchmarks.curve(previewQuantiles),
        population = "people who compete in raw powerlifting meets, ages 18 to 59",
        bodyWeightKg = 80.0,
    )
}

private fun previewUi(standing: Standing?, e1rm: Double? = 105.0, pointer: Float = 0.5f) = StandingUi(
    bestSet = LoggedSet(weightKg = 90.0, reps = 3),
    bestE1rmKg = e1rm,
    standing = standing,
    pointer = pointer,
    marker = 0.5f,
    pointerKg = 112.0,
    pointerPercentile = 62.0,
)

@Composable
private fun StandingPreview(ui: StandingUi) {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { StandingSection(ui, {}) }
    }
}

@PhonePreviews
@Composable
private fun StandingPercentilePreview() = StandingPreview(previewUi(previewPercentile(62.0)))

@PhonePreviews
@Composable
private fun StandingStandardPreview() = StandingPreview(previewUi(Standing.Standard(StrengthLevel.INTERMEDIATE, 72, ageNotSet = true), e1rm = null))

@PhonePreviews
@Composable
private fun StandingNeedsProfilePreview() = StandingPreview(previewUi(Standing.NeedsProfile))

@PhonePreviews
@Composable
private fun StandingNoBenchmarkPreview() = StandingPreview(previewUi(null))

private val previewRecord = { id: String, name: String, kg: Double, reps: Int, e1rm: Double ->
    PersonalRecord(id, name, kg, reps, e1rm, LocalDate.of(2026, 11, 3))
}

@PhonePreviews
@Composable
private fun StrengthTrackerPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StrengthContent(
                StrengthUi(
                    listOf(
                        Muscle.CHEST to listOf(StrengthRow(previewRecord("bench", "Bench press", 90.0, 3, 99.0), StrengthLevel.INTERMEDIATE)),
                        Muscle.QUADRICEPS to listOf(StrengthRow(previewRecord("squat", "Back squat", 120.0, 5, 140.0), StrengthLevel.NOVICE), StrengthRow(previewRecord("lunge", "Lunge", 40.0, 8, 50.0), null)),
                        null to listOf(StrengthRow(previewRecord("x", "Farmer's walk", 60.0, 1, 60.0), null)),
                    ),
                ),
                {},
            )
        }
    }
}

@PhonePreviews
@Composable
private fun StrengthTrackerEmptyPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md)) { StrengthContent(StrengthUi(emptyList()), {}) }
    }
}
