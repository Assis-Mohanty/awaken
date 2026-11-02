package com.assismohanty.awaken.core.reelcounter

import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.reelcounter.core.lumi.LumiState
import com.assismohanty.awaken.reelcounter.core.lumi.lumiDailyLimit
import com.assismohanty.awaken.reelcounter.core.lumi.lumiStage
import com.assismohanty.awaken.reelcounter.core.stats.DayTotal
import com.assismohanty.awaken.reelcounter.core.time.GameDay
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.ReelDayStart
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Today's reels across every app, and the one daily limit Lumi follows (null when no app has a limit). */
data class ReelLumi(val total: Int, val limit: Int?) {
    val state: LumiState get() = lumiStage(total, limit)
}

/**
 * Today's reel numbers for the Today widget, the Today tile and Screen time. Null while Reel counter is switched off, so none
 * of them shows anything for a feature the hunter has not turned on.
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
    /** Today's per-app totals, or null while the feature is off. */
    fun observeRows(): Flow<List<DayTotal>?> = settings.enabled.flatMapLatest { on ->
        if (!on) {
            flowOf(null)
        } else {
            dayRefresher.ticks
                .map { today() }
                .distinctUntilChanged()
                .flatMapLatest { day -> repository.observeDay(day) }
        }
    }.distinctUntilChanged()

    fun observe(): Flow<Int?> = observeRows().map { rows -> rows?.sumOf { it.count } }.distinctUntilChanged()

    suspend fun current(): Int? = observe().first()

    /** Today's total with Lumi's limit, or null while the feature is off. Lumi's stage is [ReelLumi.state]. */
    fun observeLumi(): Flow<ReelLumi?> =
        combine(observe(), settings.limits) { total, limits -> total?.let { ReelLumi(it, lumiDailyLimit(limits.values)) } }
            .distinctUntilChanged()

    suspend fun currentLumi(): ReelLumi? = observeLumi().first()

    private suspend fun today() = GameDay(clock.zone(), LocalTime.of(dayStart.dayStartHour.first().coerceIn(0, 23), 0)).dayOf(clock.now())
}
