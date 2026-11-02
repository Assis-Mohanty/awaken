package com.assismohanty.awaken.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.reelcounter.ReelWidgetData
import com.assismohanty.awaken.reelcounter.core.lumi.LumiState
import com.assismohanty.awaken.core.designsystem.components.HealthTileModel
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTileModel
import com.assismohanty.awaken.core.domain.HealthOverviewResult
import com.assismohanty.awaken.core.domain.LoadHealthOverviewUseCase
import com.assismohanty.awaken.core.domain.LoadScreenTimeSummaryUseCase
import com.assismohanty.awaken.core.domain.ScreenTimeSummary
import com.assismohanty.awaken.core.domain.StepSource
import com.assismohanty.awaken.engine.AppCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** The two summary tiles above Today's quests (phone feedback Part 2). */
data class TodaySummaryUiState(
    val screenTime: ScreenTimeTileModel = ScreenTimeTileModel.Loading,
    val health: HealthTileModel = HealthTileModel.Loading,
    /** Health isn't connected at all: "Set up a source" goes to Settings, not to Health Connect. */
    val healthNeedsConnect: Boolean = false,
    /** Reels watched today (Reel counter); null while that feature is switched off, so no tile shows. */
    val reelsToday: Int? = null,
    /** Lumi for [reelsToday] (every app, against the combined daily limit). */
    val reelsLumi: LumiState = LumiState.Rested,
)

/** Reads both tiles on every resume (usage and Health Connect change outside the app). Display only. */
@HiltViewModel
class TodaySummaryViewModel @Inject constructor(
    private val loadScreenTime: LoadScreenTimeSummaryUseCase,
    private val loadHealth: LoadHealthOverviewUseCase,
    reelData: ReelWidgetData,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodaySummaryUiState())
    val uiState: StateFlow<TodaySummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { reelData.observeLumi().collect { lumi ->
                _uiState.update { it.copy(reelsToday = lumi?.total, reelsLumi = lumi?.state ?: LumiState.Rested) }
            } }
    }

    fun refresh() {
        viewModelScope.launch {
            val screenTime = guarded("screen time") { loadScreenTime() }?.toTile() ?: ScreenTimeTileModel.NotTracked
            _uiState.update { it.copy(screenTime = screenTime) }
        }
        viewModelScope.launch {
            val result = guarded("health") { loadHealth(days = 1) }
            _uiState.update {
                it.copy(
                    health = result?.toTile() ?: HealthTileModel.NoData,
                    healthNeedsConnect = result == HealthOverviewResult.Disabled || result == HealthOverviewResult.Unavailable,
                )
            }
        }
    }

    private suspend fun <T> guarded(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Today tile read failed: %s", what)
        null
    }
}

internal fun ScreenTimeSummary.toTile(): ScreenTimeTileModel = when (this) {
    ScreenTimeSummary.NoAccess -> ScreenTimeTileModel.NoAccess
    ScreenTimeSummary.NotTracked -> ScreenTimeTileModel.NotTracked
    is ScreenTimeSummary.Tracked -> ScreenTimeTileModel.Tracked(
        distracting = minutesByCategory[AppCategory.DISTRACTING] ?: 0,
        productive = minutesByCategory[AppCategory.PRODUCTIVE] ?: 0,
        neutral = minutesByCategory[AppCategory.NEUTRAL] ?: 0,
    )
}

/**
 * Health for the tile: a metric the hunter didn't allow, or steps with no source, is unknown (null); nothing at all
 * recorded, Health not connected, or a failed read is [HealthTileModel.NoData]. Never a made-up number.
 */
internal fun HealthOverviewResult.toTile(): HealthTileModel {
    val overview = (this as? HealthOverviewResult.Ready)?.overview ?: return HealthTileModel.NoData
    val today = overview.trend.lastOrNull()
    val steps = overview.today.steps.takeIf { overview.stepSource != StepSource.NONE }
    val distance = overview.today.distanceMeters.takeIf { overview.canDistance && it > 0.0 }?.div(METERS_PER_KM)
    val kcal = overview.today.activeKcal.takeIf { overview.canCalories && it > 0.0 }?.toLong()
    val sleep = today?.sleepMinutes
    if (steps == null && distance == null && kcal == null && sleep == null) return HealthTileModel.NoData
    return HealthTileModel.Ready(steps, distance, kcal, sleep, overview.stepGoal)
}

private const val METERS_PER_KM = 1_000.0
