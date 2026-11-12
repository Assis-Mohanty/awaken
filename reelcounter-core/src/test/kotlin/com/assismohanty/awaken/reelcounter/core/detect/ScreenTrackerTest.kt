package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.SNAPCHAT as SNAP
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.YOUTUBE as YT
import com.assismohanty.awaken.reelcounter.core.count.CounterEvent
import com.assismohanty.awaken.reelcounter.core.count.CounterMachine
import com.assismohanty.awaken.reelcounter.core.count.CounterState
import com.assismohanty.awaken.reelcounter.core.model.Bounds
import com.assismohanty.awaken.reelcounter.core.model.ScrollSignal
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ScreenTrackerTest {
    private val launcherPkg = "com.android.launcher"
    private val t0 = Instant.parse("2026-11-01T10:00:00Z")
    private fun at(ms: Long) = t0.plusMillis(ms)

    private val config = detectorConfig(
        appDetector(IG, true, rule(IG_PAGER, ItemSignal.PAGER_PAGE, sources = listOf(IG_PAGER))),
        appDetector(YT, true, rule(YT_LIST, ItemSignal.SCROLL_INDEX, sources = listOf(YT_LIST))),
        appDetector(SNAP, true, rule(SNAP_CONTAINER, ItemSignal.CHILD_SWAP)),
    )

    private fun tracker() = ScreenTracker(ScreenDetector(config))

    private fun ytScreen() = source(
        node("root", children = listOf(node("l", id = YT_LIST, scrollable = true, children = listOf(node("y1"))))),
        YT_PKG,
    )

    private fun snapScreen(child: String) = source(
        node("root", children = listOf(node("s", id = SNAP_CONTAINER, children = listOf(node(child))))),
        SNAP_PKG,
    )

    private fun scroll(src: String, from: Int, to: Int) = ScrollSignal(src, from, to, -1, 0)

    private fun TrackerOutput.assertEvents(vararg expected: CounterEvent) =
        assertThat(events).containsExactlyElementsIn(expected).inOrder()

    private fun ScreenTracker.enterIg(ms: Long = 0) =
        onSignal(RawSignal.WindowStateChanged(IG_PKG, igPagerScreen(), at(ms)))

    @Test
    fun windowStateChangeOnContainerEmitsScreenEntered() {
        tracker().enterIg().assertEvents(CounterEvent.ScreenEntered(IG, at(0), 0))
    }

    @Test
    fun windowStateChangeAwayEmitsScreenLeft() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.WindowStateChanged(launcherPkg, plainScreen(launcherPkg), at(1_000)))
            .assertEvents(CounterEvent.ScreenLeft(IG, at(1_000)))
    }

    @Test
    fun contentChangeWithin300msIsThrottledWithRecheck() {
        val t = tracker()
        t.enterIg()
        val out = t.onSignal(RawSignal.WindowContentChanged(IG_PKG, plainScreen(IG_PKG), at(100)))
        assertThat(out.events).isEmpty()
        assertThat(out.recheckAt).isEqualTo(at(300))
    }

    @Test
    fun contentChangeAfter300msEvaluates() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.WindowContentChanged(launcherPkg, plainScreen(launcherPkg), at(300)))
            .assertEvents(CounterEvent.ScreenLeft(IG, at(300)))
    }

    @Test
    fun windowStateChangeIsNeverThrottled() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.WindowStateChanged(launcherPkg, plainScreen(launcherPkg), at(10)))
            .assertEvents(CounterEvent.ScreenLeft(IG, at(10)))
    }

    @Test
    fun pagerScrollEmitsItemChanged() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.ViewScrolled(IG_PKG, scroll(IG_PAGER, 1, 1), igPagerScreen(), at(1_000)))
            .assertEvents(CounterEvent.ItemChanged(IG, 1, at(1_000)))
    }

    @Test
    fun scrollFromOtherSourceIgnored() {
        val t = tracker()
        t.enterIg()
        val out = t.onSignal(RawSignal.ViewScrolled(IG_PKG, scroll("x:id/y", 1, 1), igPagerScreen(), at(1_000)))
        assertThat(out.events).isEmpty()
    }

    @Test
    fun scrollWhileNotActiveEvaluatesThenEntersThenItem() {
        tracker().onSignal(RawSignal.ViewScrolled(IG_PKG, scroll(IG_PAGER, 2, 2), igPagerScreen(), at(0)))
            .assertEvents(CounterEvent.ScreenEntered(IG, at(0), 0), CounterEvent.ItemChanged(IG, 2, at(0)))
    }

    @Test
    fun probeWithOtherForegroundEmitsScreenLeftThenTick() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.Probe("com.android.launcher", null, at(1_000)))
            .assertEvents(CounterEvent.ScreenLeft(IG, at(1_000)), CounterEvent.Tick(at(1_000)))
    }

    @Test
    fun probeWithNullForegroundEmitsScreenLeft() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.Probe(null, null, at(1_000)))
            .assertEvents(CounterEvent.ScreenLeft(IG, at(1_000)), CounterEvent.Tick(at(1_000)))
    }

    @Test
    fun probeAlwaysEndsWithTick() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.Probe(IG_PKG, igPagerScreen(), at(1_000))).assertEvents(CounterEvent.Tick(at(1_000)))
        val idle = tracker().onSignal(RawSignal.Probe(null, null, at(0)))
        assertThat(idle.events.last()).isEqualTo(CounterEvent.Tick(at(0)))
    }

    @Test
    fun screenOffEmitsScreenLeft() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.ScreenOff(at(1_000))).assertEvents(CounterEvent.ScreenLeft(IG, at(1_000)))
    }

    @Test
    fun screenOnOnShortVideoReenters() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.ScreenOff(at(1_000)))
        t.onSignal(RawSignal.ScreenOn(IG_PKG, igPagerScreen(), at(60_000)))
            .assertEvents(CounterEvent.ScreenEntered(IG, at(60_000), 0))
    }

    @Test
    fun childSwapSignatureChangeEmitsItemChanged() {
        val t = tracker()
        t.onSignal(RawSignal.WindowStateChanged(SNAP_PKG, snapScreen("a"), at(0)))
            .assertEvents(CounterEvent.ScreenEntered(SNAP, at(0), 0))
        t.onSignal(RawSignal.WindowContentChanged(SNAP_PKG, snapScreen("b"), at(1_000)))
            .assertEvents(CounterEvent.ItemChanged(SNAP, 1, at(1_000)))
    }

    @Test
    fun switchBetweenSupportedAppsEmitsScreenEnteredForNew() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.WindowStateChanged(YT_PKG, ytScreen(), at(1_000)))
            .assertEvents(CounterEvent.ScreenEntered(YT, at(1_000), 0))
    }

    @Test
    fun indexPersistsAcrossVisits() {
        val t = tracker()
        t.enterIg()
        t.onSignal(RawSignal.ViewScrolled(IG_PKG, scroll(IG_PAGER, 4, 4), igPagerScreen(), at(1_000)))
        t.onSignal(RawSignal.WindowStateChanged(launcherPkg, plainScreen(launcherPkg), at(2_000)))
        t.onSignal(RawSignal.WindowStateChanged(IG_PKG, igPagerScreen(), at(3_000)))
            .assertEvents(CounterEvent.ScreenEntered(IG, at(3_000), 4))
    }

    // An Instagram-like reel: the container is the item, so its own key tells reels apart.
    private val reelConfig = detectorConfig(
        appDetector(IG, true, ScreenRule(listOf(IG_REEL), signal = ItemSignal.CHILD_SWAP, itemIsContainer = true)),
        appDetector(YT, true, rule(YT_LIST, ItemSignal.SCROLL_INDEX, sources = listOf(YT_LIST))),
    )

    private fun reel(key: String) = source(node("root", children = listOf(node(key, id = IG_REEL))), IG_PKG)

    /** Mid-swipe: two reels at about half the screen each, so neither reaches the coverage rule. */
    private fun midSwipe() = source(
        node(
            "root",
            children = listOf(
                node("a", id = IG_REEL, bounds = Bounds(0, -1200, 1080, 1200)),
                node("b", id = IG_REEL, bounds = Bounds(0, 1200, 1080, 3600)),
            ),
        ),
        IG_PKG,
    )

    @Test
    fun aMissedLookMidSwipeKeepsTheVisitAndTheNextReelCountsOnce() {
        val t = ScreenTracker(ScreenDetector(reelConfig))
        val events = mutableListOf<CounterEvent>()
        events += t.onSignal(RawSignal.WindowStateChanged(IG_PKG, reel("a"), at(0))).events
        val miss = t.onSignal(RawSignal.WindowContentChanged(IG_PKG, midSwipe(), at(400)))
        assertThat(miss.events).isEmpty()
        assertThat(t.activeApp).isEqualTo(IG)
        assertThat(t.reelOnScreen).isNull()
        val next = t.onSignal(RawSignal.WindowContentChanged(IG_PKG, reel("b"), at(800)))
        next.assertEvents(CounterEvent.ItemChanged(IG, 1, at(800)))
        events += next.events
        events += t.onSignal(RawSignal.Probe(IG_PKG, reel("b"), at(1_500))).events

        val machine = CounterMachine()
        var state = CounterState.initial(java.time.LocalDate.of(2026, 11, 1), emptyMap(), emptyMap(), emptyMap())
        for (e in events) state = machine.reduce(state, e).state
        // Reel A on entry, reel B once.
        assertThat(state.countToday(IG)).isEqualTo(2)
    }

    @Test
    fun aMissThatLastsLeavesTheScreen() {
        val t = ScreenTracker(ScreenDetector(reelConfig))
        t.onSignal(RawSignal.WindowStateChanged(IG_PKG, reel("a"), at(0)))
        t.onSignal(RawSignal.Probe(IG_PKG, plainScreen(IG_PKG), at(1_000))).assertEvents(CounterEvent.Tick(at(1_000)))
        t.onSignal(RawSignal.Probe(IG_PKG, plainScreen(IG_PKG), at(2_000)))
            .assertEvents(CounterEvent.ScreenLeft(IG, at(2_000)), CounterEvent.Tick(at(2_000)))
    }

    @Test
    fun anotherAppInFrontLeavesAtOnceEvenDuringAMiss() {
        val t = ScreenTracker(ScreenDetector(reelConfig))
        t.onSignal(RawSignal.WindowStateChanged(IG_PKG, reel("a"), at(0)))
        t.onSignal(RawSignal.WindowContentChanged(IG_PKG, midSwipe(), at(400)))
        t.onSignal(RawSignal.WindowStateChanged(launcherPkg, plainScreen(launcherPkg), at(500)))
            .assertEvents(CounterEvent.ScreenLeft(IG, at(500)))
    }

    @Test
    fun aScrollIndexListAdoptsItsFirstIndexOnReEntry() {
        val t = ScreenTracker(ScreenDetector(reelConfig))
        t.onSignal(RawSignal.WindowStateChanged(YT_PKG, ytScreen(), at(0)))
        t.onSignal(RawSignal.ViewScrolled(YT_PKG, scroll(YT_LIST, 40, 40), ytScreen(), at(500)))
            .assertEvents(CounterEvent.ItemAdopted(YT, 40, at(500)))
        t.onSignal(RawSignal.ViewScrolled(YT_PKG, scroll(YT_LIST, 41, 41), ytScreen(), at(1_000)))
            .assertEvents(CounterEvent.ItemChanged(YT, 41, at(1_000)))
        t.onSignal(RawSignal.WindowStateChanged(launcherPkg, plainScreen(launcherPkg), at(2_000)))
        t.onSignal(RawSignal.WindowStateChanged(YT_PKG, ytScreen(), at(3_000)))
        // YouTube's list restarted at 0: the old 41 must not stick.
        t.onSignal(RawSignal.ViewScrolled(YT_PKG, scroll(YT_LIST, 0, 0), ytScreen(), at(3_500)))
            .assertEvents(CounterEvent.ItemAdopted(YT, 0, at(3_500)))
        t.onSignal(RawSignal.ViewScrolled(YT_PKG, scroll(YT_LIST, 1, 1), ytScreen(), at(4_000)))
            .assertEvents(CounterEvent.ItemChanged(YT, 1, at(4_000)))
    }
}
