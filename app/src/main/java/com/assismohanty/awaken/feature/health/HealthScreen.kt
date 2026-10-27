package com.assismohanty.awaken.feature.health

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.Segment
import com.assismohanty.awaken.core.designsystem.components.MetricTile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.GateBackground
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.HealthOverview
import com.assismohanty.awaken.core.domain.HealthTrendDay
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthSession
import com.assismohanty.awaken.engine.SleepStage
import com.assismohanty.awaken.engine.SleepStageType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.feature.settings.HealthActionEffect
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle


@Composable
fun HealthRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HealthActionEffect(
        action = state.action,
        permissions = state.permissions,
        onPermissionsResult = { viewModel.onEvent(HealthEvent.PermissionsResult) },
        onHandled = { viewModel.onEvent(HealthEvent.ActionHandled) },
    )
    HealthScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** Health (steps, distance, calories, heart rate, workouts, sleep, 7-day trend, sources). Display only: no rewards here. */
@Composable
fun HealthScreen(
    state: HealthUiState,
    onEvent: (HealthEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = AwakenTheme.colors
    GateBackground(rank = Rank.E, modifier = modifier) {
        LargeTitleScaffold(title = lexString(R.string.health_title), onBack = onBack) {
            val overview = state.overview
            when {
                overview != null -> {
                    OverviewContent(overview, state, onEvent, zone)
                }
                else -> MessageWindow(state.status, onEvent)
            }
        }
    }
}

@Composable
private fun MessageWindow(status: HealthScreenStatus, onEvent: (HealthEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val (text, retry) = when (status) {
        HealthScreenStatus.LOADING -> R.string.health_loading to false
        HealthScreenStatus.DISABLED -> R.string.health_state_disabled to false
        HealthScreenStatus.UNAVAILABLE -> R.string.health_state_unavailable to false
        HealthScreenStatus.READ_FAILED -> R.string.health_state_read_failed to true
        HealthScreenStatus.READY -> R.string.health_loading to false
    }
    SystemWindow(title = null, accent = colors.positive, reveal = false) {
        Text(lexString(text), style = AwakenTheme.type.body, color = colors.textSecondary)
        if (retry) {
            SecondaryButton(
                text = lexString(R.string.health_action_refresh),
                onClick = { onEvent(HealthEvent.Refresh) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OverviewContent(overview: HealthOverview, state: HealthUiState, onEvent: (HealthEvent) -> Unit, zone: ZoneId) {
    val colors = AwakenTheme.colors
    TodayWindow(overview, state, onEvent, zone)
    WorkoutsWindow(overview.today.exercise, zone)
    SleepWindow(overview.today.sleep.lastOrNull(), zone)
    TrendWindow(overview)
    SourcesWindow(overview.today)
    if (!overview.canDistance || !overview.canCalories || !overview.canHeartRate) {
        SystemWindow(title = null, accent = colors.positive, reveal = false) {
            Text(lexString(R.string.health_allow_more_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
            PrimaryButton(
                text = lexString(R.string.health_allow_more),
                onClick = { onEvent(HealthEvent.AllowMoreMetrics) },
                color = colors.positive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TodayWindow(overview: HealthOverview, state: HealthUiState, onEvent: (HealthEvent) -> Unit, zone: ZoneId) {
    val colors = AwakenTheme.colors
    val today = overview.today
    val heartRate = today.avgHeartRate
    val numbers = NumberFormat.getIntegerInstance()
    val oneDecimal = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1; minimumFractionDigits = 1 } }
    SystemWindow(
        title = lexString(R.string.health_window_today),
        accent = colors.positive,
        reveal = false,
        headerTrailing = {
            QuietButton(
                text = lexString(R.string.health_action_refresh),
                onClick = { onEvent(HealthEvent.Refresh) },
                enabled = !state.refreshing,
            )
        },
    ) {
        Text(
            lexString(R.string.health_updated, formatTime(overview.updatedAt, zone)),
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
        )
        val notAllowed = lexString(R.string.health_not_allowed)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            MetricTile(lexString(R.string.health_metric_steps), numbers.format(today.steps), Modifier.weight(1f))
            MetricTile(
                label = lexString(R.string.health_metric_distance),
                value = if (overview.canDistance) lexString(R.string.health_value_distance, oneDecimal.format(today.distanceMeters / METERS_PER_KM)) else notAllowed,
                modifier = Modifier.weight(1f),
                dim = !overview.canDistance,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                label = lexString(R.string.health_metric_calories),
                value = if (overview.canCalories) lexString(R.string.health_value_kcal, numbers.format(today.activeKcal.toLong())) else notAllowed,
                modifier = Modifier.weight(1f),
                dim = !overview.canCalories,
            )
            MetricTile(
                label = lexString(R.string.health_metric_heart_rate),
                value = when {
                    !overview.canHeartRate -> notAllowed
                    heartRate == null -> lexString(R.string.health_no_data)
                    else -> lexString(R.string.health_value_bpm, heartRate)
                },
                modifier = Modifier.weight(1f),
                dim = !overview.canHeartRate || heartRate == null,
            )
        }
    }
}

@Composable
private fun WorkoutsWindow(sessions: List<HealthSession>, zone: ZoneId) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.health_window_workouts), accent = colors.positive, reveal = false) {
        if (sessions.isEmpty()) {
            Text(lexString(R.string.health_no_workouts), style = type.body, color = colors.textSecondary)
        }
        sessions.forEach { session ->
            val name = prettyWorkoutLabel(session.label) ?: lexString(R.string.health_workout_default)
            Text(
                lexString(R.string.health_workout_title, name, formatDuration(session.duration)),
                style = type.label,
                color = colors.textPrimary,
            )
            Text(
                lexString(R.string.health_workout_window, formatTime(session.start, zone), formatTime(session.end, zone)),
                style = type.caption,
                color = colors.textSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                session.distanceMeters?.let {
                    Pill(text = lexString(R.string.health_value_distance, NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(it / METERS_PER_KM)), color = colors.textSecondary)
                }
                session.activeKcal?.let {
                    Pill(text = lexString(R.string.health_value_kcal, NumberFormat.getIntegerInstance().format(it.toLong())), color = colors.textSecondary)
                }
                session.avgHeartRate?.let { Pill(text = lexString(R.string.health_value_bpm, it), color = colors.critical) }
                session.origin?.let { Pill(text = healthSourceName(it), color = colors.positive) }
            }
        }
    }
}

@Composable
private fun SleepWindow(session: HealthSession?, zone: ZoneId) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = lexString(R.string.health_window_sleep), accent = colors.chartSeries3, reveal = false) {
        if (session == null) {
            Text(lexString(R.string.health_no_sleep), style = type.body, color = colors.textSecondary)
            return@SystemWindow
        }
        Text(lexString(R.string.health_sleep_total, formatDuration(session.duration)), style = type.numericSmall, color = colors.textPrimary)
        Text(
            lexString(R.string.health_workout_window, formatTime(session.start, zone), formatTime(session.end, zone)),
            style = type.caption,
            color = colors.textSecondary,
        )
        if (session.stages.isEmpty()) {
            Text(lexString(R.string.health_sleep_no_stages), style = type.caption, color = colors.textSecondary)
        } else {
            StageTimeline(session)
        }
        session.origin?.let { Pill(text = healthSourceName(it), color = colors.positive) }
    }
}

@Composable
private fun StageTimeline(session: HealthSession) {
    val colors = AwakenTheme.colors
    val totalMillis = session.duration.toMillis().coerceAtLeast(1L).toFloat()
    val totals = SleepStageType.entries.associateWith { stage ->
        Duration.ofMillis(session.stages.filter { it.type == stage }.sumOf { Duration.between(it.start, it.end).toMillis() })
    }.filterValues { !it.isZero }
    // The bar is a picture of the same numbers listed below it, so a screen reader skips it and reads the list once.
    val segments = session.stages.sortedBy(SleepStage::start).map { segment ->
        Segment(
            weight = (Duration.between(segment.start, segment.end).toMillis() / totalMillis).coerceAtLeast(MIN_SEGMENT_WEIGHT),
            color = stageColor(segment.type, colors.critical, colors.textSecondary, colors.chartSeries2, colors.chartSeries3, colors.textMuted),
        )
    }
    Chart(ChartSpec.Segmented(segments = segments, shape = null, height = AwakenTheme.dimens.stageBarHeight))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        totals.forEach { (stage, duration) ->
            Text(
                lexString(R.string.health_stage_line, stageName(stage), formatDuration(duration)),
                style = AwakenTheme.type.caption,
                color = stageColor(stage, colors.critical, colors.textSecondary, colors.chartSeries2, colors.chartSeries3, colors.textMuted),
            )
        }
    }
}

private fun stageColor(stage: SleepStageType, awake: Color, light: Color, deep: Color, rem: Color, unknown: Color): Color = when (stage) {
    SleepStageType.AWAKE -> awake
    SleepStageType.LIGHT -> light
    SleepStageType.DEEP -> deep
    SleepStageType.REM -> rem
    SleepStageType.UNKNOWN -> unknown
}

@Composable
private fun stageName(stage: SleepStageType): String = lexString(
    when (stage) {
        SleepStageType.AWAKE -> R.string.health_stage_awake
        SleepStageType.LIGHT -> R.string.health_stage_light
        SleepStageType.DEEP -> R.string.health_stage_deep
        SleepStageType.REM -> R.string.health_stage_rem
        SleepStageType.UNKNOWN -> R.string.health_stage_unknown
    },
)

@Composable
private fun TrendWindow(overview: HealthOverview) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.health_window_trend), accent = colors.positive, reveal = false) {
        TrendChart(
            title = lexString(R.string.health_metric_steps),
            days = overview.trend,
            value = { it.steps.toDouble() },
            color = colors.chartSeries4,
        )
        if (overview.canDistance) {
            TrendChart(
                title = lexString(R.string.health_metric_distance),
                days = overview.trend,
                value = { it.distanceMeters / METERS_PER_KM },
                color = colors.chartSeries2,
            )
        }
        if (overview.canCalories) {
            TrendChart(
                title = lexString(R.string.health_metric_calories),
                days = overview.trend,
                value = { it.activeKcal },
                color = colors.chartSeries1,
            )
        }
    }
}

@Composable
private fun TrendChart(title: String, days: List<HealthTrendDay>, value: (HealthTrendDay) -> Double, color: Color) {
    val colors = AwakenTheme.colors
    val values = days.map(value)
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val numbers = NumberFormat.getIntegerInstance()
    val description = lexString(R.string.cd_trend_chart, title, values.joinToString(", ") { numbers.format(it.toLong()) })
    Text(title, style = AwakenTheme.type.label, color = colors.textPrimary)
    Chart(
        ChartSpec.Columns(
            fractions = values.map { (it / max).toFloat().coerceAtLeast(MIN_BAR_FRACTION) },
            labels = days.map { it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.current.platformLocale) },
            color = color,
            description = description,
        ),
    )
}

