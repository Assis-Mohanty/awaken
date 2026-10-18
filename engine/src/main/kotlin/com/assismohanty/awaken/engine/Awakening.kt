package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat

/** Onboarding rules (DESIGN_SYSTEM §4.1, GAME_DESIGN §2). */
object Awakening {
    const val NAME_MIN = 3
    const val NAME_MAX = 20

    /** One calibration answer: Rarely / <1h / <6h → LOW; Sometimes / 1–3h / 6–7h → MID; Often / 3h+ / 7h+ → HIGH. */
    enum class Answer(val tier: Difficulty) {
        LOW(Difficulty.EASY),
        MID(Difficulty.NORMAL),
        HIGH(Difficulty.HARD),
    }

    /** Training → STR, study → INT, sleep → VIT; WIL and CHA start EASY (Phase 6 spec). */
    fun baselineTiers(training: Answer, study: Answer, sleep: Answer): Map<Stat, Difficulty> = mapOf(
        Stat.STR to training.tier,
        Stat.INT to study.tier,
        Stat.WIL to Difficulty.EASY,
        Stat.VIT to sleep.tier,
        Stat.CHA to Difficulty.EASY,
    )

    /** GD §2: 3–20 characters after trimming. */
    fun isValidName(name: String): Boolean = name.trim().length in NAME_MIN..NAME_MAX
}
