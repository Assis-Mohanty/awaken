package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CounterMachineRestartTest {

    @Test
    fun initialStateRestoresTodayCountsAndSnoozes() {
        val h = MachineHarness(
            limits = mapOf(IG to 5),
            todayCounts = mapOf(IG to 4),
            todaySnoozes = mapOf(IG to 2),
        )
        h.open(IG, ms = 0)
        h.snooze(IG)
        assertThat(h.state.countToday(IG)).isEqualTo(5)
        assertThat(h.nudges()).isEmpty()
        assertThat(h.snoozeEffects()).containsExactly(CounterEffect.SnoozeRejected(IG))
    }

    @Test
    fun restartWithinGraceOfRestoredSessionDoesNotRecount() {
        val h = MachineHarness(
            todayCounts = mapOf(IG to 10),
            restored = mapOf(IG to RestoredSession(111, 7, T0)),
        )
        h.enter(IG, ms = 60_000, index = 7)
        h.tick(60_400)
        h.item(IG, 8, ms = 61_000)
        h.tick(61_400)
        assertThat(h.state.countToday(IG)).isEqualTo(11)
        val only = h.counted().single()
        assertThat(only.sessionId).isEqualTo(111)
        assertThat(only.index).isEqualTo(8)
    }

    @Test
    fun restartAfterGraceOfRestoredSessionCountsVisibleVideo() {
        val h = MachineHarness(
            todayCounts = mapOf(IG to 10),
            restored = mapOf(IG to RestoredSession(111, 7, T0)),
        )
        h.enter(IG, ms = 700_000, index = 0)
        h.tick(700_400)
        assertThat(h.state.countToday(IG)).isEqualTo(11)
        assertThat(h.counted().single().sessionId).isNotEqualTo(111)
    }

    @Test
    fun restartWithoutRestoreCountsVisibleVideoOnce() {
        val h = MachineHarness(todayCounts = mapOf(IG to 10))
        h.enter(IG, ms = 0, index = 7)
        h.tick(400)
        h.tick(5_000)
        assertThat(h.state.countToday(IG)).isEqualTo(11)
    }

    @Test
    fun restartRenumberingFromZeroWithinGraceStartsANewSessionThatCounts() {
        val h = MachineHarness(
            todayCounts = mapOf(IG to 10),
            restored = mapOf(IG to RestoredSession(111, 40, T0)),
        )
        h.enter(IG, ms = 60_000, index = 0)
        h.tick(60_400)
        assertThat(h.state.countToday(IG)).isEqualTo(10)
        h.item(IG, 1, ms = 61_000)
        h.tick(61_400)
        assertThat(h.state.countToday(IG)).isEqualTo(11)
        val only = h.counted().single()
        assertThat(only.sessionId).isEqualTo(T0.plusMillis(60_000).toEpochMilli())
        assertThat(only.index).isEqualTo(1)
    }

    @Test
    fun adoptingARestartedListIndexStartsANewSession() {
        val h = MachineHarness()
        h.open(IG, ms = 0, index = 0)
        h.watchNext(IG, 40)
        assertThat(h.state.countToday(IG)).isEqualTo(41)
        h.leave(IG, h.t + 1_000)
        h.enter(IG, h.t + 1_000, index = 40)
        h.send(CounterEvent.ItemAdopted(IG, 0, h.now()))
        h.settle()
        assertThat(h.state.countToday(IG)).isEqualTo(41)
        h.swipeTo(IG, 1)
        assertThat(h.state.countToday(IG)).isEqualTo(42)
    }

    @Test
    fun adoptingOneStepBackIsASwipeBackAndRecountsNothing() {
        val h = MachineHarness()
        h.open(IG, ms = 0, index = 0)
        h.watchNext(IG, 5)
        h.leave(IG, h.t + 1_000)
        h.enter(IG, h.t + 1_000, index = 5)
        h.send(CounterEvent.ItemAdopted(IG, 4, h.now()))
        h.swipeTo(IG, 5)
        assertThat(h.state.countToday(IG)).isEqualTo(6)
        h.swipeTo(IG, 6)
        assertThat(h.state.countToday(IG)).isEqualTo(7)
    }
}
