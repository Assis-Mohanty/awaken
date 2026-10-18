package com.assismohanty.awaken.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveHunterStateUseCase
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.model.Rank
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface LaunchState {
    data object Loading : LaunchState
    data object NeedsOnboarding : LaunchState
    data class Ready(val rank: Rank) : LaunchState
}

sealed interface AppEvent {
    /** The app came to the foreground. */
    data object Started : AppEvent

    /** The app left the foreground; the background worker takes over day starts. */
    data object Stopped : AppEvent
}

/**
 * Decides onboarding vs the main tabs and keeps "today" current while the app is visible: a rollover on every
 * foreground, and another at each day start crossed while open (ARCH §3.2).
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    observeHunterState: ObserveHunterStateUseCase,
    private val dayRefresher: DayRefresher,
    private val hunterDao: HunterDao,
    private val clock: Clock,
) : ViewModel() {

    private var dayStartTick: Job? = null

    val state: StateFlow<LaunchState> = observeHunterState(dayChanges = dayRefresher.ticks)
        .map { hunter -> if (hunter == null) LaunchState.NeedsOnboarding else LaunchState.Ready(hunter.rank) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LaunchState.Loading)

    fun onEvent(event: AppEvent) {
        when (event) {
            AppEvent.Started -> {
                viewModelScope.launch { refresh() }
                dayStartTick?.cancel()
                dayStartTick = viewModelScope.launch {
                    while (true) {
                        delay(untilNextDayStart().toMillis())
                        refresh()
                    }
                }
            }
            AppEvent.Stopped -> {
                dayStartTick?.cancel()
                dayStartTick = null
            }
        }
    }

    private suspend fun untilNextDayStart(): Duration {
        val dayStartHour = hunterDao.getProfile()?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR
        val nextStart = GameDay.endOf(clock.gameDate(dayStartHour), clock.zone(), dayStartHour)
        // A small margin so the tick lands inside the new day, never on its boundary.
        return Duration.between(clock.now(), nextStart).plus(DAY_START_MARGIN).coerceAtLeast(DAY_START_MARGIN)
    }

    private suspend fun refresh() {
        try {
            dayRefresher.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Today still shows the last generated state; the next open retries.
            Timber.e(e, "Day rollover failed")
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val DAY_START_MARGIN: Duration = Duration.ofSeconds(1)
    }
}
