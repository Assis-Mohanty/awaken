package com.assismohanty.awaken.reelcounter.core.limits

/**
 * The one daily limit shown beside today's total: limits are set per app, so it is their sum over the apps that have
 * one, or null when no app has a limit.
 */
fun combinedDailyLimit(perAppLimits: Collection<Int>): Int? {
    val set = perAppLimits.filter { it > 0 }
    return if (set.isEmpty()) null else set.sumOf { it.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
