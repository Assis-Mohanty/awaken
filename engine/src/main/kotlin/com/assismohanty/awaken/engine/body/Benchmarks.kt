package com.assismohanty.awaken.engine.body

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The five strength levels shown on the gauge (GD §18.3). */
enum class StrengthLevel { BEGINNER, NOVICE, INTERMEDIATE, ADVANCED, ELITE }

/** One population's lift-to-body-weight ratios as the 1st..99th percentile values (99 numbers, ascending). */
@Serializable
data class RatioQuantiles(
    /** Benchmark key, e.g. `squat`. */
    val key: String,
    /** `M` or `F`. */
    val sex: String,
    /** The 99 ratio values for percentiles 1..99. */
    val quantiles: List<Double>,
    val n: Int,
    /** Who the people are, in words, for the caption (GD §18.3 Q2). */
    val population: String,
    val source: String,
    val licence: String,
)

/** A published standard: points earned by reaching [value] (reps, kg or seconds), for one sex and age band. */
@Serializable
data class StandardTable(
    val key: String,
    val sex: String,
    val ageFrom: Int,
    val ageTo: Int,
    /** `reps`, `kg` or `seconds`. */
    val unit: String,
    /** Lowest points that count as a pass. */
    val passPoints: Int,
    /** `[value, points]` pairs, ascending in value. */
    val rows: List<List<Double>>,
    val source: String,
    val licence: String,
)

@Serializable
data class BenchmarkData(
    val version: Int = 1,
    val percentile: List<RatioQuantiles> = emptyList(),
    val standards: List<StandardTable> = emptyList(),
    /** Catalog exercise id → benchmark key. */
    val exercises: Map<String, String> = emptyMap(),
) {
    fun keyFor(exerciseId: String): String? = exercises[exerciseId]

    fun quantiles(key: String, sex: String): RatioQuantiles? = percentile.firstOrNull { it.key == key && it.sex == sex }

    fun standard(key: String, sex: String, age: Int?): StandardTable? {
        val own = standards.filter { it.key == key && it.sex == sex }
        return own.firstOrNull { age != null && age in it.ageFrom..it.ageTo }
            ?: own.firstOrNull { Benchmarks.DEFAULT_AGE in it.ageFrom..it.ageTo }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): BenchmarkData = json.decodeFromString(serializer(), text)
    }
}

/** Benchmark maths (GD §18.3). All pure. A percentile is an estimate against a named population, never a rank among users. */
object Benchmarks {
    /** G10: age band used when no birth year is set. */
    const val DEFAULT_AGE = 24

    /** G8: first percentile of each level above Beginner. */
    private val LEVEL_FROM = intArrayOf(20, 50, 80, 95)

    fun ratio(e1rmKg: Double, bodyWeightKg: Double): Double? = if (e1rmKg > 0.0 && bodyWeightKg > 0.0) e1rmKg / bodyWeightKg else null

    /** Percentile 1..99 by linear interpolation between the stored quantiles; below the first or above the last clamps. */
    fun percentile(ratio: Double, quantiles: List<Double>): Double {
        if (quantiles.size < 2) return 50.0
        if (ratio <= quantiles.first()) return 1.0
        if (ratio >= quantiles.last()) return quantiles.size.toDouble()
        var i = 0
        while (i < quantiles.lastIndex && quantiles[i + 1] <= ratio) i++
        val low = quantiles[i]
        val high = quantiles[i + 1]
        val fraction = if (high > low) (ratio - low) / (high - low) else 0.0
        return (i + 1) + fraction
    }

    /** The ratio that sits at [percentile] (1..99); the inverse of [percentile], for the draggable curve. */
    fun ratioAt(percentile: Double, quantiles: List<Double>): Double {
        val p = percentile.coerceIn(1.0, quantiles.size.toDouble())
        val i = kotlin.math.floor(p).toInt() - 1
        if (i >= quantiles.lastIndex) return quantiles.last()
        return quantiles[i] + (quantiles[i + 1] - quantiles[i]) * (p - (i + 1))
    }

    fun level(percentile: Double): StrengthLevel {
        val index = LEVEL_FROM.count { percentile >= it }
        return StrengthLevel.entries[index]
    }

    /**
     * The shape of the distribution as (ratio, relative density) points for the curve: the density between neighbouring
     * quantiles is 1 / spacing, smoothed over three neighbours. Relative only: the UI never shows the peak value.
     */
    fun curve(quantiles: List<Double>, samples: Int = 48): List<Pair<Double, Double>> {
        if (quantiles.size < 3 || samples < 2) return emptyList()
        val lo = quantiles.first()
        val hi = quantiles.last()
        fun rawDensity(x: Double): Double {
            var i = 0
            while (i < quantiles.size - 2 && quantiles[i + 1] <= x) i++
            val spacing = (quantiles[i + 1] - quantiles[i]).coerceAtLeast(1e-9)
            return 1.0 / spacing
        }
        val xs = (0 until samples).map { lo + (hi - lo) * it / (samples - 1) }
        val raw = xs.map(::rawDensity)
        val smooth = raw.indices.map { i ->
            val window = (maxOf(0, i - 2)..minOf(raw.lastIndex, i + 2)).map { raw[it] }
            window.average()
        }
        val peak = smooth.max()
        return xs.zip(smooth.map { it / peak })
    }

    /** Points for [value] on a published table: the highest row reached; 0 below the first row. */
    fun points(value: Double, table: StandardTable): Int =
        table.rows.lastOrNull { it.size == 2 && value >= it[0] }?.get(1)?.toInt() ?: 0

    /** G9: below a pass is Beginner, then 60–79, 80–99 and 100 move up; a pass under 60 is Novice. */
    fun standardLevel(points: Int, passPoints: Int): StrengthLevel = when {
        points >= 100 -> StrengthLevel.ELITE
        points >= 80 -> StrengthLevel.ADVANCED
        points >= 60 -> StrengthLevel.INTERMEDIATE
        points >= passPoints -> StrengthLevel.NOVICE
        else -> StrengthLevel.BEGINNER
    }
}
