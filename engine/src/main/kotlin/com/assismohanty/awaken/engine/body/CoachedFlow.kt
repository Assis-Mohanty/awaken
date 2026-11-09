package com.assismohanty.awaken.engine.body

/** One set row of the workout in progress, reduced to what the coached flow needs. */
data class FlowRow(
    val position: Int,
    val setIndex: Int,
    /** `LEFT` or `RIGHT` for the two steps of a one-sided set; null otherwise. */
    val side: String?,
    val done: Boolean,
    /** Exercises of one superset share a letter; null = not in a superset. */
    val superset: String?,
)

/** The set to do next: its exercise [position], its [setIndex] and, for a one-sided set, the [side]. */
data class FlowCursor(val position: Int, val setIndex: Int, val side: String?)

/**
 * Which set comes next in the coached workout and when a finished set starts the rest (GD §18.2). Exercises run in
 * order; the exercises of a superset take turns round by round (set 1 of each, then set 2 of each); the two sides of a
 * one-sided set follow each other. Rest starts after the last step of a round, never between superset partners or
 * between left and right. Pure.
 */
object CoachedFlow {
    private fun sideOrder(side: String?): Int = if (side == "RIGHT") 1 else 0

    /** Positions grouped in running order: one block per plain exercise, one per superset (contiguous exercises sharing a letter). */
    private fun blocks(rows: List<FlowRow>): List<List<Int>> {
        val positions = rows.map { it.position }.distinct().sorted()
        val letterOf = rows.groupBy { it.position }.mapValues { (_, r) -> r.firstNotNullOfOrNull { it.superset } }
        val blocks = mutableListOf<MutableList<Int>>()
        positions.forEach { p ->
            val letter = letterOf[p]
            val last = blocks.lastOrNull()
            if (letter != null && last != null && letterOf[last.last()] == letter) last += p else blocks += mutableListOf(p)
        }
        return blocks
    }

    /** The next set to do, or null when every set is done. */
    fun current(rows: List<FlowRow>): FlowCursor? {
        val block = blocks(rows).firstOrNull { b -> rows.any { it.position in b && !it.done } } ?: return null
        val next = rows.filter { it.position in block && !it.done }
            .minWith(compareBy<FlowRow>({ it.setIndex }, { block.indexOf(it.position) }, { sideOrder(it.side) }))
        return FlowCursor(next.position, next.setIndex, next.side)
    }

    /** Whether completing [row] starts the rest: not while its other side or a superset partner of this round is still to do. */
    fun restAfter(rows: List<FlowRow>, row: FlowRow): Boolean {
        val others = rows.filter { it !== row && !(it.position == row.position && it.setIndex == row.setIndex && it.side == row.side) }
        if (row.side != null && others.any { it.position == row.position && it.setIndex == row.setIndex && it.side != row.side && !it.done }) return false
        val letter = row.superset ?: return true
        return others.none { it.superset == letter && it.position != row.position && it.setIndex == row.setIndex && !it.done }
    }

    /** "Set k of n" for an exercise: [setIndex] counted among that exercise's distinct set indexes. */
    fun setNumber(rows: List<FlowRow>, position: Int, setIndex: Int): Pair<Int, Int> {
        val indexes = rows.filter { it.position == position }.map { it.setIndex }.distinct().sorted()
        return (indexes.indexOf(setIndex) + 1) to indexes.size
    }

    /** True when no set is left to do. */
    fun finished(rows: List<FlowRow>): Boolean = rows.isNotEmpty() && rows.all { it.done }

    /** True when the next set to complete is the very last one: Finish becomes the main action. */
    fun onLastSet(rows: List<FlowRow>): Boolean = rows.count { !it.done } == 1
}
