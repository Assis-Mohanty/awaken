package com.assismohanty.awaken.reelcounter.core.limit

object LimitPolicy {
    const val MIN_LIMIT = 5
    const val MAX_LIMIT = 500
    const val STEP = 5
    const val DEFAULT_LIMIT = 50

    fun allowance(limit: Int?, snoozesUsed: Int, snoozeSize: Int): Int? =
        limit?.let { it + snoozesUsed * snoozeSize }

    fun clamp(limit: Int): Int = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)
}
