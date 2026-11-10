package com.assismohanty.awaken.engine.body

/** Household measures, in the order the serving picker lists them (Indian units first). [volumeMl] is null for counted units. */
enum class PortionUnit(val volumeMl: Double? = null) {
    KATORI(150.0),
    ROTI,
    PIECE,
    CUP(240.0),
    GLASS(250.0),
    TBSP(15.0),
    TSP(5.0),
    SLICE,
    PLATE,
    OTHER,
}

/** Serving and label arithmetic (Diet v2 plan section 3.3). Pure. */
object Portions {
    private const val SALT_SODIUM_RATIO = 2.5
    private const val KJ_PER_KCAL = 4.184
    private const val MG_PER_G = 1_000.0

    /** Grams in [count] servings of [portionGrams] each. */
    fun grams(count: Double, portionGrams: Double): Double = count * portionGrams

    /** Grams in [count] measures of [unit] of a food that weighs [gramsPerMl]; null for a unit that is not a volume. */
    fun gramsForVolume(unit: PortionUnit, count: Double, gramsPerMl: Double): Double? = unit.volumeMl?.let { count * it * gramsPerMl }

    /** A label reads per serving; the catalog stores per 100 g. */
    fun per100FromServing(perServing: Nutrients, servingGrams: Double): Nutrients {
        require(servingGrams > 0.0) { "servingGrams must be positive" }
        return Nutrients(perServing.values.mapValues { (_, amount) -> amount * Nutrients.PER_GRAMS / servingGrams })
    }

    fun kcalFromKj(kj: Double): Double = kj / KJ_PER_KCAL

    /** Sodium in mg for [saltGrams] of salt (labels that print salt, not sodium: sodium = salt / 2.5). */
    fun sodiumMgFromSalt(saltGrams: Double): Double = saltGrams / SALT_SODIUM_RATIO * MG_PER_G
}
