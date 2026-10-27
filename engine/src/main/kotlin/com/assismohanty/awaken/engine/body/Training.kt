package com.assismohanty.awaken.engine.body

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The 17 muscle groups of the bundled exercise catalog. [key] is the catalog's spelling. */
enum class Muscle(val key: String) {
    ABDOMINALS("abdominals"),
    ABDUCTORS("abductors"),
    ADDUCTORS("adductors"),
    BICEPS("biceps"),
    CALVES("calves"),
    CHEST("chest"),
    FOREARMS("forearms"),
    GLUTES("glutes"),
    HAMSTRINGS("hamstrings"),
    LATS("lats"),
    LOWER_BACK("lower back"),
    MIDDLE_BACK("middle back"),
    NECK("neck"),
    QUADRICEPS("quadriceps"),
    SHOULDERS("shoulders"),
    TRAPS("traps"),
    TRICEPS("triceps"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): Muscle? = byKey[key]
    }
}

/** Equipment of the bundled exercise catalog; anything unknown or missing is [OTHER]. */
enum class Equipment(val key: String) {
    BODY_ONLY("body only"),
    DUMBBELL("dumbbell"),
    BARBELL("barbell"),
    EZ_BAR("e-z curl bar"),
    KETTLEBELLS("kettlebells"),
    CABLE("cable"),
    MACHINE("machine"),
    BANDS("bands"),
    MEDICINE_BALL("medicine ball"),
    EXERCISE_BALL("exercise ball"),
    FOAM_ROLL("foam roll"),
    OTHER("other"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String?): Equipment = key?.let(byKey::get) ?: OTHER
    }
}

/** One logged set. Only [done] sets count toward volume, PRs and muscle load. */
data class LoggedSet(val weightKg: Double, val reps: Int, val done: Boolean = true)

/** The muscles an exercise works, for muscle load. */
data class MuscleTargets(val primary: Set<Muscle>, val secondary: Set<Muscle>)

/** Volume, estimated 1RM and weekly muscle load (GD §13.2). */
object TrainingMath {
    // Input sanity for set rows (not game numbers).
    const val MAX_WEIGHT_KG = 1_000.0
    const val MAX_REPS = 500

    /** Epley only holds for low reps: sets above this don't produce an estimate. */
    const val E1RM_MAX_REPS = 12

    private const val EPLEY_DIVISOR = 30.0
    private const val PRIMARY_LOAD = 1.0
    private const val SECONDARY_LOAD = 0.5

    /** Upper bound of each weekly muscle-load band 1..3; above the last is band 4, zero is band 0. */
    private val BAND_LIMITS = doubleArrayOf(5.0, 10.0, 15.0)
    const val BAND_COUNT = 5

    fun volume(sets: Iterable<LoggedSet>): Double = sets.filter { it.done }.sumOf { it.weightKg * it.reps }

    /** Estimated one-rep max: the weight itself for a single, Epley for 2..12 reps, null otherwise or unweighted. */
    fun estimatedOneRepMax(set: LoggedSet): Double? = when {
        !set.done || set.weightKg <= 0.0 -> null
        set.reps == 1 -> set.weightKg
        set.reps in 2..E1RM_MAX_REPS -> set.weightKg * (1.0 + set.reps / EPLEY_DIVISOR)
        else -> null
    }

    /** The set with the highest estimated 1RM, or null when none qualifies. */
    fun bestSet(sets: Iterable<LoggedSet>): LoggedSet? =
        sets.mapNotNull { set -> estimatedOneRepMax(set)?.let { set to it } }.maxByOrNull { it.second }?.first

    /** Sets per muscle: each done set adds 1 to its primary muscles and 0.5 to its secondary ones. */
    fun muscleLoad(work: Iterable<Pair<MuscleTargets, LoggedSet>>): Map<Muscle, Double> {
        val load = mutableMapOf<Muscle, Double>()
        work.filter { (_, set) -> set.done }.forEach { (targets, _) ->
            targets.primary.forEach { load.merge(it, PRIMARY_LOAD, Double::plus) }
            (targets.secondary - targets.primary).forEach { load.merge(it, SECONDARY_LOAD, Double::plus) }
        }
        return load
    }

    /**
     * Volume (kg × reps) per muscle for the body-map sheet: each done set counts in full for every muscle it works,
     * primary or secondary (ENGINE_DECISIONS #101). Display only; no reward reads it.
     */
    fun muscleVolume(work: Iterable<Pair<MuscleTargets, LoggedSet>>): Map<Muscle, Double> {
        val volume = mutableMapOf<Muscle, Double>()
        work.filter { (_, set) -> set.done }.forEach { (targets, set) ->
            (targets.primary + targets.secondary).forEach { volume.merge(it, set.weightKg * set.reps, Double::plus) }
        }
        return volume
    }

    /** Body-map band 0..4 for a week's sets on one muscle: 0 · ≤5 · ≤10 · ≤15 · more. */
    fun band(sets: Double): Int = when {
        sets <= 0.0 -> 0
        else -> 1 + BAND_LIMITS.count { sets > it }
    }

    /** Monday of the week holding [date]: the body map and weekly snapshot run Monday → Sunday. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
