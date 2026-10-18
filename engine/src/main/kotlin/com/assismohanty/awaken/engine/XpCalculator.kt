package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.BreakdownItem
import com.assismohanty.awaken.engine.model.BreakdownItem.Kind
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestReward
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource

/** The facts about a quest instance needed to price its completion. */
data class CompletableQuest(
    val questInstanceId: String,
    val type: QuestType,
    val stat: Stat,
    val secondaryStat: Stat?,
    val difficulty: Difficulty,
    val verification: VerificationSource,
)

/** Quest XP, stat split, gold and fragments (GD §3, §4.1–4.3, §8, §9.1). */
object XpCalculator {
    const val BASE = 40
    const val STREAK_CAP_DAYS = 30
    const val STREAK_DIVISOR = 60.0
    const val SECONDARY_SHARE = 0.3
    const val GOLD_PER_XP = 0.5

    /** `S = 1 + min(streakDays, 30) / 60`. */
    fun streakMultiplier(streakDays: Int): Double = 1.0 + minOf(maxOf(streakDays, 0), STREAK_CAP_DAYS) / STREAK_DIVISOR

    /** `round(BASE × D × S × V × C)`, half-up on the final product only. */
    fun questXp(difficulty: Difficulty, streakDays: Int, verification: VerificationSource, crit: Boolean): Int {
        val c = if (crit) CritRoller.CRIT_MULTIPLIER else 1.0
        return roundHalfUp(BASE * difficulty.multiplier * streakMultiplier(streakDays) * verification.multiplier * c)
    }

    /** `round(questXp × 0.5)`; callers pass 0 gold for capped quests. */
    fun goldFor(questXp: Int): Int = roundHalfUp(questXp * GOLD_PER_XP)

    fun forCompletion(quest: CompletableQuest, context: HunterContext): QuestReward {
        val crit = quest.type.canCrit && CritRoller.isCrit(quest.questInstanceId)
        val fullXp = questXp(quest.difficulty, context.streak, quest.verification, crit)
        val capped = DailyCaps.exceeds(quest.verification, DailyCaps.earnedToday(quest.verification, context), fullXp)
        val xp = if (capped) roundHalfUp(fullXp * DailyCaps.BEYOND_CAP_FACTOR) else fullXp
        val gold = if (capped) 0 else goldFor(xp)

        val statXp = buildMap {
            put(quest.stat, xp)
            val secondary = quest.secondaryStat
            if (secondary != null && secondary != quest.stat) put(secondary, roundHalfUp(xp * SECONDARY_SHARE))
        }
        val fragments = (if (crit) 1 else 0) + (if (quest.type == QuestType.GATE) 1 else 0)

        val breakdown = buildList {
            add(BreakdownItem(Kind.BASE, BASE.toDouble()))
            add(BreakdownItem(Kind.DIFFICULTY, quest.difficulty.multiplier))
            add(BreakdownItem(Kind.STREAK, streakMultiplier(context.streak)))
            add(BreakdownItem(Kind.VERIFICATION, quest.verification.multiplier))
            if (crit) add(BreakdownItem(Kind.CRIT, CritRoller.CRIT_MULTIPLIER))
            if (capped) add(BreakdownItem(Kind.DAILY_CAP, DailyCaps.BEYOND_CAP_FACTOR))
        }
        return QuestReward(
            xp = xp,
            statXp = statXp,
            gold = gold,
            crit = crit,
            cappedSource = if (capped) quest.verification else null,
            fragments = fragments,
            breakdown = breakdown,
        )
    }

    /** `Math.round` semantics (half-up), as GD §4.1 requires; `kotlin.math.round` would be half-even. */
    internal fun roundHalfUp(value: Double): Int = Math.round(value).toInt()
}
