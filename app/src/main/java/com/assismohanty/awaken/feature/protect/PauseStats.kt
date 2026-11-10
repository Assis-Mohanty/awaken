package com.assismohanty.awaken.feature.protect

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
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PauseDao
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.ComparisonRow
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.PauseOutcome
import com.assismohanty.awaken.engine.PauseRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PauseStatsRow(val label: String, val pauses: Int, val notNow: Int)

data class PauseStatsUiState(
    val loading: Boolean = true,
    val rows: List<PauseStatsRow> = emptyList(),
) {
    val totalPauses: Int get() = rows.sumOf { it.pauses }
    val totalNotNow: Int get() = rows.sumOf { it.notNow }
}

@HiltViewModel
class PauseStatsViewModel @Inject constructor(
    dao: PauseDao,
    hunterDao: HunterDao,
    clock: Clock,
    private val usage: UsageGateway,
) : ViewModel() {
    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState: StateFlow<PauseStatsUiState> = flow {
        emit(hunterDao.getProfile()?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR)
    }.flatMapLatest { startHour ->
        val since = clock.gameDate(startHour).minusDays((PauseRules.STATS_DAYS - 1).toLong())
        combine(dao.observeSince(since), labels) { events, names ->
            val rows = events.groupBy { it.packageName }.map { (pkg, list) ->
                PauseStatsRow(names[pkg] ?: pkg, list.size, list.count { it.outcome == PauseOutcome.NOT_NOW })
            }.sortedByDescending { it.pauses }
            PauseStatsUiState(loading = false, rows = rows)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PauseStatsUiState())

    init {
        viewModelScope.launch { labels.value = usage.launcherApps().associate { it.packageName to it.label } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun PauseStatsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PauseStatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PauseStatsScreen(state, onBack, modifier)
}

/** Pause, last 7 days (GD §19.1 PZ9): pauses and Not now per app. Display only; nothing here earns or costs anything. */
@Composable
fun PauseStatsScreen(state: PauseStatsUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    ScreenBackground(modifier = modifier) {
        LargeTitleScaffold(title = lexString(R.string.pause_stats_title), subtitle = lexString(R.string.pause_stats_subtitle), onBack = onBack) {
            if (!state.loading && state.rows.isEmpty()) {
                Text(lexString(R.string.pause_stats_empty), style = AwakenTheme.type.caption, color = colors.textSecondary)
            } else if (state.rows.isNotEmpty()) {
                SystemWindow(title = null, accent = colors.accent, reveal = false) {
                    Text(
                        lexString(R.string.pause_stats_total, state.totalPauses, state.totalNotNow),
                        style = AwakenTheme.type.label,
                        color = colors.textPrimary,
                    )
                    val widest = state.rows.maxOf { it.pauses }.coerceAtLeast(1)
                    Chart(
                        ChartSpec.Comparison(
                            rows = state.rows.map { ComparisonRow(it.label, lexString(R.string.pause_stats_value, it.pauses), it.pauses / widest.toFloat()) },
                            description = lexString(R.string.pause_stats_cd),
                        ),
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@PhonePreviews
@Composable
private fun PauseStatsPreview() {
    AwakenTheme(reduceMotion = true) {
        PauseStatsScreen(
            PauseStatsUiState(
                loading = false,
                rows = listOf(PauseStatsRow("Instagram", 14, 6), PauseStatsRow("YouTube", 9, 2), PauseStatsRow("Reddit", 3, 0)),
            ),
            {},
        )
    }
}

@PhonePreviews
@Composable
private fun PauseStatsEmptyPreview() {
    AwakenTheme(reduceMotion = true) { PauseStatsScreen(PauseStatsUiState(loading = false), {}) }
}
