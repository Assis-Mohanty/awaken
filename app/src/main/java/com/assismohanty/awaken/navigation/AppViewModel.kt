package com.assismohanty.awaken.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveHunterStateUseCase
import com.assismohanty.awaken.engine.model.Rank
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
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
}

/** Decides onboarding vs the main tabs, and runs the day rollover each time the app is opened (ARCH §3.2). */
@HiltViewModel
class AppViewModel @Inject constructor(
    observeHunterState: ObserveHunterStateUseCase,
    private val dayRefresher: DayRefresher,
) : ViewModel() {

    val state: StateFlow<LaunchState> = observeHunterState(dayChanges = dayRefresher.ticks)
        .map { hunter -> if (hunter == null) LaunchState.NeedsOnboarding else LaunchState.Ready(hunter.rank) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LaunchState.Loading)

    fun onEvent(event: AppEvent) {
        when (event) {
            AppEvent.Started -> viewModelScope.launch {
                try {
                    dayRefresher.refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Today still shows the last generated state; the next open retries.
                    Timber.e(e, "Day rollover failed")
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
