package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.Duration
import java.time.Instant

/** Completion, undo and custom-quest rules (GD §4.3, §6.5). */
object QuestRules {
    val UNDO_WINDOW: Duration = Duration.ofMinutes(10)
    const val MAX_ACTIVE_CUSTOM = 8

    /** A SELF quest that was begun cannot complete before `minDurationSec`; one never begun may complete any time. */
    fun canComplete(startedAt: Instant?, now: Instant, minDurationSec: Int): Boolean =
        startedAt == null || Duration.between(startedAt, now).seconds >= minDurationSec

    fun canUndo(completedAt: Instant, now: Instant): Boolean =
        !now.isBefore(completedAt) && Duration.between(completedAt, now) <= UNDO_WINDOW

    /** Custom quests max out at Normal unless verified by TIMER or HEALTH. */
    fun maxCustomDifficulty(verification: VerificationSource): Difficulty =
        if (verification == VerificationSource.SELF) Difficulty.NORMAL else Difficulty.EXTREME

    fun canAddCustom(activeCustomCount: Int): Boolean = activeCustomCount < MAX_ACTIVE_CUSTOM
}

/** Focus Gate breach rules (GD §8). */
object GateRules {
    const val BREACH_BUDGET_MS = 90_000L
    const val MAX_PAUSES = 1
    val MAX_PAUSE: Duration = Duration.ofMinutes(5)

    fun isCollapsed(backgroundMs: Long): Boolean = backgroundMs > BREACH_BUDGET_MS

    fun canPause(pausesUsed: Int): Boolean = pausesUsed < MAX_PAUSES
}
