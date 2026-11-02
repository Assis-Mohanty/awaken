package com.assismohanty.awaken.engine.body

enum class PlateUnit { KG, LB }

/** A bar and the plate sizes owned (any number of each, GD §16.8 N16). */
data class PlateSetup(val barWeight: Double, val plates: List<Double>)

sealed interface PlatePlan {
    /** The target is under the bar's own weight. */
    data class BelowBar(val barWeight: Double) : PlatePlan

    /** The exact load can be built: [perSide] plates, heaviest first. */
    data class Exact(val perSide: List<Double>) : PlatePlan

    /** The nearest buildable load below the target; [shortBy] is the gap. */
    data class Short(val perSide: List<Double>, val built: Double, val shortBy: Double) : PlatePlan
}

/** Plate calculator (GD §16.8). Works in hundredths so 1.25 and 2.5 plates never drift. Pure. */
object PlateMath {
    private const val SCALE = 100.0

    /** N17 */
    fun defaults(unit: PlateUnit): PlateSetup = when (unit) {
        PlateUnit.KG -> PlateSetup(20.0, listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25))
        PlateUnit.LB -> PlateSetup(45.0, listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5))
    }

    private fun units(value: Double): Long = Math.round(value * SCALE)

    fun plan(target: Double, setup: PlateSetup): PlatePlan {
        val bar = units(setup.barWeight)
        val total = units(target)
        if (total < bar) return PlatePlan.BelowBar(setup.barWeight)
        var left = (total - bar) / 2
        val perSide = mutableListOf<Double>()
        setup.plates.filter { it > 0.0 }.sortedDescending().forEach { plate ->
            val size = units(plate)
            while (size > 0 && left >= size) {
                perSide += plate
                left -= size
            }
        }
        val built = bar + perSide.sumOf { units(it) } * 2
        if (built == total) return PlatePlan.Exact(perSide)
        return PlatePlan.Short(perSide, built / SCALE, (total - built) / SCALE)
    }
}
