package com.assismohanty.awaken.reelcounter.time

import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import java.time.Instant
import java.time.ZoneId

/** The only place in the module that reads the real time. */
class SystemReelClock : ReelClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
