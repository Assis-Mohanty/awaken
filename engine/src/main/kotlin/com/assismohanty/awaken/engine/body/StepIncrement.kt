package com.assismohanty.awaken.engine.body

/**
 * How far the weight stepper moves per tap (GD §18.2, G6). The exercise's own increment override (GD §16.6) wins over
 * this; the caller applies it. Pure.
 */
object StepIncrement {
    private const val DEFAULT_BARBELL_KG = 2.5
    private const val DEFAULT_BARBELL_LB = 5.0

    fun step(equipment: Equipment, unit: PlateUnit, smallestPlate: Double?): Double = when (equipment) {
        // A barbell steps by a pair of the smallest owned plate (one on each side).
        Equipment.BARBELL, Equipment.EZ_BAR ->
            smallestPlate?.takeIf { it > 0.0 }?.let { it * 2 } ?: if (unit == PlateUnit.KG) DEFAULT_BARBELL_KG else DEFAULT_BARBELL_LB
        Equipment.CABLE, Equipment.MACHINE -> if (unit == PlateUnit.KG) 5.0 else 10.0
        Equipment.KETTLEBELLS -> if (unit == PlateUnit.KG) 4.0 else 10.0
        Equipment.DUMBBELL -> if (unit == PlateUnit.KG) 2.5 else 5.0
        Equipment.BODY_ONLY, Equipment.BANDS, Equipment.MEDICINE_BALL, Equipment.EXERCISE_BALL, Equipment.FOAM_ROLL, Equipment.OTHER ->
            if (unit == PlateUnit.KG) 2.5 else 5.0
    }

    /** The step for an exercise given the hunter's plate setup in [unit]. */
    fun step(equipment: Equipment, unit: PlateUnit, setup: PlateSetup): Double = step(equipment, unit, setup.plates.minOrNull())
}
