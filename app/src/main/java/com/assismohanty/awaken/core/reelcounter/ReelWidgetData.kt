package com.assismohanty.awaken.core.reelcounter

import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.reelcounter.core.time.GameDay
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.ReelDayStart
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Today's reel total for the Today widget and the Today tile. Null while Reel counter is switched off, so neither
 * shows anything for a feature the hunter has not turned on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ReelWidgetData @Inject constructor(
    private val settings: SettingsStore,
    private val repository: ReelRepository,
    private val clock: ReelClock,
    private val dayStart: ReelDayStart,
    private val dayRefresher: DayRefresher,
) {
    fun observe(): Flow<Int?> = settings.enabled.flatMapLatest { on ->
        if (!on) {
            flowOf(null)
        } else {
            dayRefresher.ticks
                .map { today() }
                .distinctUntilChanged()
                .flatMapLatest { day -> repository.observeDay(day).map { rows -> rows.sumOf { it.count } } }
        }
    }.distinctUntilChanged()

    suspend fun current(): Int? = observe().first()

    private suspend fun today() = GameDay(clock.zone(), LocalTime.of(dayStart.dayStartHour.first().coerceIn(0, 23), 0)).dayOf(clock.now())
}
