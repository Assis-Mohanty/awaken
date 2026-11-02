package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.YOUTUBE as YT
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CounterMachineSessionTest {

    @Test
    fun leaveAndReturnWithinGraceToSameVideoDoesNotRecount() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 1)
        h.leave(IG, ms = 5_000)
        h.enter(IG, ms = 65_000, index = 1)
        h.tick(66_000)
        assertThat(h.state.countToday(IG)).isEqualTo(2)
        assertThat(h.counted()).hasSize(2)
    }

    @Test
    fun leaveAndReturnWithinGraceThenNewVideoCounts() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 1)
        h.leave(IG, ms = 5_000)
        h.enter(IG, ms = 65_000, index = 1)
        h.tick(66_000)
        h.item(IG, 2, ms = 67_000)
        h.tick(67_400)
        assertThat(h.state.countToday(IG)).isEqualTo(3)
        assertThat(h.counted().last().index).isEqualTo(2)
        assertThat(h.counted().last().sessionId).isEqualTo(h.counted().first().sessionId)
    }

    @Test
    fun returnExactlyAtGraceResumesSession() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.leave(IG, ms = 1_000)
        h.enter(IG, ms = 601_000, index = 0)
        h.tick(601_400)
        assertThat(h.state.countToday(IG)).isEqualTo(1)
    }

    @Test
    fun returnAfterGraceStartsNewSessionAndCountsVisibleVideo() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.leave(IG, ms = 1_000)
        h.enter(IG, ms = 601_001, index = 0)
        h.tick(601_401)
        assertThat(h.state.countToday(IG)).isEqualTo(2)
        assertThat(h.counted()[1].index).isEqualTo(0)
    }

    @Test
    fun newSessionIdIsEnterTimestamp() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.leave(IG, ms = 1_000)
        h.enter(IG, ms = 601_001, index = 0)
        h.tick(601_401)
        assertThat(h.counted()[0].sessionId).isEqualTo(T0.toEpochMilli())
        assertThat(h.counted()[1].sessionId).isEqualTo(T0.toEpochMilli() + 601_001)
    }

    @Test
    fun appSwitchMidVideoKeepsSessionsSeparate() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 1)
        h.open(YT, ms = h.t + 1_000)
        h.enter(IG, ms = h.t + 1_000, index = 1)
        h.tick(h.t + 400)
        assertThat(h.state.countToday(IG)).isEqualTo(2)
        assertThat(h.state.countToday(YT)).isEqualTo(1)
        assertThat(h.bubbles().map { it.app }).containsExactly(IG, YT, IG).inOrder()
    }

    @Test
    fun appSwitchBeforeSettleDropsPendingOfOldApp() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.item(IG, 1, ms = 1_000)
        h.enter(YT, ms = 1_100, index = 0)
        h.tick(1_500)
        assertThat(h.state.countToday(IG)).isEqualTo(1)
        assertThat(h.state.countToday(YT)).isEqualTo(1)
    }

    @Test
    fun droppedPendingIsCountedWhenReturningToIt() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.item(IG, 1, ms = 1_000)
        h.enter(YT, ms = 1_100, index = 0)
        h.tick(1_500)
        h.enter(IG, ms = 2_000, index = 1)
        h.tick(2_400)
        assertThat(h.state.countToday(IG)).isEqualTo(2)
    }

    @Test
    fun pendingItemIsCommittedOnScreenLeftIfSettled() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.item(IG, 1, ms = 1_000)
        h.leave(IG, ms = 1_400)
        assertThat(h.state.countToday(IG)).isEqualTo(2)
    }

    @Test
    fun pendingItemIsDroppedOnScreenLeftIfNotSettled() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.item(IG, 1, ms = 1_000)
        h.leave(IG, ms = 1_200)
        assertThat(h.state.countToday(IG)).isEqualTo(1)
    }

    @Test
    fun duplicateScreenEnteredIsIdempotent() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.enter(IG, ms = 100, index = 0)
        h.tick(500)
        assertThat(h.state.countToday(IG)).isEqualTo(1)
        assertThat(h.bubbles().map { it.app }).containsExactly(IG)
        assertThat(h.state.sessions).hasSize(1)
    }

    @Test
    fun screenLeftWithoutEnterIsNoOp() {
        val h = MachineHarness()
        h.leave(IG, ms = 0)
        assertThat(h.effects).isEmpty()
        assertThat(h.state).isEqualTo(CounterState.initial(DAY_1, emptyMap(), emptyMap(), emptyMap()))
    }

    @Test
    fun screenLeftForOtherAppIsIgnored() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.leave(YT, ms = 1_000)
        assertThat(h.state.active?.app).isEqualTo(IG)
        assertThat(h.bubbles().map { it.app }).containsExactly(IG)
    }

    @Test
    fun screenOffThenOnSameVideoNoRecountNoOffTime() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.tick(1_000)
        h.tick(2_000)
        h.leave(IG, ms = 2_000)
        h.enter(IG, ms = 62_000, index = 0)
        h.tick(63_000)
        assertThat(h.state.countToday(IG)).isEqualTo(1)
        assertThat(h.seconds(IG)).isEqualTo(3)
    }
}
