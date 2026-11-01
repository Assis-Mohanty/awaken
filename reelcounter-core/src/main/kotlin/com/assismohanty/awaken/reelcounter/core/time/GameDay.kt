package com.assismohanty.awaken.reelcounter.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Maps instants to a "game day" that starts at [dayStart] local time instead of midnight. */
class GameDay(
    private val zone: ZoneId,
    private val dayStart: LocalTime = DEFAULT_DAY_START,
) {
    fun dayOf(at: Instant): LocalDate {
        val local = at.atZone(zone)
        return if (local.toLocalTime() < dayStart) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** First instant strictly after [after] at which [dayOf] changes. */
    fun nextBoundary(after: Instant): Instant {
        val today = after.atZone(zone).toLocalDate()
        for (offset in 0L..2L) {
            val candidate = ZonedDateTime.of(today.plusDays(offset), dayStart, zone).toInstant()
            if (candidate.isAfter(after)) return candidate
        }
        return ZonedDateTime.of(today.plusDays(3), dayStart, zone).toInstant()
    }

    companion object {
        val DEFAULT_DAY_START: LocalTime = LocalTime.of(4, 0)
    }
}
