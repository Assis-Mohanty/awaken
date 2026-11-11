package com.assismohanty.awaken.engine.body

/** Water tracker numbers (GD section 20.4). A tracker only: nothing here pays XP. */
object Hydration {
    const val TARGET_FEMALE_ML = 2_000
    const val TARGET_MALE_ML = 2_500

    /** The target a hunter can change within [TARGET_RANGE_ML]. */
    val TARGET_RANGE_ML = 500..6_000

    /** One glass. */
    const val GLASS_ML = 250

    fun defaultTargetMl(sex: Sex): Int = if (sex == Sex.FEMALE) TARGET_FEMALE_ML else TARGET_MALE_ML

    fun targetMl(sex: Sex, chosen: Int?): Int = chosen?.coerceIn(TARGET_RANGE_ML) ?: defaultTargetMl(sex)
}
