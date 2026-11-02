package com.assismohanty.awaken.reelcounter.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** The only source of time. Real implementation lives in :android. */
interface ReelClock {
    fun now(): Instant
    fun zone(): ZoneId
}

class FixedReelClock(
    var instant: Instant,
    var zoneId: ZoneId = ZoneOffset.UTC,
) : ReelClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    fun advanceMillis(ms: Long) {
        instant = instant.plusMillis(ms)
    }
}
