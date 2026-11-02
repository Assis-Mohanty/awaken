package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CounterMachineRolloverTest {

    @Test
    fun dayRolledResetsCountsToZero() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 2)
        h.roll(DAY_2, ms = 10_000)
        assertThat(h.state.countToday(IG)).isEqualTo(0)
        assertThat(h.state.day).isEqualTo(DAY_2)
        assertThat(h.state.totalToday()).isEqualTo(0)
    }

    @Test
    fun rolloverMidSessionSplitsTimeAcrossDays() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.tick(1_000)
        h.tick(2_000)
        h.tick(3_000)
        h.roll(DAY_2, ms = 3_500)
        h.tick(4_500)
        assertThat(h.seconds(IG, DAY_1)).isEqualTo(3)
        assertThat(h.seconds(IG, DAY_2)).isEqualTo(1)
    }

    @Test
    fun rolloverMidSessionDoesNotRecountVideoOnScreen() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 1)
        val before = h.counted().size
        h.roll(DAY_2, ms = h.t + 4_000)
        h.tick(h.t + 1_000)
        h.tick(h.t + 1_000)
        assertThat(h.state.countToday(IG)).isEqualTo(0)
        assertThat(h.counted()).hasSize(before)
    }

    @Test
    fun rolloverMidSessionNextNewVideoCountsOnNewDay() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 1)
        val sessionId = h.counted().first().sessionId
        h.roll(DAY_2, ms = h.t + 4_000)
        h.tick(h.t + 1_000)
        h.item(IG, 2, ms = h.t + 1_000)
        h.tick(h.t + 400)
        assertThat(h.state.countToday(IG)).isEqualTo(1)
        val last = h.counted().last()
        assertThat(last.day).isEqualTo(DAY_2)
        assertThat(last.countToday).isEqualTo(1)
        assertThat(last.sessionId).isEqualTo(sessionId)
    }

    @Test
    fun settledPendingAtRolloverCountsOnOldDay() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.item(IG, 1, ms = 1_000)
        h.roll(DAY_2, ms = 1_400)
        val last = h.counted().last()
        assertThat(last.index).isEqualTo(1)
        assertThat(last.day).isEqualTo(DAY_1)
        assertThat(last.countToday).isEqualTo(2)
        assertThat(h.state.countToday(IG)).isEqualTo(0)
    }

    @Test
    fun rolloverResetsSnoozes() {
        val h = MachineHarness(limits = mapOf(IG to 5))
        h.snooze(IG, ms = 0)
        assertThat(h.state.snoozesUsed(IG)).isEqualTo(1)
        h.roll(DAY_2, ms = 1_000)
        assertThat(h.state.snoozesUsed(IG)).isEqualTo(0)
        assertThat(h.state.allowance(IG, h.config.snoozeSize)).isEqualTo(5)
    }

    @Test
    fun duplicateOrOlderDayRolledIsNoOp() {
        val h = MachineHarness()
        assertThat(h.roll(DAY_1, ms = 0)).isEmpty()
        h.roll(DAY_2, ms = 1_000)
        h.open(IG, ms = 2_000)
        assertThat(h.state.countToday(IG)).isEqualTo(1)
        val before = h.state
        assertThat(h.roll(DAY_2, ms = 3_000)).isEmpty()
        assertThat(h.roll(DAY_1, ms = 4_000)).isEmpty()
        assertThat(h.state.day).isEqualTo(DAY_2)
        assertThat(h.state.countToday(IG)).isEqualTo(before.countToday(IG))
    }

    @Test
    fun rolloverWhileOffScreenThenReturnAfterGraceCounts() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.leave(IG, ms = 1_000)
        h.roll(DAY_2, ms = 2_000)
        h.enter(IG, ms = 700_000, index = 0)
        h.tick(700_400)
        assertThat(h.state.countToday(IG)).isEqualTo(1)
        assertThat(h.counted().last().day).isEqualTo(DAY_2)
    }

    @Test
    fun rolloverWhileOffScreenReturnWithinGraceSameVideoNotRecounted() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.leave(IG, ms = 1_000)
        h.roll(DAY_2, ms = 2_000)
        h.enter(IG, ms = 3_000, index = 0)
        h.tick(3_400)
        assertThat(h.state.countToday(IG)).isEqualTo(0)
    }
}
