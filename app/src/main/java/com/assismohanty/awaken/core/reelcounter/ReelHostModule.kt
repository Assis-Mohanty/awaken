package com.assismohanty.awaken.core.reelcounter

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.ReelDayStart
import com.assismohanty.awaken.reelcounter.overlay.ReelOverlayUi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

/**
 * What the Reel Counter module needs from AWAKEN: AWAKEN's own [Clock] (so debug time travel moves it too) and the
 * hunter's day start (GD §2), instead of a clock and day start of its own.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReelHostModule {
    @Provides
    @Singleton
    fun reelClock(clock: Clock): ReelClock = object : ReelClock {
        override fun now(): Instant = clock.now()
        override fun zone(): ZoneId = clock.zone()
    }

    @Provides
    @Singleton
    fun reelDayStart(hunterDao: HunterDao): ReelDayStart = object : ReelDayStart {
        override val dayStartHour = hunterDao.observeProfile()
            .map { it?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR }
            .distinctUntilChanged()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReelHostBindings {
    @Binds
    abstract fun overlayUi(impl: ReelOverlayWindows): ReelOverlayUi
}
