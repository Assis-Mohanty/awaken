package com.assismohanty.awaken.engine.body

/** An exercise's target rep range (GD §16.6). */
data class RepRange(val min: Int, val max: Int) {
    val isValid: Boolean get() = min in ProgressionRules.REP_BOUNDS && max in ProgressionRules.REP_BOUNDS && min <= max
}

/** What to try on set 1 of the next session. Never forced, never rewarded. */
sealed interface ProgressionHint {
    /** Lift [weightKg] for [reps] (one entry per working set). [weightKg] is 0 for an unweighted exercise. */
    data class Suggest(val weightKg: Double, val reps: List<Int>) : ProgressionHint

    /** An unweighted exercise whose every set reached the top of the range: no number to suggest. */
    data object AddWeightOrVariation : ProgressionHint
}

/** The working sets of a finished session: the done sets at its heaviest weight, in order. */
data class WorkingSets(val weightKg: Double, val reps: List<Int>)

/** Double progression (GD §16.6): add reps until the top of the range, then add weight and drop back to the bottom. Pure. */
object ProgressionRules {
    /** N14: the rep range every exercise starts with. */
    val DEFAULT_RANGE = RepRange(8, 12)

    /** Per-exercise rep range bounds. */
    val REP_BOUNDS = 1..30

    /** N15: the weight step for upper-body and lower-body exercises. */
    const val UPPER_INCREMENT_KG = 2.5
    const val LOWER_INCREMENT_KG = 5.0

    /** Per-exercise increment override bounds, in 0.5 kg steps. */
    const val MIN_INCREMENT_KG = 0.5
    const val MAX_INCREMENT_KG = 20.0
    const val INCREMENT_STEP_KG = 0.5

    fun defaultIncrement(primary: Set<Muscle>): Double = if (primary.any { it.isLowerBody }) LOWER_INCREMENT_KG else UPPER_INCREMENT_KG

    fun isValidIncrement(kg: Double): Boolean = kg in MIN_INCREMENT_KG..MAX_INCREMENT_KG && kg * 2 == Math.rint(kg * 2)

    /** The done sets at the heaviest weight of [lastSession] (a finished session's sets, in order); null when none is done. */
    fun workingSets(lastSession: List<LoggedSet>): WorkingSets? {
        val done = lastSession.filter { it.done && it.reps > 0 }
        if (done.isEmpty()) return null
        val heaviest = done.maxOf { it.weightKg }
        return WorkingSets(heaviest, done.filter { it.weightKg == heaviest }.map { it.reps })
    }

    /** The hint after [lastSession]; null when there is nothing to build on. */
    fun suggest(lastSession: List<LoggedSet>, range: RepRange, incrementKg: Double): ProgressionHint? {
        val working = workingSets(lastSession) ?: return null
        val allAtTop = working.reps.all { it >= range.max }
        return when {
            working.weightKg <= 0.0 && allAtTop -> ProgressionHint.AddWeightOrVariation
            allAtTop -> ProgressionHint.Suggest(working.weightKg + incrementKg, working.reps.map { range.min })
            else -> ProgressionHint.Suggest(working.weightKg, working.reps.map { minOf(it + 1, range.max) })
        }
    }
}
