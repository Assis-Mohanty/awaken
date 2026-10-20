package com.assismohanty.awaken.engine.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** One entry of `assets/quests/templates.json` (GD §6.2). */
@Serializable
data class QuestTemplate(
    val id: String,
    val stat: Stat,
    val secondaryStat: Stat? = null,
    val titles: List<String>,
    val description: String,
    val unit: String,
    val targets: Map<Difficulty, Int>,
    val verification: Verification,
    val equipment: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val minDurationSec: Int = 0,
) {
    init {
        require(titles.isNotEmpty()) { "template $id has no titles" }
        require(targets.isNotEmpty()) { "template $id has no targets" }
    }
}

/**
 * A generated quest before it is persisted. [questInstanceId] is a deterministic UUID string (crit seed + ledger key).
 * [templateId] is null for Path quests, which come from a validated, priced blueprint instead ([pathSlotId], GD §6.6).
 */
data class QuestDraft(
    val questInstanceId: String,
    val gameDate: LocalDate,
    val type: QuestType,
    val templateId: String?,
    val stat: Stat,
    val secondaryStat: Stat?,
    val title: String,
    val description: String,
    val unit: String,
    val target: Int,
    val difficulty: Difficulty,
    val verification: Verification,
    val minDurationSec: Int,
    /** The Path slot this quest was scheduled from; null for template, habit, penalty and trial quests. */
    val pathSlotId: String? = null,
)

/**
 * Main + Path + side quests generated for one `(hunterId, gameDate)` (GD §6.3, §6.6.6). [paths] are the Path quests
 * that were not promoted to main. Habits are user-owned and not generated.
 */
data class Directive(
    val gameDate: LocalDate,
    val main: QuestDraft?,
    val sides: List<QuestDraft>,
    val paths: List<QuestDraft> = emptyList(),
) {
    val quests: List<QuestDraft> get() = listOfNotNull(main) + paths + sides
}

/**
 * Everything the engine needs to know about the hunter to price a completion or generate a directive.
 *
 * @param streak streak entering today, i.e. folded over finalized days only (today's completions never boost themselves).
 * @param todaySelfXp XP already awarded today from SELF-verified sources.
 * @param todayTimerXp XP already awarded today from TIMER-verified sources.
 * @param tiers adaptive difficulty per stat (GD §6.4); missing stats default to [Difficulty.NORMAL].
 * @param ownedEquipment equipment ids the hunter owns (settings).
 * @param lastActiveDates last game-date each stat had a completion; missing = never.
 * @param recentTemplateIds template ids used in the novelty window (the 7 game-days before the target date).
 * @param firstDirective the hunter's first directive (onboarding day).
 */
data class HunterContext(
    val hunterId: String,
    val streak: Int = 0,
    val todaySelfXp: Int = 0,
    val todayTimerXp: Int = 0,
    val tiers: Map<Stat, Difficulty> = emptyMap(),
    val focusStats: List<Stat>,
    val ownedEquipment: Set<String> = emptySet(),
    /** Template tags to avoid: empty when Injury Mode is off, else the hunter's chosen tags (settings). */
    val injuryTags: Set<String> = emptySet(),
    val recoveryMode: Boolean = false,
    val healthAvailable: Boolean = true,
    val lastActiveDates: Map<Stat, LocalDate> = emptyMap(),
    val recentTemplateIds: Set<String> = emptySet(),
    /** True only for the hunter's first game-day: one side slot becomes an Easy starter quest (GD §6.3). */
    val firstDirective: Boolean = false,
) {
    init {
        require(focusStats.size == 2 && focusStats[0] != focusStats[1]) { "exactly two distinct focus stats required" }
        require(streak >= 0) { "streak must be >= 0" }
    }

    fun tier(stat: Stat): Difficulty = tiers[stat] ?: Difficulty.NORMAL
}

/** One factor of the explainable XP formula (GD §1 pillar 4). The UI localises [kind]. */
@Serializable
data class BreakdownItem(val kind: Kind, val value: Double) {
    enum class Kind { BASE, DIFFICULTY, STREAK, VERIFICATION, CRIT, DAILY_CAP }
}

/** The full result of completing one quest (GD §4, §9.1). */
data class QuestReward(
    val xp: Int,
    val statXp: Map<Stat, Int>,
    val gold: Int,
    val crit: Boolean,
    /** Non-null when the daily cap for this source reduced the reward. */
    val cappedSource: VerificationSource?,
    val fragments: Int,
    val breakdown: List<BreakdownItem>,
)
