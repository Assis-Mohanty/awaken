package com.assismohanty.awaken.engine.model

import kotlinx.serialization.Serializable

/** The five hunter stats (GD §3). Declaration order is the canonical tie-break order. */
@Serializable
enum class Stat { STR, INT, WIL, VIT, CHA }

/** Difficulty multiplier `D` (GD §4.1). Declaration order is the tier order EASY < … < EXTREME. */
@Serializable
enum class Difficulty(val multiplier: Double) {
    EASY(1.0),
    NORMAL(1.5),
    HARD(2.25),
    EXTREME(3.0);

    /** One tier up, capped at [EXTREME]. */
    fun harder(): Difficulty = entries[minOf(ordinal + 1, entries.lastIndex)]

    /** One tier down, floored at [EASY]. */
    fun easier(): Difficulty = entries[maxOf(ordinal - 1, 0)]
}

/** The three verification sources that drive `V` and the daily caps (GD §4.1, §4.3). */
enum class VerificationSource(val multiplier: Double) {
    SELF(1.0),
    TIMER(1.15),
    HEALTH(1.30),
}

/** Verification as written in templates (GD §6.2). Every HEALTH_* variant earns the HEALTH multiplier. */
@Serializable
enum class Verification(val source: VerificationSource) {
    SELF(VerificationSource.SELF),
    TIMER(VerificationSource.TIMER),
    HEALTH_STEPS(VerificationSource.HEALTH),
    HEALTH_EXERCISE(VerificationSource.HEALTH),
    HEALTH_SLEEP(VerificationSource.HEALTH),
}

/** Quest types (GD §6.1). */
enum class QuestType {
    MAIN,
    SIDE,
    HABIT,
    PENALTY,
    TRIAL,
    GATE,

    /** A Path quest that was not promoted to main (GD §6.6.6). A promoted one is MAIN with a pathSlotId. */
    PATH;

    /** Penalty quests and Rank Trial stages cannot crit (GD §4.2). */
    val canCrit: Boolean get() = this != PENALTY && this != TRIAL

    /** Main + side quests are "generated" (GD §6.4, §6.5). PATH is not: Path quests follow §6.6 (carry-over, own signal). */
    val isGenerated: Boolean get() = this == MAIN || this == SIDE
}

/** Lifecycle of a quest instance (ARCHITECTURE §2). */
enum class QuestStatus {
    ACTIVE, STARTED, COMPLETED, EXPIRED, MISSED, REROLLED,

    /** A Path quest the hunter reported (PRD F21): hidden like REROLLED, but never counted as a paid reroll. */
    REPORTED;

    /** Replaced or removed quests: not shown, not counted, not attempted (ENGINE_DECISIONS #74). */
    val isHidden: Boolean get() = this == REROLLED || this == REPORTED
}

/** Hunter ranks and their level bands (GD §5). */
enum class Rank(val minLevel: Int) {
    E(1),
    D(10),
    C(20),
    B(35),
    A(50),
    S(70);

    val next: Rank? get() = entries.getOrNull(ordinal + 1)
}

/** Ledger row kinds (ARCHITECTURE §2). */
enum class LedgerKind { XP_TOTAL, XP_STAT, GOLD, ITEM, FRAGMENT }

/** Ledger row source types (ARCHITECTURE §2). */
enum class SourceType { QUEST, GATE, TRIAL, ACHIEVEMENT, DAY_CLEAR, SHOP, UNDO, SHIELD_USE, CACHE_OPEN }

/** Focus Gate ranks (GD §8). */
enum class GateRank(val durationMinutes: Int, val difficulty: Difficulty) {
    E(15, Difficulty.EASY),
    D(25, Difficulty.NORMAL),
    C(50, Difficulty.HARD),
    B(90, Difficulty.EXTREME),
}
