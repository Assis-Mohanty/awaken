package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.VerificationSource

/** Daily anti-farm caps per game-day (GD §4.3). The rule is per-quest: a quest that would cross the cap is reduced whole. */
object DailyCaps {
    const val SELF_CAP = 600
    const val TIMER_CAP = 900
    const val BEYOND_CAP_FACTOR = 0.25

    /** Cap for [source], or null when uncapped. */
    fun capFor(source: VerificationSource): Int? = when (source) {
        VerificationSource.SELF -> SELF_CAP
        VerificationSource.TIMER -> TIMER_CAP
        VerificationSource.HEALTH -> null
    }

    fun earnedToday(source: VerificationSource, context: HunterContext): Int = when (source) {
        VerificationSource.SELF -> context.todaySelfXp
        VerificationSource.TIMER -> context.todayTimerXp
        VerificationSource.HEALTH -> 0
    }

    /** True when awarding [questXp] in full would push today's [source] total past its cap. */
    fun exceeds(source: VerificationSource, earnedToday: Int, questXp: Int): Boolean {
        val cap = capFor(source) ?: return false
        return earnedToday + questXp > cap
    }
}
