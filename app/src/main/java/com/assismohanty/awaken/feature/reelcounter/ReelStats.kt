package com.assismohanty.awaken.feature.reelcounter

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.StackedColumn
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SectionTitle
import com.assismohanty.awaken.core.designsystem.components.SegmentedControl
import com.assismohanty.awaken.core.designsystem.components.ValueRow
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.stats.StatsCalculator
import com.assismohanty.awaken.reelcounter.core.time.GameDay
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.ReelDayStart
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class ReelRange(val days: Int) { WEEK(7), MONTH(30) }

data class ReelAppRowUi(val app: ShortVideoApp, val count: Int, val minutes: Long)

/** One day's column: [total] reels, on [day]. */
data class ReelBarUi(val day: LocalDate, val total: Int, val isToday: Boolean)

data class ReelStatsUiState(
    val range: ReelRange = ReelRange.WEEK,
    val todayTotal: Int = 0,
    val todayMinutes: Long = 0,
    val rows: List<ReelAppRowUi> = emptyList(),
    val bars: List<ReelBarUi> = emptyList(),
    val average: Int = 0,
    /** Days in a row within every limit; null when no limits are set. */
    val streak: Int? = null,
    val hasData: Boolean = false,
)

sealed interface ReelStatsEvent {
    data class SetRange(val range: ReelRange) : ReelStatsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReelStatsViewModel @Inject constructor(
    repository: ReelRepository,
    settings: SettingsStore,
    clock: ReelClock,
    dayStart: ReelDayStart,
) : ViewModel() {
    private val range = MutableStateFlow(ReelRange.WEEK)

    private val today: Flow<LocalDate> = dayStart.dayStartHour.flatMapLatest { hour ->
        val gameDay = GameDay(clock.zone(), LocalTime.of(hour.coerceIn(0, 23), 0))
        flow {
            while (true) {
                emit(gameDay.dayOf(clock.now()))
                delay(TICK_MILLIS)
            }
        }
    }.distinctUntilChanged()

    private val history = today.flatMapLatest { day -> repository.observeRange(day.minusDays(HISTORY_DAYS), day) }

    val uiState: StateFlow<ReelStatsUiState> =
        combine(today, history, repository.observeFirstDay(), settings.limits, range) { day, totals, firstDay, limits, selected ->
            val series = StatsCalculator.series(totals, day, selected.days)
            val todayRows = StatsCalculator.totalsForDay(totals, day)
            ReelStatsUiState(
                range = selected,
                todayTotal = todayRows.values.sumOf { it.count },
                todayMinutes = StatsCalculator.minutes(todayRows.values.sumOf { it.seconds }),
                rows = ShortVideoApp.entries.map { app ->
                    ReelAppRowUi(app, todayRows[app]?.count ?: 0, StatsCalculator.minutes(todayRows[app]?.seconds ?: 0))
                },
                bars = series.map { ReelBarUi(it.day, it.total, it.day == day) },
                average = StatsCalculator.dailyAverage(series, firstDay),
                streak = StatsCalculator.withinLimitStreak(totals, limits, day, firstDay),
                hasData = totals.isNotEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReelStatsUiState())

    fun onEvent(event: ReelStatsEvent) {
        when (event) {
            is ReelStatsEvent.SetRange -> range.update { event.range }
        }
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val HISTORY_DAYS = 365L
    }
}

@Composable
fun ReelStatsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReelStatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReelStatsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
fun ReelStatsScreen(state: ReelStatsUiState, onEvent: (ReelStatsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    LargeTitleScaffold(title = lexString(R.string.reel_stats_title), onBack = onBack, modifier = modifier) {
        BigValue(value = state.todayTotal.toString(), unit = lexPlural(R.plurals.reel_stats_today, state.todayTotal), large = true)
        if (state.todayMinutes > 0) {
            Text(lexString(R.string.reel_stats_minutes, state.todayMinutes), style = type.body, color = colors.textSecondary)
        }
        if (!state.hasData) {
            Text(lexString(R.string.reel_stats_empty), style = type.body, color = colors.textSecondary)
        }
        SectionTitle(lexString(R.string.reel_stats_by_app))
        AwakenCard(reveal = false) {
            state.rows.forEach {
                ValueRow(label = lexString(it.app.labelRes()), value = lexString(R.string.reel_stats_row, it.count, it.minutes))
            }
        }
        AwakenCard(reveal = false) {
            SegmentedControl(
                options = listOf(lexString(R.string.reel_stats_range_7), lexString(R.string.reel_stats_range_30)),
                selected = ReelRange.entries.indexOf(state.range),
                onSelect = { onEvent(ReelStatsEvent.SetRange(ReelRange.entries[it])) },
            )
            val peak = state.bars.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
            Chart(
                ChartSpec.StackedColumns(
                    columns = state.bars.map {
                        val fraction = it.total.toFloat() / peak
                        StackedColumn(
                            total = fraction,
                            highlight = if (it.isToday) fraction else 0f,
                            description = lexString(R.string.reel_cd_day, it.day.toString(), it.total),
                        )
                    },
                    base = colors.accentMuted,
                    highlight = colors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(lexString(R.string.reel_stats_average, state.average), style = type.caption, color = colors.textSecondary)
        }
        AwakenCard(reveal = false) {
            Text(
                state.streak?.let { lexPlural(R.plurals.reel_stats_streak, it, it) } ?: lexString(R.string.reel_stats_streak_none),
                style = type.label,
                color = colors.textPrimary,
            )
        }
    }
}

private val PREVIEW_DAY: LocalDate = LocalDate.of(2026, 11, 1)

private fun previewState(range: ReelRange, days: Int) = ReelStatsUiState(
    range = range,
    todayTotal = 38,
    todayMinutes = 52,
    rows = listOf(
        ReelAppRowUi(ShortVideoApp.INSTAGRAM, 23, 31),
        ReelAppRowUi(ShortVideoApp.YOUTUBE, 15, 21),
        ReelAppRowUi(ShortVideoApp.TIKTOK, 0, 0),
        ReelAppRowUi(ShortVideoApp.FACEBOOK, 0, 0),
        ReelAppRowUi(ShortVideoApp.SNAPCHAT, 0, 0),
    ),
    bars = (days - 1 downTo 0).map { ReelBarUi(PREVIEW_DAY.minusDays(it.toLong()), 20 + (it * 7) % 40, isToday = it == 0) },
    average = 41,
    streak = 5,
    hasData = true,
)

@PhonePreviews
@Composable
private fun ReelStatsWeekPreview() {
    AwakenTheme(reduceMotion = true) { ReelStatsScreen(previewState(ReelRange.WEEK, 7), {}, {}) }
}

@PhonePreviews
@Composable
private fun ReelStatsMonthPreview() {
    AwakenTheme(reduceMotion = true) { ReelStatsScreen(previewState(ReelRange.MONTH, 30).copy(streak = null), {}, {}) }
}

@PhonePreviews
@Composable
private fun ReelStatsEmptyPreview() {
    AwakenTheme(reduceMotion = true) { ReelStatsScreen(ReelStatsUiState(), {}, {}) }
}
