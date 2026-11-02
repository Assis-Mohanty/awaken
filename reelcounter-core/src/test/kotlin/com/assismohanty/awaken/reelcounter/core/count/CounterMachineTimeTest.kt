package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.YOUTUBE as YT
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CounterMachineTimeTest {

    @Test
    fun timeAccumulatesFromTicksWhileOnScreen() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        for (ms in 1_000L..10_000L step 1_000) h.tick(ms)
        assertThat(h.seconds(IG)).isEqualTo(10)
        val added = h.effects.filterIsInstance<CounterEffect.TimeAdded>()
        assertThat(added).hasSize(10)
        assertThat(added.all { it.seconds == 1L }).isTrue()
    }

    @Test
    fun noTimeAccumulatesOffScreen() {
        val h = MachineHarness()
        for (ms in 0L..10_000L step 1_000) h.tick(ms)
        h.enter(IG, ms = 10_000, index = 0)
        h.leave(IG, ms = 12_000)
        h.tick(20_000)
        assertThat(h.seconds(IG)).isEqualTo(2)
    }

    @Test
    fun tickGapLargerThanCapIsClamped() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.tick(1_000)
        h.tick(61_000)
        assertThat(h.seconds(IG)).isEqualTo(6)
    }

    @Test
    fun subSecondCarryKeptAcrossLeave() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.tick(1_000)
        h.leave(IG, ms = 2_500)
        assertThat(h.seconds(IG)).isEqualTo(2)
        assertThat(h.state.apps.getValue(IG).carryMillis).isEqualTo(500)
        h.enter(IG, ms = 10_000, index = 0)
        h.tick(10_500)
        assertThat(h.seconds(IG)).isEqualTo(3)
    }

    @Test
    fun timeAttributedToCorrectAppWhenSwitching() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.tick(1_000)
        h.tick(2_000)
        h.enter(YT, ms = 3_000, index = 0)
        h.tick(4_000)
        assertThat(h.seconds(IG)).isEqualTo(3)
        assertThat(h.seconds(YT)).isEqualTo(1)
    }

    @Test
    fun outOfOrderEventWithEarlierTimestampAddsNoTime() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.tick(2_000)
        assertThat(h.tick(1_500)).isEmpty()
        h.tick(3_000)
        assertThat(h.seconds(IG)).isEqualTo(3)
    }

    @Test
    fun itemChangedAndScreenLeftAlsoAdvanceTime() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.item(IG, 1, ms = 1_000)
        h.leave(IG, ms = 2_000)
        assertThat(h.seconds(IG)).isEqualTo(2)
    }

    @Test
    fun timeAddedCarriesCurrentDay() {
        val h = MachineHarness()
        h.enter(IG, ms = 0, index = 0)
        h.tick(1_000)
        assertThat(h.effects.filterIsInstance<CounterEffect.TimeAdded>().single().day).isEqualTo(DAY_1)
    }
}
