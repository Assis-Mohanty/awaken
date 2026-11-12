package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.model.ScrollSignal
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ItemPositionTrackerTest {
    private val t0 = Instant.parse("2026-11-01T10:00:00Z")
    private val pagerRule = rule(IG_PAGER, ItemSignal.PAGER_PAGE, sources = listOf(IG_PAGER))
    private val listRule = rule(YT_LIST, ItemSignal.SCROLL_INDEX, sources = listOf(YT_LIST))
    private val swapRule = rule(SNAP_CONTAINER, ItemSignal.CHILD_SWAP)

    private fun scroll(src: String?, from: Int, to: Int, dy: Int = 0) = ScrollSignal(src, from, to, -1, dy)

    private fun tracker(rule: ScreenRule) = ItemPositionTracker().also { it.setRule(rule) }

    @Test
    fun pagerSettledFromEqualsToBecomesIndex() {
        assertThat(tracker(pagerRule).onScroll(scroll(IG_PAGER, 3, 3), t0)).isEqualTo(3)
    }

    @Test
    fun pagerMidSwipeFromDiffersFromToIgnored() {
        assertThat(tracker(pagerRule).onScroll(scroll(IG_PAGER, 3, 4), t0)).isNull()
    }

    @Test
    fun pagerToIndexMissingAccepted() {
        assertThat(tracker(pagerRule).onScroll(scroll(IG_PAGER, 5, -1), t0)).isEqualTo(5)
    }

    @Test
    fun scrollIndexUsesFromIndex() {
        assertThat(tracker(listRule).onScroll(scroll(YT_LIST, 4, 6), t0)).isEqualTo(4)
    }

    @Test
    fun scrollFromOtherSourceIdIgnored() {
        assertThat(tracker(listRule).onScroll(scroll("other:id/x", 4, 4), t0)).isNull()
    }

    @Test
    fun emptySourceFilterAcceptsAnySource() {
        val open = rule(YT_LIST, ItemSignal.SCROLL_INDEX)
        assertThat(tracker(open).onScroll(scroll("whatever:id/y", 2, 2), t0)).isEqualTo(2)
    }

    @Test
    fun negativeFromIndexIgnored() {
        assertThat(tracker(listRule).onScroll(scroll(YT_LIST, -1, -1), t0)).isNull()
    }

    @Test
    fun sameIndexReturnsNull() {
        val t = tracker(pagerRule)
        assertThat(t.onScroll(scroll(IG_PAGER, 2, 2), t0)).isEqualTo(2)
        assertThat(t.onScroll(scroll(IG_PAGER, 2, 2), t0.plusMillis(10))).isNull()
    }

    @Test
    fun childSwapFirstSignatureSeedsWithoutChange() {
        val t = tracker(swapRule)
        assertThat(t.onChildSignature("a", t0)).isNull()
        assertThat(t.index).isEqualTo(0)
    }

    @Test
    fun childSwapForwardAndBackward() {
        val t = tracker(swapRule)
        t.onChildSignature("a", t0)
        assertThat(t.onChildSignature("b", t0.plusMillis(100))).isEqualTo(1)

        val s = t0.plusMillis(5_000)
        t.onScroll(scroll(null, -1, -1, dy = -500), s)
        assertThat(t.onChildSignature("c", s.plusMillis(200))).isEqualTo(0)

        val s2 = t0.plusMillis(10_000)
        t.onScroll(scroll(null, -1, -1, dy = -500), s2)
        assertThat(t.onChildSignature("d", s2.plusMillis(1_500))).isEqualTo(1)
    }

    @Test
    fun childSwapNeverBelowZeroAndSameSignatureNull() {
        val t = tracker(swapRule)
        t.onChildSignature("a", t0)
        t.onScroll(scroll(null, -1, -1, dy = -500), t0.plusMillis(10))
        assertThat(t.onChildSignature("b", t0.plusMillis(100))).isNull()
        assertThat(t.index).isEqualTo(0)
        assertThat(t.onChildSignature("b", t0.plusMillis(200))).isNull()
    }

    private fun pages(vararg p: Pair<String, Int>) = p.map { PageHint(it.first, it.second) }

    /** A YouTube-like list: one full page at a time, the next one rises from below or the previous comes down from above. */
    @Test
    fun childSwapReadsTheDirectionFromWhichPageIsOnTop() {
        val t = tracker(swapRule)
        t.onChildSignature("a", t0)
        // Forward: our page (a) is the upper one, the next (b) rises from below.
        t.onPages(pages("a" to -500, "b" to 1700), t0.plusMillis(100))
        assertThat(t.onChildSignature("b", t0.plusMillis(300))).isEqualTo(1)
        t.onPages(pages("b" to -300, "c" to 1900), t0.plusMillis(1_000))
        assertThat(t.onChildSignature("c", t0.plusMillis(1_200))).isEqualTo(2)
        // Back: our page (c) is the lower one, the previous (b) comes down from above.
        t.onPages(pages("b" to -1800, "c" to 400), t0.plusMillis(2_000))
        assertThat(t.onChildSignature("b", t0.plusMillis(2_200))).isEqualTo(1)
    }

    @Test
    fun childSwapDirectionIgnoresScrollAmountsThatDisagree() {
        val t = tracker(swapRule)
        t.onChildSignature("a", t0)
        // The app reported a large negative step for a forward swipe: the page positions win.
        t.onScroll(scroll(SNAP_CONTAINER, -1, -1, dy = -1004), t0.plusMillis(50))
        t.onPages(pages("a" to -400, "b" to 1800), t0.plusMillis(100))
        assertThat(t.onChildSignature("b", t0.plusMillis(300))).isEqualTo(1)
    }

    @Test
    fun childSwapDirectionIsOnlyTrustedForAMoment() {
        val t = tracker(swapRule)
        t.onChildSignature("a", t0)
        // A peek that snapped back, then much later a swipe whose pages were never seen: forward, the safe default.
        t.onPages(pages("a" to -400, "b" to 1800), t0.plusMillis(100))
        t.onPages(pages("b" to -1800, "a" to 400), t0.plusMillis(200))
        assertThat(t.onChildSignature("c", t0.plusMillis(5_000))).isEqualTo(1)
    }

    @Test
    fun childSwapTreatsAnUnsetDeltaAsForward() {
        val t = tracker(swapRule)
        t.onChildSignature("a", t0)
        t.onChildSignature("b", t0.plusMillis(100))
        // Android reports -1 when a view never set the delta.
        t.onScroll(scroll(SNAP_CONTAINER, -1, -1, dy = -1), t0.plusMillis(200))
        assertThat(t.onChildSignature("c", t0.plusMillis(300))).isEqualTo(2)
    }

    @Test
    fun childSwapIgnoresAScrollFromAnotherView() {
        val t = tracker(rule(SNAP_CONTAINER, ItemSignal.CHILD_SWAP, sources = listOf(SNAP_CONTAINER)))
        t.onChildSignature("a", t0)
        t.onChildSignature("b", t0.plusMillis(100))
        t.onScroll(scroll("com.snapchat.android:id/comments", -1, -1, dy = -500), t0.plusMillis(200))
        assertThat(t.onChildSignature("c", t0.plusMillis(300))).isEqualTo(2)
    }
}
