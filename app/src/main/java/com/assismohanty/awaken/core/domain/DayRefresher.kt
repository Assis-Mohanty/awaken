package com.assismohanty.awaken.core.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runs the day rollover in-app (foreground, day-start tick) and tells observers to re-derive "today". Background runs
 * happen in DayRolloverWorker; the foreground refresh picks up whatever they did.
 */
@Singleton
class DayRefresher @Inject constructor(private val rollover: RunRolloverUseCase) {
    private val refreshes = MutableStateFlow(0L)

    /** Emits after each completed refresh; pass it as `dayChanges` to [ObserveHunterStateUseCase]. */
    val ticks: StateFlow<Long> = refreshes.asStateFlow()

    suspend fun refresh() {
        rollover()
        refreshes.update { it + 1 }
    }
}
