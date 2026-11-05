package com.assismohanty.awaken.engine

/** The hunter's name rules (GAME_DESIGN §2). The starting values come from the onboarding answers: `onboarding/OnboardingMapping`. */
object Awakening {
    const val NAME_MIN = 3
    const val NAME_MAX = 20

    /** GD §2: 3–20 characters after trimming. */
    fun isValidName(name: String): Boolean = name.trim().length in NAME_MIN..NAME_MAX
}
