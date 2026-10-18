package com.assismohanty.awaken.core.data.clock

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release builds use the real wall clock. Debug builds bind DebugClock (src/debug) for time travel. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    abstract fun bindClock(impl: SystemClock): Clock
}
