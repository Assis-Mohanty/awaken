package com.assismohanty.awaken.core.data.clock

import java.time.Instant
import java.time.ZoneId

/**
 * The only source of "now" in the app (CLAUDE.md invariant 6). Inject it; never read system time directly.
 * Game-day conversion (dayStartHour) is done by the engine's GameDay from [now] and [zone].
 */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
}
