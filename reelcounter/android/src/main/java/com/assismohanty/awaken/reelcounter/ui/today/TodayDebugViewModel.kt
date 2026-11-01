package com.assismohanty.awaken.reelcounter.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.stats.DayTotal
import com.assismohanty.awaken.reelcounter.core.time.GameDay
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.assismohanty.awaken.reelcounter.service.ServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class AppTodayRow(val app: ShortVideoApp, val count: Int, val seconds: Long)

data class TodayDebugUiState(
    val serviceConnected: Boolean = false,
    val rows: List<AppTodayRow> = emptyList(),
) {
    val total: Int get() = rows.sumOf { it.count }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayDebugViewModel @Inject constructor(
    repository: ReelRepository,
    settings: SettingsStore,
    private val clock: ReelClock,
    status: ServiceStatus,
) : ViewModel() {

    private val today: Flow<LocalDate> = settings.dayStartMinutes.flatMapLatest { minutes ->
        val gameDay = GameDay(clock.zone(), LocalTime.of(minutes / 60, minutes % 60))
        flow {
            while (true) {
                emit(gameDay.dayOf(clock.now()))
                delay(TICK_MILLIS)
            }
        }
    }.distinctUntilChanged()

    val state: StateFlow<TodayDebugUiState> = combine(
        today.flatMapLatest { repository.observeDay(it) }.map(::toRows),
        status.connected,
    ) { rows, connected -> TodayDebugUiState(connected, rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayDebugUiState())

    private fun toRows(totals: List<DayTotal>): List<AppTodayRow> =
        ShortVideoApp.entries.map { app ->
            val row = totals.firstOrNull { it.app == app }
            AppTodayRow(app, row?.count ?: 0, row?.seconds ?: 0L)
        }

    private companion object {
        const val TICK_MILLIS = 30_000L
    }
}
