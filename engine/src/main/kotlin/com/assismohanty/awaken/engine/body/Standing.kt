package com.assismohanty.awaken.engine.body

/** Where an exercise's best result stands (GD §18.3). Never a rank among users: a named population or a published standard. */
sealed interface Standing {
    /** Where the marker sits across the five equal gauge bands, 0..1. */
    val gauge: Float

    /** Squat, bench or deadlift against people who compete in raw powerlifting. [curve] is (ratio, relative density). */
    data class Percentile(
        val level: StrengthLevel,
        val percentile: Double,
        val ratio: Double,
        val quantiles: List<Double>,
        val curve: List<Pair<Double, Double>>,
        val population: String,
        val bodyWeightKg: Double,
    ) : Standing {
        override val gauge: Float get() = StandingRules.gaugePosition(percentile)
    }

    /** Reps or seconds against the US Army test standard: a level only, no percentile and no curve. */
    data class Standard(val level: StrengthLevel, val points: Int, val ageNotSet: Boolean) : Standing {
        override val gauge: Float get() = (level.ordinal + 0.5f) / StrengthLevel.entries.size
    }

    /** The exercise is benchmarked but body weight or sex is missing. */
    data object NeedsProfile : Standing {
        override val gauge: Float = 0f
    }
}

object StandingRules {
    private val BAND_STARTS = doubleArrayOf(1.0, 20.0, 50.0, 80.0, 95.0, 100.0)

    /** The percentile's place on a gauge of five equal bands: its level's band, and how far through that level's percentile range it is. */
    fun gaugePosition(percentile: Double): Float {
        val level = Benchmarks.level(percentile).ordinal
        val within = ((percentile - BAND_STARTS[level]) / (BAND_STARTS[level + 1] - BAND_STARTS[level])).coerceIn(0.0, 1.0)
        return ((level + within) / StrengthLevel.entries.size).toFloat()
    }

    /** The lift (kg, estimated 1RM) and percentile under a pointer dragged to [fraction] (0..1) along the curve. */
    fun atPointer(standing: Standing.Percentile, fraction: Float): Pair<Double, Double> {
        val low = standing.quantiles.first()
        val high = standing.quantiles.last()
        val ratio = low + (high - low) * fraction.coerceIn(0f, 1f)
        return ratio * standing.bodyWeightKg to Benchmarks.percentile(ratio, standing.quantiles)
    }

    /** Where the hunter's own ratio sits along the curve, 0..1. */
    fun markerFraction(standing: Standing.Percentile): Float {
        val low = standing.quantiles.first()
        val high = standing.quantiles.last()
        return ((standing.ratio - low) / (high - low)).toFloat().coerceIn(0f, 1f)
    }

    /** Which kind of result an exercise's benchmark reads: the lifts use the estimated 1RM, the standards use reps or seconds. */
    fun standing(
        data: BenchmarkData,
        exerciseId: String,
        bestE1rmKg: Double?,
        bestReps: Int?,
        bestSeconds: Int?,
        sex: String?,
        bodyWeightKg: Double?,
        age: Int?,
    ): Standing? {
        val key = data.keyFor(exerciseId) ?: return null
        val lift = data.percentile.any { it.key == key }
        if (lift) {
            val weight = bodyWeightKg?.takeIf { it > 0.0 }
            val table = sex?.let { data.quantiles(key, it) }
            if (weight == null || table == null) return Standing.NeedsProfile
            val ratio = bestE1rmKg?.let { Benchmarks.ratio(it, weight) } ?: return null
            val percentile = Benchmarks.percentile(ratio, table.quantiles)
            return Standing.Percentile(
                level = Benchmarks.level(percentile),
                percentile = percentile,
                ratio = ratio,
                quantiles = table.quantiles,
                curve = Benchmarks.curve(table.quantiles),
                population = table.population,
                bodyWeightKg = weight,
            )
        }
        if (sex == null) return Standing.NeedsProfile
        val table = data.standard(key, sex, age) ?: return null
        val value = when (table.unit) {
            "reps" -> bestReps?.toDouble()
            "seconds" -> bestSeconds?.toDouble()
            else -> bestE1rmKg
        } ?: return null
        val points = Benchmarks.points(value, table)
        return Standing.Standard(Benchmarks.standardLevel(points, table.passPoints), points, ageNotSet = age == null)
    }
}
