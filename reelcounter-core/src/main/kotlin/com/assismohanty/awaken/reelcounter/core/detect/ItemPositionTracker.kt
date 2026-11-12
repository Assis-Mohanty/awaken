package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.model.ScrollSignal
import java.time.Duration
import java.time.Instant

/** Turns pager / scroll / child-swap signals into an item index. One per app, lives across visits. */
class ItemPositionTracker(private val childSwapWindowMillis: Long = 1_000) {
    var index: Int = 0
        private set

    private var rule: ScreenRule? = null
    private var lastSignature: String? = null
    private var lastDeltaY: Int = 0
    private var lastDeltaAt: Instant? = null

    /** Which way the swipe in progress goes (+1 forward, -1 back), read from where the pages sit, and when it was read. */
    private var pagesDirection = 0
    private var pagesDirectionAt: Instant? = null

    /** A scroll-index list keeps no numbering across visits (YouTube's restarts at 0): the next scroll's index is adopted. */
    var adopting: Boolean = false
        private set

    /** The list tells items apart by which page view is on screen (YouTube Shorts, Instagram, Snapchat). */
    val childSwap: Boolean get() = rule?.signal == ItemSignal.CHILD_SWAP

    fun setRule(rule: ScreenRule) {
        this.rule = rule
    }

    /** Forgets the child signature so the next one only seeds. Called when a visit starts. */
    fun resetSignature() {
        lastSignature = null
        lastDeltaAt = null
        pagesDirection = 0
        pagesDirectionAt = null
    }

    /**
     * Two pages on screen at once is a swipe in progress. The page we were on is either the upper one (the next page is
     * rising from below: forward) or the lower one (the previous page is coming down from above: back). Scroll amounts
     * cannot tell, because apps report them in either sign for the same swipe.
     */
    fun onPages(pages: List<PageHint>, at: Instant) {
        if (rule?.signal != ItemSignal.CHILD_SWAP || pages.size < 2) return
        val current = lastSignature ?: return
        val ordered = pages.sortedBy { it.top }
        val direction = when (current) {
            ordered.first().key -> 1
            ordered.last().key -> -1
            else -> return
        }
        pagesDirection = direction
        pagesDirectionAt = at
    }

    /** Called when a visit starts. */
    fun startVisit() {
        adopting = rule?.signal == ItemSignal.SCROLL_INDEX
    }

    fun onScroll(scroll: ScrollSignal, at: Instant): Int? {
        val r = rule ?: return null
        if (r.signalSourceIds.isNotEmpty() && scroll.sourceViewId !in r.signalSourceIds) return null
        if (r.signal == ItemSignal.CHILD_SWAP) {
            lastDeltaY = scroll.scrollDeltaY
            lastDeltaAt = at
            return null
        }
        if (scroll.fromIndex < 0) return null
        if (adopting) {
            adopting = false
            index = scroll.fromIndex
            return index
        }
        if (r.signal == ItemSignal.PAGER_PAGE && !(scroll.toIndex == scroll.fromIndex || scroll.toIndex < 0)) {
            return null
        }
        return update(scroll.fromIndex)
    }

    fun onChildSignature(signature: String?, at: Instant): Int? {
        if (rule?.signal != ItemSignal.CHILD_SWAP) return null
        if (signature == null || signature == lastSignature) return null
        val previous = lastSignature
        lastSignature = signature
        if (previous == null) return null
        val seenAt = pagesDirectionAt
        val fromPages = if (seenAt != null && Duration.between(seenAt, at).toMillis() in 0..childSwapWindowMillis) pagesDirection else 0
        pagesDirection = 0
        pagesDirectionAt = null
        val deltaAt = lastDeltaAt
        val backwards = if (fromPages != 0) {
            fromPages < 0
        } else {
            deltaAt != null &&
                Duration.between(deltaAt, at).toMillis() in 0..childSwapWindowMillis &&
                // -1 is what Android reports when a view never set the delta, so only a larger negative step means "back".
                lastDeltaY < -1
        }
        return update(maxOf(0, index + if (backwards) -1 else 1))
    }

    private fun update(newIndex: Int): Int? {
        if (newIndex == index) return null
        index = newIndex
        return newIndex
    }
}
