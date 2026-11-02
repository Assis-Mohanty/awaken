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

    fun setRule(rule: ScreenRule) {
        this.rule = rule
    }

    /** Forgets the child signature so the next one only seeds. Called when a visit starts. */
    fun resetSignature() {
        lastSignature = null
        lastDeltaAt = null
    }

    fun onScroll(scroll: ScrollSignal, at: Instant): Int? {
        val r = rule ?: return null
        if (r.signal == ItemSignal.CHILD_SWAP) {
            lastDeltaY = scroll.scrollDeltaY
            lastDeltaAt = at
            return null
        }
        if (r.signalSourceIds.isNotEmpty() && scroll.sourceViewId !in r.signalSourceIds) return null
        if (scroll.fromIndex < 0) return null
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
        val deltaAt = lastDeltaAt
        val backwards = deltaAt != null &&
            Duration.between(deltaAt, at).toMillis() in 0..childSwapWindowMillis &&
            lastDeltaY < 0
        return update(maxOf(0, index + if (backwards) -1 else 1))
    }

    private fun update(newIndex: Int): Int? {
        if (newIndex == index) return null
        index = newIndex
        return newIndex
    }
}
