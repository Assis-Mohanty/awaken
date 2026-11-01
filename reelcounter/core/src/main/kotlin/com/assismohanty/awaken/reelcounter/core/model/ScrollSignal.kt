package com.assismohanty.awaken.reelcounter.core.model

/** Abstract payload of a TYPE_VIEW_SCROLLED event. -1 / 0 mean "not reported". */
data class ScrollSignal(
    val sourceViewId: String?,
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    val scrollDeltaY: Int,
)
