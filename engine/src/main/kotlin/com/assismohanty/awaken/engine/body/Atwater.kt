package com.assismohanty.awaken.engine.body

import kotlin.math.abs
import kotlin.math.max

enum class AtwaterResult { OK, WARNING, ERROR, UNKNOWN }

/**
 * Energy sanity check for a food (GD section 20.3, ENGINE_DECISIONS #182): calories should roughly match what the macros
 * add up to. Fibre counts 2 kcal/g (carbs include fibre), not the 4 of other carbs.
 */
object Atwater {
    private const val KCAL_PROTEIN = 4.0
    private const val KCAL_CARBS = 4.0
    private const val KCAL_FAT = 9.0
    private const val KCAL_ALCOHOL = 7.0
    private const val KCAL_FIBRE = 2.0

    private const val WARN_KCAL = 20.0
    private const val WARN_SHARE = 0.20
    private const val OWN_KCAL = 10.0
    private const val OWN_SHARE = 0.10

    /** Calories the macros add up to, or null when protein, carbs or fat is unknown. */
    fun estimateKcal(n: Nutrients): Double? {
        val v = n.values
        if (Nutrient.PROTEIN !in v || Nutrient.CARBS !in v || Nutrient.FAT !in v) return null
        val fibre = n[Nutrient.FIBER]
        return KCAL_PROTEIN * n[Nutrient.PROTEIN] + KCAL_CARBS * (n[Nutrient.CARBS] - fibre) + KCAL_FIBRE * fibre +
            KCAL_FAT * n[Nutrient.FAT] + KCAL_ALCOHOL * n[Nutrient.ALCOHOL]
    }

    /** [ownRow] = a row AWAKEN computed itself (tight tolerance, an error); source data only gets a WARNING. */
    fun check(n: Nutrients, ownRow: Boolean): AtwaterResult {
        val estimate = estimateKcal(n) ?: return AtwaterResult.UNKNOWN
        if (Nutrient.KCAL !in n.values) return AtwaterResult.UNKNOWN
        val kcal = n[Nutrient.KCAL]
        val off = abs(kcal - estimate)
        return when {
            ownRow && off > max(OWN_KCAL, OWN_SHARE * kcal) -> AtwaterResult.ERROR
            !ownRow && off > max(WARN_KCAL, WARN_SHARE * kcal) -> AtwaterResult.WARNING
            else -> AtwaterResult.OK
        }
    }
}
