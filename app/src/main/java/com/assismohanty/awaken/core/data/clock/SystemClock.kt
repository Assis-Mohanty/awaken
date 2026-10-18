package com.assismohanty.awaken.core.data.clock

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Production [Clock] backed by the device wall clock and current time zone. */
@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()

    // Read on every call so a time-zone change while the app runs is picked up.
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
