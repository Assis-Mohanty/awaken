package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class GameDayTest {

    private val london = ZoneId.of("Europe/London")
    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(local: String, zone: ZoneId) = LocalDateTime.parse(local).atZone(zone).toInstant()

    @Test
    fun `03_59 belongs to the previous day and 04_00 to the current day`() {
        assertThat(GameDay.of(at("2026-10-18T03:59:59", kolkata), kolkata, 4)).isEqualTo(LocalDate.of(2026, 10, 17))
        assertThat(GameDay.of(at("2026-10-18T04:00:00", kolkata), kolkata, 4)).isEqualTo(LocalDate.of(2026, 10, 18))
        assertThat(GameDay.of(at("2026-10-18T23:59:59", kolkata), kolkata, 4)).isEqualTo(LocalDate.of(2026, 10, 18))
    }

    @Test
    fun `dayStartHour 0 is the calendar day`() {
        assertThat(GameDay.of(at("2026-10-18T00:00:00", kolkata), kolkata, 0)).isEqualTo(LocalDate.of(2026, 10, 18))
    }

    @Test
    fun `London spring-forward day is 23 hours and boundaries hold`() {
        val day = LocalDate.of(2026, 3, 29) // 01:00 GMT -> 02:00 BST
        assertThat(GameDay.of(at("2026-03-29T03:59:00", london), london, 4)).isEqualTo(day.minusDays(1))
        assertThat(GameDay.of(at("2026-03-29T04:00:00", london), london, 4)).isEqualTo(day)
        assertThat(GameDay.startOf(day, london, 4)).isEqualTo(LocalDateTime.parse("2026-03-29T03:00:00").toInstant(ZoneOffset.UTC))
        assertThat(Duration.between(GameDay.startOf(day.minusDays(1), london), GameDay.startOf(day, london))).isEqualTo(Duration.ofHours(23))
    }

    @Test
    fun `London fall-back day is 25 hours and the repeated hour stays in the previous day`() {
        val day = LocalDate.of(2026, 10, 25) // 02:00 BST -> 01:00 GMT
        val firstOneThirty = LocalDateTime.parse("2026-10-25T01:30:00").atZone(london).withEarlierOffsetAtOverlap().toInstant()
        val secondOneThirty = LocalDateTime.parse("2026-10-25T01:30:00").atZone(london).withLaterOffsetAtOverlap().toInstant()
        assertThat(firstOneThirty).isNotEqualTo(secondOneThirty)
        assertThat(GameDay.of(firstOneThirty, london, 4)).isEqualTo(day.minusDays(1))
        assertThat(GameDay.of(secondOneThirty, london, 4)).isEqualTo(day.minusDays(1))
        assertThat(GameDay.of(at("2026-10-25T04:00:00", london), london, 4)).isEqualTo(day)
        assertThat(Duration.between(GameDay.startOf(day.minusDays(1), london), GameDay.startOf(day, london))).isEqualTo(Duration.ofHours(25))
    }

    @Test
    fun `day start inside the London DST gap resolves to the first valid instant`() {
        val start = GameDay.startOf(LocalDate.of(2026, 3, 29), london, dayStartHour = 1)
        assertThat(start).isEqualTo(LocalDateTime.parse("2026-03-29T01:00:00").toInstant(ZoneOffset.UTC)) // 02:00 BST
    }

    @Test
    fun `Asia Kolkata has no DST so the same dates are plain 24-hour days`() {
        for (day in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
            assertThat(Duration.between(GameDay.startOf(day, kolkata), GameDay.endOf(day, kolkata))).isEqualTo(Duration.ofHours(24))
            assertThat(GameDay.startOf(day, kolkata)).isEqualTo(day.minusDays(1).atTime(22, 30).toInstant(ZoneOffset.UTC))
            assertThat(GameDay.of(at("${day}T03:59:00", kolkata), kolkata)).isEqualTo(day.minusDays(1))
        }
    }

    @Test
    fun `the same instant can be different game-days in different zones`() {
        val instant = LocalDateTime.parse("2026-10-17T23:00:00").toInstant(ZoneOffset.UTC) // 00:00 BST, 04:30 IST
        assertThat(GameDay.of(instant, london)).isEqualTo(LocalDate.of(2026, 10, 17))
        assertThat(GameDay.of(instant, kolkata)).isEqualTo(LocalDate.of(2026, 10, 18))
    }
}
