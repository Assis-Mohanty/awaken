package com.assismohanty.awaken.engine.body

/** How weights are shown and typed. Everything is stored in kilograms; a unit only changes what the hunter sees. Pure. */
enum class WeightUnit {
    KG,
    LB,
    ;

    fun fromKg(kg: Double): Double = if (this == LB) kg / KG_PER_LB else kg

    fun toKg(value: Double): Double = if (this == LB) value * KG_PER_LB else value

    companion object {
        /** The international avoirdupois pound, exactly. */
        const val KG_PER_LB = 0.45359237
    }
}
