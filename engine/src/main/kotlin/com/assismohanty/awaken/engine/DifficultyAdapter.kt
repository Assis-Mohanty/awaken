package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * A generated quest's outcome, as input to recalibration. [pathSlotId] marks a Path quest (even when promoted to
 * main); recalibration ignores those (GD §6.4).
 */
data class QuestOutcome(
    val gameDate: LocalDate,
    val type: QuestType,
    val stat: Stat,
    val status: QuestStatus,
    val pathSlotId: String? = null,
)

/** Weekly per-stat adaptive difficulty (GD §6.4). */
object DifficultyAdapter {
    const val PROMOTE_RATE = 0.85
    const val DEMOTE_RATE = 0.40
    const val MIN_QUESTS = 4
    const val WINDOW_DAYS = 7L

    enum class Change { UP, DOWN, NONE }

    data class Recalibration(val tiers: Map<Stat, Difficulty>, val changes: Map<Stat, Change>)

    /** The Monday on/before [gameDate]; recalibration is due when the last run was before it ("Monday or first open after"). */
    fun recalibrationDate(gameDate: LocalDate): LocalDate = gameDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun isDue(gameDate: LocalDate, lastRecalibratedOn: LocalDate?): Boolean =
        lastRecalibratedOn == null || lastRecalibratedOn < recalibrationDate(gameDate)

    /**
     * New tier for each stat from the 7 game-days before the recalibration Monday. Only template-generated quests (main/side)
     * count; REROLLED quests were replaced, not attempted, and are ignored.
     */
    fun recalibrate(current: Map<Stat, Difficulty>, outcomes: List<QuestOutcome>, gameDate: LocalDate): Recalibration {
        val monday = recalibrationDate(gameDate)
        val window = monday.minusDays(WINDOW_DAYS)..monday.minusDays(1)
        val relevant = outcomes.filter { it.type.isGenerated && it.pathSlotId == null && it.gameDate in window && !it.status.isHidden }
        val tiers = HashMap<Stat, Difficulty>()
        val changes = HashMap<Stat, Change>()
        for (stat in Stat.entries) {
            val tier = current[stat] ?: Difficulty.NORMAL
            val quests = relevant.filter { it.stat == stat }
            val change = changeFor(quests.count { it.status == QuestStatus.COMPLETED }, quests.size)
            tiers[stat] = when (change) {
                Change.UP -> tier.harder()
                Change.DOWN -> tier.easier()
                Change.NONE -> tier
            }
            changes[stat] = if (tiers[stat] == tier) Change.NONE else change
        }
        return Recalibration(tiers, changes)
    }

    fun changeFor(completed: Int, total: Int): Change {
        if (total < MIN_QUESTS) return Change.NONE
        val rate = completed.toDouble() / total
        return when {
            rate >= PROMOTE_RATE -> Change.UP
            rate <= DEMOTE_RATE -> Change.DOWN
            else -> Change.NONE
        }
    }
}
