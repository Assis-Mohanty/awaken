package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderRulesTest {

    private val utc = ZoneOffset.UTC
    private val seven = LocalTime.of(7, 0)

    private fun at(text: String): Instant = Instant.parse(text)

    @Test
    fun `next fire is today when the time is still ahead`() {
        assertThat(ReminderRules.nextFire(at("2026-10-19T06:59:00Z"), utc, seven)).isEqualTo(at("2026-10-19T07:00:00Z"))
    }

    @Test
    fun `next fire is tomorrow at or after the time`() {
        assertThat(ReminderRules.nextFire(at("2026-10-19T07:00:00Z"), utc, seven)).isEqualTo(at("2026-10-20T07:00:00Z"))
        assertThat(ReminderRules.nextFire(at("2026-10-19T22:00:00Z"), utc, seven)).isEqualTo(at("2026-10-20T07:00:00Z"))
    }

    @Test
    fun `next fire follows the local clock across a DST change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-10-25 03:00 CEST falls back to 02:00 CET: 07:00 local is 05:00Z before, 06:00Z after.
        assertThat(ReminderRules.nextFire(at("2026-10-24T12:00:00Z"), berlin, seven)).isEqualTo(at("2026-10-25T06:00:00Z"))
        assertThat(ReminderRules.nextFire(at("2026-10-23T12:00:00Z"), berlin, seven)).isEqualTo(at("2026-10-24T05:00:00Z"))
    }

    @Test
    fun `a time inside a spring-forward gap lands on the first valid instant`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 02:30 does not exist in Berlin; it resolves to 03:30 CEST (01:30Z).
        assertThat(ReminderRules.nextFire(at("2026-03-28T12:00:00Z"), berlin, LocalTime.of(2, 30))).isEqualTo(at("2026-03-29T01:30:00Z"))
    }

    private val date = LocalDate.parse("2026-10-19")

    @Test
    fun `last call needs a reminder before 21 00 and an uncleared day`() {
        val open = DayRecord(date, mainCompleted = false, itemsCompleted = 1)
        assertThat(ReminderRules.shouldLastCall(seven, open)).isTrue()
        assertThat(ReminderRules.shouldLastCall(null, open)).isFalse()
        assertThat(ReminderRules.shouldLastCall(LocalTime.of(21, 0), open)).isFalse()
        assertThat(ReminderRules.shouldLastCall(LocalTime.of(20, 59), open)).isTrue()
    }

    @Test
    fun `last call is skipped on a cleared or rest day`() {
        assertThat(ReminderRules.shouldLastCall(seven, DayRecord(date, mainCompleted = true))).isFalse()
        assertThat(ReminderRules.shouldLastCall(seven, DayRecord(date, itemsCompleted = 3))).isFalse()
        assertThat(ReminderRules.shouldLastCall(seven, DayRecord(date, restDay = true))).isFalse()
    }

    @Test
    fun `quests to clear counts what is left`() {
        assertThat(ReminderRules.questsToClear(DayRecord(date, itemsCompleted = 0), mainOpen = true)).isEqualTo(1)
        assertThat(ReminderRules.questsToClear(DayRecord(date, itemsCompleted = 2), mainOpen = true)).isEqualTo(1)
        assertThat(ReminderRules.questsToClear(DayRecord(date, itemsCompleted = 0), mainOpen = false)).isEqualTo(3)
        assertThat(ReminderRules.questsToClear(DayRecord(date, itemsCompleted = 1), mainOpen = false)).isEqualTo(2)
        assertThat(ReminderRules.questsToClear(DayRecord(date, itemsCompleted = 2), mainOpen = false)).isEqualTo(1)
    }

    @Test
    fun `a cleared day needs nothing more`() {
        assertThat(ReminderRules.questsToClear(DayRecord(date, mainCompleted = true), mainOpen = false)).isEqualTo(0)
        assertThat(ReminderRules.questsToClear(DayRecord(date, itemsCompleted = 3), mainOpen = true)).isEqualTo(0)
    }
}