@Composable
private fun SourcesWindow(today: HealthDay) {
    val colors = AwakenTheme.colors
    val origins = (today.exercise + today.sleep).mapNotNull { it.origin }.distinct()
    SystemWindow(title = lexString(R.string.health_window_sources), accent = colors.positive, reveal = false) {
        if (origins.isEmpty()) {
            Text(lexString(R.string.health_sources_none), style = AwakenTheme.type.body, color = colors.textSecondary)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                origins.forEach { Pill(text = healthSourceName(it), color = colors.positive) }
            }
        }
        Text(lexString(R.string.health_sources_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
    }
}

private fun formatTime(instant: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone).format(instant)

@Composable
private fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    return if (minutes >= MINUTES_PER_HOUR) {
        lexString(R.string.health_duration_hm, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    } else {
        lexString(R.string.health_duration_min, minutes)
    }
}

private const val METERS_PER_KM = 1_000.0
private const val MINUTES_PER_HOUR = 60L
private const val MIN_SEGMENT_WEIGHT = 0.004f
private const val MIN_BAR_FRACTION = 0.04f

// region previews

private fun previewOverview(allowed: Boolean = true): HealthOverview {
    val start = Instant.parse("2026-10-18T05:30:00Z")
    val sleepStart = Instant.parse("2026-10-17T22:30:00Z")
    fun stage(minutesFrom: Long, minutes: Long, type: SleepStageType) =
        SleepStage(sleepStart.plusSeconds(minutesFrom * 60), sleepStart.plusSeconds((minutesFrom + minutes) * 60), type)
    val day = HealthDay(
        steps = 8_412,
        distanceMeters = if (allowed) 6_240.0 else 0.0,
        activeKcal = if (allowed) 412.0 else 0.0,
        avgHeartRate = if (allowed) 118 else null,
        exercise = listOf(
            HealthSession(
                start, start.plusSeconds(38 * 60), origin = "com.strava", label = "running",
                distanceMeters = if (allowed) 5_100.0 else null, activeKcal = if (allowed) 366.0 else null, avgHeartRate = if (allowed) 152 else null,
            ),
        ),
        sleep = listOf(
            HealthSession(
                sleepStart, sleepStart.plusSeconds(450 * 60), origin = "com.fitbit.FitbitMobile",
                stages = listOf(
                    stage(0, 20, SleepStageType.LIGHT), stage(20, 90, SleepStageType.DEEP), stage(110, 150, SleepStageType.LIGHT),
                    stage(260, 30, SleepStageType.REM), stage(290, 10, SleepStageType.AWAKE), stage(300, 150, SleepStageType.LIGHT),
                ),
            ),
        ),
    )
    val date = LocalDate.parse("2026-10-18")
    return HealthOverview(
        today = day,
        trend = (6 downTo 0).map { back ->
            HealthTrendDay(date.minusDays(back.toLong()), 4_000L + back * 900L, if (allowed) 3_000.0 + back * 400 else 0.0, if (allowed) 250.0 + back * 30 else 0.0)
        },
        updatedAt = Instant.parse("2026-10-18T10:00:00Z"),
        canDistance = allowed,
        canCalories = allowed,
        canHeartRate = allowed,
    )
}

@PhonePreviews
@Composable
private fun HealthScreenPreview() {
    AwakenTheme(reduceMotion = true) {
        HealthScreen(
            state = HealthUiState(status = HealthScreenStatus.READY, overview = previewOverview()),
            onEvent = {}, onBack = {}, zone = ZoneId.of("UTC"),
        )
    }
}

@PhonePreviews
@Composable
private fun HealthScreenCoreOnlyPreview() {
    AwakenTheme(reduceMotion = true) {
        HealthScreen(
            state = HealthUiState(status = HealthScreenStatus.READY, overview = previewOverview(allowed = false)),
            onEvent = {}, onBack = {}, zone = ZoneId.of("UTC"),
        )
    }
}

@PhonePreviews
@Composable
private fun HealthScreenDisabledPreview() {
    AwakenTheme(reduceMotion = true) {
        HealthScreen(state = HealthUiState(status = HealthScreenStatus.DISABLED), onEvent = {}, onBack = {}, zone = ZoneId.of("UTC"))
    }
}

// endregion
