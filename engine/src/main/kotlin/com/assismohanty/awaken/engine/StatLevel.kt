package com.assismohanty.awaken.engine

/** Stat level (GD §3): `floor(sqrt(statXp / 40)) + 1`, computed in integers to avoid floating-point edge errors. */
object StatLevel {
    const val XP_DIVISOR = 40L
    const val MIN_RADAR_MAX = 20
    const val RADAR_HEADROOM = 5

    fun level(statXp: Long): Int {
        if (statXp <= 0) return 1
        val quotient = statXp / XP_DIVISOR // floor(sqrt(x / 40)) == floor(sqrt(floor(x / 40))) for x ≥ 0
        var root = kotlin.math.sqrt(quotient.toDouble()).toLong()
        while (root * root > quotient) root--
        while ((root + 1) * (root + 1) <= quotient) root++
        return (root + 1).toInt()
    }

    /** Minimum stat XP needed to reach [level]. */
    fun xpForLevel(level: Int): Long {
        require(level >= 1) { "level must be >= 1" }
        val n = (level - 1).toLong()
        return n * n * XP_DIVISOR
    }

    /** Radar axis max = max(20, highestStatLevel + 5). */
    fun radarMax(highestStatLevel: Int): Int = maxOf(MIN_RADAR_MAX, highestStatLevel + RADAR_HEADROOM)
}
