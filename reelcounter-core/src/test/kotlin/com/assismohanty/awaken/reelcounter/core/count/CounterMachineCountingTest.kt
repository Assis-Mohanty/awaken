package com.assismohanty.awaken.reelcounter.core.count

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.YOUTUBE
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CounterMachineCountingTest {

    @Test
    fun firstVideoCountsAfterSettle() {
        val h = MachineHarness()
        h.enter(INSTAGRAM, ms = 0)
        h.tick(ms = 400)
        assertThat(h.counted()).hasSize(1)
        assertThat(h.counted().single().index).isEqualTo(0)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(1)
    }

    @Test
    fun itemNotCountedBeforeSettleWindow() {
        val h = MachineHarness()
        h.enter(INSTAGRAM, ms = 0)
        h.tick(ms = 349)
        assertThat(h.counted()).isEmpty()
        h.tick(ms = 350)
        assertThat(h.counted()).hasSize(1)
    }

    @Test
    fun rapidDuplicateItemChangedSameIndexCountsOnce() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.item(INSTAGRAM, 1, ms = 1_000)
        h.item(INSTAGRAM, 1, ms = 1_005)
        h.item(INSTAGRAM, 1, ms = 1_010)
        h.tick(ms = 1_500)
        h.item(INSTAGRAM, 1, ms = 2_000)
        h.tick(ms = 3_000)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(2)
    }

    @Test
    fun burstOfEventsWithinSettleCountsOnce() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        // Duplicate events must not restart the settle window, or a chatty app would never commit.
        for (ms in 1_000L..1_400L step 50) h.item(INSTAGRAM, 1, ms = ms)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(2)
        h.tick(ms = 2_000)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(2)
    }

    @Test
    fun partialSwipeThatBouncesBackDoesNotCount() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.item(INSTAGRAM, 1, ms = 1_000)
        h.item(INSTAGRAM, 0, ms = 1_100)
        h.tick(ms = 2_000)
        h.tick(ms = 3_000)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(1)
    }

    @Test
    fun fullSwipeForwardCountsPlusOne() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.watchNext(INSTAGRAM)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(2)
        assertThat(h.counted().map { it.index }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun scrollBackDoesNotRecount() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.watchNext(INSTAGRAM, 2)
        h.swipeTo(INSTAGRAM, 1)
        h.swipeTo(INSTAGRAM, 0)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(3)
    }

    @Test
    fun scrollBackThenForwardToSeenVideoDoesNotRecount() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.watchNext(INSTAGRAM, 2)
        h.swipeTo(INSTAGRAM, 1)
        h.swipeTo(INSTAGRAM, 2)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(3)
    }

    @Test
    fun scrollBackThenPastHighestCountsOnlyNewVideo() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.watchNext(INSTAGRAM, 2)
        h.swipeTo(INSTAGRAM, 1)
        h.swipeTo(INSTAGRAM, 2)
        h.swipeTo(INSTAGRAM, 3)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(4)
        assertThat(h.counted().map { it.index }).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun fastFlingSkippingIndicesCountsOncePerSettledStop() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.item(INSTAGRAM, 1, ms = 1_000)
        h.item(INSTAGRAM, 2, ms = 1_050)
        h.item(INSTAGRAM, 3, ms = 1_100)
        h.tick(ms = 1_600)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(2)
        assertThat(h.counted().last().index).isEqualTo(3)
        h.swipeTo(INSTAGRAM, 4)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(3)
    }

    @Test
    fun tickAloneDoesNotCount() {
        val h = MachineHarness()
        for (ms in 0L..10_000L step 1_000) h.tick(ms = ms)
        assertThat(h.counted()).isEmpty()

        h.enter(INSTAGRAM, ms = 11_000)
        for (ms in 12_000L..30_000L step 1_000) h.tick(ms = ms)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(1)
    }

    @Test
    fun contentChangesWithoutIndexChange_likeCommentPause_doNotCount() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.watchNext(INSTAGRAM)
        // Liking, opening comments, pausing: the app re-reports the same item over and over.
        repeat(20) { h.item(INSTAGRAM, 1, ms = h.t + 700) }
        h.tick(ms = h.t + 5_000)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(2)
    }

    @Test
    fun itemChangedForOtherAppWhileOnScreenIsIgnored() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.item(YOUTUBE, 5, ms = 1_000)
        h.tick(ms = 2_000)
        assertThat(h.state.countToday(YOUTUBE)).isEqualTo(0)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(1)
    }

    @Test
    fun itemChangedWhenNotOnShortVideoScreenIsIgnored() {
        val h = MachineHarness()
        h.item(INSTAGRAM, 1, ms = 0)
        h.tick(ms = 1_000)
        assertThat(h.counted()).isEmpty()
    }

    @Test
    fun countsArePerApp() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.watchNext(INSTAGRAM)
        h.open(YOUTUBE, ms = h.t + 1_000)
        assertThat(h.state.countToday(INSTAGRAM)).isEqualTo(2)
        assertThat(h.state.countToday(YOUTUBE)).isEqualTo(1)
        assertThat(h.state.totalToday()).isEqualTo(3)
    }

    @Test
    fun countedEffectCarriesSessionIdIndexAndRunningTotal() {
        val h = MachineHarness()
        h.open(INSTAGRAM, ms = 0)
        h.item(INSTAGRAM, 1, ms = 1_000)
        h.tick(ms = 1_500)
        val second = h.counted().last()
        assertThat(second.app).isEqualTo(INSTAGRAM)
        assertThat(second.day).isEqualTo(DAY_1)
        assertThat(second.index).isEqualTo(1)
        assertThat(second.countToday).isEqualTo(2)
        // The timestamp is when the video came into view, not when the settle window closed.
        assertThat(second.at).isEqualTo(T0.plusMillis(1_000))
        assertThat(second.sessionId).isEqualTo(h.counted().first().sessionId)
    }
}
