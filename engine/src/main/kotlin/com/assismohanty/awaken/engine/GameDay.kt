package com.assismohanty.awaken.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Game-day mapping (GD §2): a day runs `dayStartHour:00` → `dayStartHour−1:59` local. Uses local wall-clock time, so DST
 * shifts never move a moment into the wrong day.
 */
object GameDay {
    const val DEFAULT_DAY_START_HOUR = 4

    fun of(instant: Instant, zone: ZoneId, dayStartHour: Int = DEFAULT_DAY_START_HOUR): LocalDate {
        require(dayStartHour in 0..23) { "dayStartHour must be in 0..23" }
        val local = instant.atZone(zone).toLocalDateTime()
        return if (local.hour < dayStartHour) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** The instant [gameDate] begins. If `dayStartHour:00` falls in a DST gap, the first valid instant after it. */
    fun startOf(gameDate: LocalDate, zone: ZoneId, dayStartHour: Int = DEFAULT_DAY_START_HOUR): Instant {
        require(dayStartHour in 0..23) { "dayStartHour must be in 0..23" }
        return ZonedDateTime.of(gameDate, LocalTime.of(dayStartHour, 0), zone).toInstant()
    }

    /** Exclusive end of [gameDate] = start of the next game-day. */
    fun endOf(gameDate: LocalDate, zone: ZoneId, dayStartHour: Int = DEFAULT_DAY_START_HOUR): Instant =
        startOf(gameDate.plusDays(1), zone, dayStartHour)
}
