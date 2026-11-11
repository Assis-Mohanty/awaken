package com.assismohanty.awaken.engine.body

/** Nutrition-label arithmetic for the "add it from the label" form (Diet v2 plan section 6.4). Pure. */
object LabelMath {
    /** One kilocalorie is 4.184 kilojoules (the thermochemical definition). */
    const val KJ_PER_KCAL = 4.184

    /** Salt is sodium times 2.5 on labels, so sodium is salt divided by 2.5; the app keeps sodium in milligrams. */
    const val SALT_PER_SODIUM = 2.5
    private const val MG_PER_G = 1_000.0

    fun kcalFromKj(kj: Double): Double = kj / KJ_PER_KCAL

    fun sodiumMgFromSalt(saltGrams: Double): Double = saltGrams / SALT_PER_SODIUM * MG_PER_G

    /** A value printed per serving of [servingGrams] as its amount per 100 g; null when the serving size is not a positive weight. */
    fun per100(valuePerServing: Double, servingGrams: Double): Double? =
        if (servingGrams > 0.0) valuePerServing * Nutrients.PER_GRAMS / servingGrams else null
}
