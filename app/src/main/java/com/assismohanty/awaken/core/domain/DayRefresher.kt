package com.assismohanty.awaken.core.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runs the day rollover when the app comes to the foreground and tells observers to re-derive "today". This covers
 * crossing the day start while backgrounded and debug time travel; the in-app day-start tick arrives in Phase 7.
 */
@Singleton
class DayRefresher @Inject constructor(private val rollover: DayRolloverUseCase) {
    private val refreshes = MutableStateFlow(0L)

    /** Emits after each completed refresh; pass it as `dayChanges` to [ObserveHunterStateUseCase]. */
    val ticks: StateFlow<Long> = refreshes.asStateFlow()

    suspend fun refresh() {
        rollover()
        refreshes.update { it + 1 }
    }
}
