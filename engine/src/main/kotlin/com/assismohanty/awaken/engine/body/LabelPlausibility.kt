package com.assismohanty.awaken.engine.body

/**
 * Can a packaged product's label numbers be believed? Volunteers type these into Open Food Facts, so a product is only
 * shown when its numbers are possible and its calories roughly match its macros (the [Atwater] source-data check). The
 * same limits build the offline pack (`tools/packaged-pack/build-packaged.mjs`). Pure.
 */
object LabelPlausibility {
    const val MAX_KCAL_PER_100G = 900.0

    /** Protein + carbs + fat per 100 g cannot honestly pass 100 g; a little slack covers rounding on labels. */
    const val MACRO_SUM_LIMIT = 105.0

    /** Sugars, fibre and saturated fat may not exceed their parent (carbs or fat) by more than this many grams. */
    const val PART_TOLERANCE = 0.5
    const val MAX_SODIUM_MG_PER_100G = 40_000.0

    enum class Problem { MISSING, NEGATIVE, IMPOSSIBLE, MACROS_OVER_100G, CALORIES_DISAGREE }

    /** Label values per 100 g; sodium in milligrams. Null means the label did not say. */
    data class Label(
        val kcal: Double?,
        val protein: Double?,
        val carbs: Double?,
        val fat: Double?,
        val sugars: Double? = null,
        val satFat: Double? = null,
        val fiber: Double? = null,
        val sodiumMg: Double? = null,
    )

    sealed interface Verdict {
        /** [per100] holds calories and the three macros, plus each extra value that was consistent. */
        data class Accepted(val per100: Nutrients) : Verdict
        data class Rejected(val problem: Problem) : Verdict
    }

    fun check(label: Label): Verdict {
        val kcal = label.kcal
        val protein = label.protein
        val carbs = label.carbs
        val fat = label.fat
        if (kcal == null || protein == null || carbs == null || fat == null) return Verdict.Rejected(Problem.MISSING)
        if (listOf(kcal, protein, carbs, fat).any { it < 0.0 }) return Verdict.Rejected(Problem.NEGATIVE)
        if (kcal > MAX_KCAL_PER_100G || protein > Nutrients.PER_GRAMS || carbs > Nutrients.PER_GRAMS || fat > Nutrients.PER_GRAMS) {
            return Verdict.Rejected(Problem.IMPOSSIBLE)
        }
        if (protein + carbs + fat > MACRO_SUM_LIMIT) return Verdict.Rejected(Problem.MACROS_OVER_100G)

        val fiber = label.fiber?.takeIf { it in 0.0..(carbs + PART_TOLERANCE) }
        val sugars = label.sugars?.takeIf { it in 0.0..(carbs + PART_TOLERANCE) }
        val satFat = label.satFat?.takeIf { it in 0.0..(fat + PART_TOLERANCE) }
        val sodium = label.sodiumMg?.takeIf { it in 0.0..MAX_SODIUM_MG_PER_100G }

        val values = buildMap {
            put(Nutrient.KCAL, kcal)
            put(Nutrient.PROTEIN, protein)
            put(Nutrient.CARBS, carbs)
            put(Nutrient.FAT, fat)
            sugars?.let { put(Nutrient.SUGARS, it) }
            satFat?.let { put(Nutrient.SAT_FAT, it) }
            fiber?.let { put(Nutrient.FIBER, it) }
            sodium?.let { put(Nutrient.SODIUM, it) }
        }
        val nutrients = Nutrients(values)
        if (Atwater.check(nutrients, ownRow = false) == AtwaterResult.WARNING) return Verdict.Rejected(Problem.CALORIES_DISAGREE)
        return Verdict.Accepted(nutrients)
    }
}
