package com.assismohanty.awaken.engine

/**
 * Deterministic crit (GD §4.2): `fnv1a64(questInstanceId) mod 10_000 < 800`. The hash is treated as unsigned and the id is
 * lower-cased first so the same UUID always rolls the same result.
 */
object CritRoller {
    const val CRIT_MULTIPLIER = 2.0
    private const val MODULUS = 10_000uL
    private const val THRESHOLD = 800uL

    fun isCrit(questInstanceId: String): Boolean =
        Fnv1a.hash64(questInstanceId.lowercase()).toULong() % MODULUS < THRESHOLD
}
