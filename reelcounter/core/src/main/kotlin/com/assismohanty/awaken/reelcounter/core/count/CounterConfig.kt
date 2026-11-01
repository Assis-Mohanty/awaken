package com.assismohanty.awaken.reelcounter.core.count

data class CounterConfig(
    /** Debounce: an index must stay unchanged this long before it counts. */
    val settleMillis: Long = 350,
    /** Leaving and returning within this window resumes the same session. */
    val resumeGraceMillis: Long = 600_000,
    /** Time added per event is clamped to this (screen off, doze). */
    val maxTickGapMillis: Long = 5_000,
    val snoozeSize: Int = 5,
    val maxSnoozes: Int = 2,
)
