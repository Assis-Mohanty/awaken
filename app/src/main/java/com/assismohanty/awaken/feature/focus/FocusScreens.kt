package com.assismohanty.awaken.feature.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.ComparisonRow
import com.assismohanty.awaken.core.designsystem.charts.StackedColumn
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.formatMinutes
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun FocusReportRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: FocusReportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FocusReportScreen(state = state, onBack = onBack, modifier = modifier)
}

/** Weekly focus report: minutes per day (columns) and per subject (bars) over the last 7 game-days. */
@Composable
fun FocusReportScreen(state: FocusReportUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    LargeTitleScaffold(title = lexString(R.string.focus_report_title), onBack = onBack, modifier = modifier) {
        when {
            state.loading -> SkeletonCard()
            state.totalMinutes == 0 -> EmptyState(line = lexString(R.string.focus_report_empty), actionLabel = null, onAction = {})
            else -> {
                AwakenCard(title = lexString(R.string.focus_report_days), reveal = false) {
                    val max = state.days.maxOf { it.minutes }.coerceAtLeast(1)
                    Chart(
                        ChartSpec.StackedColumns(
                            columns = state.days.map { day ->
                                StackedColumn(
                                    total = day.minutes.toFloat() / max,
                                    highlight = 0f,
                                    description = lexString(R.string.focus_report_day_cd, dayName(day.date), formatMinutes(day.minutes)),
                                )
                            },
                            base = colors.divider,
                            highlight = colors.accent,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                        state.days.forEach { day ->
                            Text(dayName(day.date), style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
                        }
                    }
                    Text(
                        lexString(R.string.focus_report_total, formatMinutes(state.totalMinutes)),
                        style = AwakenTheme.type.label,
                        color = colors.textPrimary,
                    )
                }
                AwakenCard(title = lexString(R.string.focus_report_subjects), reveal = false) {
                    val top = state.subjects.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                    Chart(
                        ChartSpec.Comparison(
                            rows = state.subjects.map { subject ->
                                ComparisonRow(
                                    label = subject.name ?: lexString(R.string.focus_report_no_subject),
                                    value = formatMinutes(subject.minutes),
                                    fraction = subject.minutes.toFloat() / top,
                                    color = colors.accent,
                                )
                            },
                            description = lexString(R.string.focus_report_subjects_cd),
                        ),
                    )
                }
            }
        }
    }
}

private fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

private val previewDays = (0L until 7L).map { FocusDayUi(LocalDate.of(2026, 11, 2).plusDays(it), listOf(50, 0, 95, 25, 140, 0, 60)[it.toInt()]) }

@PhonePreviews
@Composable
private fun FocusReportPreview() {
    AwakenTheme {
        FocusReportScreen(
            FocusReportUiState(
                loading = false,
                days = previewDays,
                subjects = listOf(FocusSubjectUi("DSA", 190), FocusSubjectUi("Reading", 130), FocusSubjectUi(null, 50)),
            ),
            onBack = {},
        )
    }
}

@PhonePreviews
@Composable
private fun FocusReportEmptyPreview() {
    AwakenTheme { FocusReportScreen(FocusReportUiState(loading = false, days = previewDays.map { it.copy(minutes = 0) }), onBack = {}) }
}
