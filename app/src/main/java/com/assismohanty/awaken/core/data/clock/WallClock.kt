package com.assismohanty.awaken.core.data.clock

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant

/**
 * The real wall clock, for system APIs that work in it (the usage log the Protect watchers read). Debug time travel shifts
 * [Clock] but never this, so a shifted day cannot blind the watchers; game-day logic keeps using [Clock] (invariant 6).
 */
fun interface WallClock {
    fun now(): Instant
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WallClockModule {
    // SystemClock is the one place real time is read, in every build type.
    @Binds
    abstract fun bindWallClock(impl: SystemClock): WallClock
}
