package com.assismohanty.awaken.engine.body

/** One exercise of a routine, as the duration estimate needs it. */
data class RoutineLine(val sets: Int, val restSec: Int?, val supersetWithNext: Boolean = false)

/**
 * How long a routine takes, for the Gym home and plan screens (GD §18.1, G20). Display only: it feeds no XP, cap or
 * rule. Each set takes [SET_WORK_SEC] plus the exercise's rest; a superset partner that is followed by another does not
 * rest, because rest comes after the last exercise of the group (GD §18.2).
 */
object RoutineMath {
    /** G20: seconds one set takes to perform. */
    const val SET_WORK_SEC = 40

    fun estimateMinutes(lines: List<RoutineLine>): Int {
        if (lines.isEmpty()) return 0
        val seconds = lines.sumOf { line ->
            val rest = if (line.supersetWithNext) 0 else RestRules.clamp(line.restSec) ?: 0
            line.sets.coerceAtLeast(0) * (SET_WORK_SEC + rest)
        }
        return (seconds + 30) / 60
    }
}
