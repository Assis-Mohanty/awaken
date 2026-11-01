package com.assismohanty.awaken.reelcounter.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class GameDayTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant()

    @Test
    fun beforeDayStartBelongsToPreviousDay() {
        val gd = GameDay(kolkata)
        assertThat(gd.dayOf(at(kolkata, 2026, 11, 2, 3, 59))).isEqualTo(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun exactlyAtDayStartIsNewDay() {
        val gd = GameDay(kolkata)
        assertThat(gd.dayOf(at(kolkata, 2026, 11, 2, 4, 0))).isEqualTo(LocalDate.of(2026, 11, 2))
    }

    @Test
    fun customDayStartMidnight() {
        val gd = GameDay(kolkata, LocalTime.MIDNIGHT)
        assertThat(gd.dayOf(at(kolkata, 2026, 11, 2, 0, 0))).isEqualTo(LocalDate.of(2026, 11, 2))
    }

    @Test
    fun nextBoundaryLaterToday() {
        val gd = GameDay(kolkata)
        assertThat(gd.nextBoundary(at(kolkata, 2026, 11, 2, 3, 0))).isEqualTo(at(kolkata, 2026, 11, 2, 4, 0))
    }

    @Test
    fun nextBoundaryTomorrow() {
        val gd = GameDay(kolkata)
        assertThat(gd.nextBoundary(at(kolkata, 2026, 11, 2, 10, 0))).isEqualTo(at(kolkata, 2026, 11, 3, 4, 0))
    }

    @Test
    fun nextBoundaryAtExactBoundaryIsNextDay() {
        val gd = GameDay(kolkata)
        assertThat(gd.nextBoundary(at(kolkata, 2026, 11, 2, 4, 0))).isEqualTo(at(kolkata, 2026, 11, 3, 4, 0))
    }

    @Test
    fun dstSpringForwardGapResolvesForward() {
        val gd = GameDay(newYork, LocalTime.of(2, 30))
        val boundary = gd.nextBoundary(at(newYork, 2026, 3, 8, 1, 0))
        assertThat(boundary).isEqualTo(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, newYork).toInstant())
    }

    @Test
    fun dstFallBackOverlapUsesEarlierOffset() {
        val gd = GameDay(newYork, LocalTime.of(1, 30))
        val boundary = gd.nextBoundary(at(newYork, 2026, 11, 1, 0, 0))
        assertThat(boundary).isEqualTo(java.time.OffsetDateTime.parse("2026-11-01T01:30:00-04:00").toInstant())
    }
}
