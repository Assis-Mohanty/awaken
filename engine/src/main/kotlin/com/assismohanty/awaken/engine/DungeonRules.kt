package com.assismohanty.awaken.engine

import java.time.LocalDate

/** Why a Dungeon cannot be entered. */
enum class DungeonEntry { OK, LOCKED, NOT_ENOUGH_GOLD, ALREADY_ACTIVE, BAD_LENGTH }

/** What a finished day means for the running Dungeon. */
enum class DungeonVerdict {
    /** Nothing to decide yet (a later stage, or the day is not over). */
    PENDING,

    /** This stage is done and more remain. */
    STAGE_DONE,

    /** The last stage is done: the Dungeon is cleared. */
    CLEARED,

    /** A stage was not completed by the end of its game-day. */
    FAILED,
}

/**
 * Dungeons (GD §16.14, approved 2026-11-02): a multi-day challenge of Hard quests for a gold entry fee. Pure. Failing costs
 * only the fee already paid: nothing else is ever deducted and no penalty quest follows.
 */
object DungeonRules {
    /** N27: level 10 (the D rank band) unlocks Dungeons, so new hunters do not burn their first gold. */
    const val UNLOCK_LEVEL = 10

    /** N28: the lengths in days and what each costs to enter. */
    val LENGTHS = listOf(3, 5, 7)
    private val FEES = mapOf(3 to 100, 5 to 200, 7 to 300)

    /** N29: a clear pays back 1.5 × the fee (as 3/2, so it stays whole) and one fragment. */
    const val FRAGMENTS_ON_CLEAR = 1

    fun fee(lengthDays: Int): Int? = FEES[lengthDays]

    fun reward(lengthDays: Int): Int? = FEES[lengthDays]?.let { it * 3 / 2 }

    fun isUnlocked(level: Int): Boolean = level >= UNLOCK_LEVEL

    fun canEnter(level: Int, gold: Long, lengthDays: Int, hasActiveDungeon: Boolean): DungeonEntry {
        val fee = fee(lengthDays) ?: return DungeonEntry.BAD_LENGTH
        return when {
            !isUnlocked(level) -> DungeonEntry.LOCKED
            hasActiveDungeon -> DungeonEntry.ALREADY_ACTIVE
            gold < fee -> DungeonEntry.NOT_ENOUGH_GOLD
            else -> DungeonEntry.OK
        }
    }

    /** Stage 1 is the next game-day after entering. */
    fun firstStageDate(entered: LocalDate): LocalDate = entered.plusDays(1)

    fun stageDate(firstStage: LocalDate, stage: Int): LocalDate = firstStage.plusDays(stage - 1L)

    /** The 1-based stage of [date], or null outside the Dungeon's days. */
    fun stageOn(firstStage: LocalDate, lengthDays: Int, date: LocalDate): Int? {
        val stage = (date.toEpochDay() - firstStage.toEpochDay() + 1).toInt()
        return stage.takeIf { it in 1..lengthDays }
    }

    /**
     * The verdict on the stage of [date] once that game-day is over: done → on to the next (or cleared after the last);
     * anything else, including a day the app never generated, fails the Dungeon.
     */
    fun verdict(stage: Int, lengthDays: Int, stageCompleted: Boolean): DungeonVerdict = when {
        !stageCompleted -> DungeonVerdict.FAILED
        stage >= lengthDays -> DungeonVerdict.CLEARED
        else -> DungeonVerdict.STAGE_DONE
    }

    /** True for a seven-day clear (`dungeon_7`). */
    fun isTopLength(lengthDays: Int): Boolean = lengthDays == LENGTHS.last()
}
