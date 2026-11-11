package com.assismohanty.awaken.engine.body

/**
 * How much of a day's food has a value for a nutrient (GD section 20.4). Many foods lack micro data, so a low total can mean
 * "not recorded" rather than "not eaten": under [PARTIAL_BELOW] the screen says "Partial data" and shows no bar against the
 * reference. The share is by calories, so a glass of water does not count as a food without data. Pure.
 */
object NutrientCoverage {
    /** Under this share of the day's calories, a nutrient's total is shown as partial. */
    const val PARTIAL_BELOW = 0.8

    /** One diary row's amounts as eaten. A nutrient absent from [amounts] is unknown for that row. */
    fun share(rows: List<Nutrients>, nutrient: Nutrient): Double? {
        val total = rows.sumOf { it[Nutrient.KCAL] }
        if (total <= 0.0) return null
        val known = rows.filter { nutrient in it.values }.sumOf { it[Nutrient.KCAL] }
        return known / total
    }

    fun isPartial(share: Double?): Boolean = share != null && share < PARTIAL_BELOW
}
