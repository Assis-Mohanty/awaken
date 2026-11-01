package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.YOUTUBE as YT
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CounterMachineLimitTest {

    @Test
    fun noLimitByDefaultNeverNudges() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 99)
        assertThat(h.state.countToday(IG)).isEqualTo(100)
        assertThat(h.nudges()).isEmpty()
    }

    @Test
    fun reachingLimitRequestsNudge() {
        val h = MachineHarness(limits = mapOf(IG to 50))
        h.open(IG, ms = 0)
        h.watchNext(IG, 49)
        assertThat(h.state.countToday(IG)).isEqualTo(50)
        assertThat(h.nudges()).containsExactly(CounterEffect.NudgeRequested(IG, 50, 2))
        val i = h.effects.indexOfFirst { it is CounterEffect.NudgeRequested }
        val before = h.effects[i - 1] as CounterEffect.Counted
        assertThat(before.countToday).isEqualTo(50)
    }

    @Test
    fun belowLimitNoNudge() {
        val h = MachineHarness(limits = mapOf(IG to 50))
        h.open(IG, ms = 0)
        h.watchNext(IG, 48)
        assertThat(h.state.countToday(IG)).isEqualTo(49)
        assertThat(h.nudges()).isEmpty()
    }

    @Test
    fun snoozeGrantsFiveMore() {
        val h = MachineHarness(limits = mapOf(IG to 3))
        h.open(IG, ms = 0)
        h.watchNext(IG, 2)
        h.snooze(IG)
        h.watchNext(IG, 4)
        assertThat(h.effects.filterIsInstance<CounterEffect.SnoozeAccepted>())
            .containsExactly(CounterEffect.SnoozeAccepted(IG, DAY_1, 1))
        assertThat(h.state.countToday(IG)).isEqualTo(7)
        assertThat(h.nudges()).hasSize(1)
    }

    @Test
    fun nudgeAgainWhenSnoozedAllowanceReached() {
        val h = MachineHarness(limits = mapOf(IG to 3))
        h.open(IG, ms = 0)
        h.watchNext(IG, 2)
        h.snooze(IG)
        h.watchNext(IG, 5)
        assertThat(h.state.countToday(IG)).isEqualTo(8)
        assertThat(h.nudges().last()).isEqualTo(CounterEffect.NudgeRequested(IG, 8, 1))
    }

    @Test
    fun nudgeRepeatsForEveryVideoWhileOverAllowance() {
        val h = MachineHarness(limits = mapOf(IG to 1))
        h.open(IG, ms = 0)
        h.watchNext(IG, 2)
        assertThat(h.nudges().map { it.countToday }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun snoozeCapIsTwoPerDay() {
        val h = MachineHarness(limits = mapOf(IG to 1))
        h.open(IG, ms = 0)
        h.snooze(IG)
        h.snooze(IG)
        h.snooze(IG)
        assertThat(h.snoozeEffects()).containsExactly(
            CounterEffect.SnoozeAccepted(IG, DAY_1, 1),
            CounterEffect.SnoozeAccepted(IG, DAY_1, 2),
            CounterEffect.SnoozeRejected(IG),
        ).inOrder()
        assertThat(h.state.snoozesUsed(IG)).isEqualTo(2)
        assertThat(h.state.allowance(IG, h.config.snoozeSize)).isEqualTo(11)
    }

    @Test
    fun snoozeWithoutLimitIsRejected() {
        val h = MachineHarness()
        h.snooze(IG, ms = 0)
        assertThat(h.effects).containsExactly(CounterEffect.SnoozeRejected(IG))
        assertThat(h.state.snoozesUsed(IG)).isEqualTo(0)
    }

    @Test
    fun limitsChangedMidDayAppliesImmediately() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 9)
        assertThat(h.setLimits(mapOf(IG to 12))).isEmpty()
        h.watchNext(IG, 1)
        assertThat(h.nudges()).isEmpty()
        h.watchNext(IG, 1)
        assertThat(h.nudges().map { it.countToday }).containsExactly(12)
    }

    @Test
    fun lowerLimitBelowCurrentCountNudgesOnNextVideo() {
        val h = MachineHarness()
        h.open(IG, ms = 0)
        h.watchNext(IG, 9)
        assertThat(h.setLimits(mapOf(IG to 5))).isEmpty()
        h.watchNext(IG, 1)
        assertThat(h.nudges()).containsExactly(CounterEffect.NudgeRequested(IG, 11, 2))
    }

    @Test
    fun removingLimitStopsNudges() {
        val h = MachineHarness(limits = mapOf(IG to 1))
        h.open(IG, ms = 0)
        h.setLimits(emptyMap())
        h.watchNext(IG, 1)
        assertThat(h.nudges()).hasSize(1)
    }

    @Test
    fun limitsChangedKeepsSnoozes() {
        val h = MachineHarness(limits = mapOf(IG to 1))
        h.open(IG, ms = 0)
        h.snooze(IG)
        h.setLimits(mapOf(IG to 2))
        assertThat(h.state.snoozesUsed(IG)).isEqualTo(1)
        assertThat(h.state.allowance(IG, h.config.snoozeSize)).isEqualTo(7)
    }

    @Test
    fun nonPositiveLimitsAreIgnored() {
        val h = MachineHarness()
        h.setLimits(mapOf(IG to 0, YT to -3))
        assertThat(h.state.limits).isEmpty()
    }
}
