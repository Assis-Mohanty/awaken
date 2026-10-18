package com.assismohanty.awaken.engine

import kotlin.math.pow

/** Level curve (GD §4.4). `cumulative(L)` = total XP required to *reach* level L; `cumulative(1) = 0`. */
object LevelCurve {
    const val MAX_LEVEL = 100

    /** `round(100 + 12 × L^1.6)` — XP to go from level [level] to level+1. */
    fun xpToNext(level: Int): Int {
        require(level >= 1) { "level must be >= 1" }
        return XpCalculator.roundHalfUp(100 + 12 * level.toDouble().pow(1.6))
    }

    /** Index L holds cumulative(L); index 0 is unused. Computed once. */
    private val cumulativeTable: LongArray = LongArray(MAX_LEVEL + 1).also { table ->
        for (level in 1 until MAX_LEVEL) table[level + 1] = table[level] + xpToNext(level)
    }

    fun cumulative(level: Int): Long {
        require(level in 1..MAX_LEVEL) { "level must be in 1..$MAX_LEVEL" }
        return cumulativeTable[level]
    }

    /** Largest L with `cumulative(L) ≤ totalXp`, capped at [MAX_LEVEL]. Negative totals clamp to level 1. */
    fun level(totalXp: Long): Int {
        // Binary search over the monotonic table.
        var lo = 1
        var hi = MAX_LEVEL
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cumulativeTable[mid] <= totalXp) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** XP progress within the current level, for the XP bar. At max level `needed` is 0. */
    fun progress(totalXp: Long): Progress {
        val level = level(totalXp)
        val into = (totalXp - cumulativeTable[level]).coerceAtLeast(0)
        val needed = if (level == MAX_LEVEL) 0L else xpToNext(level).toLong()
        return Progress(level, into, needed)
    }

    data class Progress(val level: Int, val xpIntoLevel: Long, val xpForLevel: Long)
}
