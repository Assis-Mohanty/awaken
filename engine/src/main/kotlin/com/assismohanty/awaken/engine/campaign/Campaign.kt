package com.assismohanty.awaken.engine.campaign

import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.Fnv1a
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestTemplate
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Lifecycle of a 45-day campaign (GD §6.8). ENDED = day 45 passed below the completion bar. */
enum class CampaignStatus { ACTIVE, COMPLETED, ENDED, ABANDONED }

/** The three acts of 15 days. [base] is the habit difficulty the act asks for before the adaptive cap. */
enum class CampaignAct(val base: Difficulty) {
    FOUNDATION(Difficulty.EASY),
    BUILD(Difficulty.NORMAL),
    ASCEND(Difficulty.HARD),
}

/** Who the scan says you are: named after the weakest stat, the one the campaign starts with. */
enum class Archetype(val stat: Stat) {
    REBUILDER(Stat.STR),
    SCHOLAR(Stat.INT),
    IRON_WILL(Stat.WIL),
    RESTORER(Stat.VIT),
    ENVOY(Stat.CHA),
}

/** The 45-day campaign's fixed rules (GD §6.8). Missed days never reset anything (GD §1 pillar 3). */
object CampaignRules {
    const val LENGTH_DAYS = 45
    const val ACT_DAYS = 15
    /** ≥ 70 % of 45 days cleared, rounded up. */
    const val COMPLETION_CLEARED_DAYS = 32
    const val REWARD_ACHIEVEMENT_ID = "reforged"
    const val MIN_HABITS = 3
    const val MAX_HABITS = 5

    fun endDate(start: LocalDate): LocalDate = start.plusDays(LENGTH_DAYS - 1L)

    /** 1-based day of the campaign on [date], or null outside it. */
    fun dayIndex(start: LocalDate, date: LocalDate): Int? {
        val index = ChronoUnit.DAYS.between(start, date).toInt() + 1
        return index.takeIf { it in 1..LENGTH_DAYS }
    }

    fun act(dayIndex: Int): CampaignAct = CampaignAct.entries[((dayIndex - 1) / ACT_DAYS).coerceIn(0, CampaignAct.entries.size - 1)]

    /** The act's tier, never more than one step above the stat's adaptive tier (GD §6.4), so a struggling stat isn't pushed. */
    fun habitDifficulty(act: CampaignAct, statTier: Difficulty): Difficulty =
        minOf(act.base, statTier.harder())

    /** True once [date] is past the last day. */
    fun isOver(start: LocalDate, date: LocalDate): Boolean = date.isAfter(endDate(start))

    fun outcome(clearedDays: Int): CampaignStatus =
        if (clearedDays >= COMPLETION_CLEARED_DAYS) CampaignStatus.COMPLETED else CampaignStatus.ENDED

    /** Whether the bar is still reachable with [daysLeft] days to go (UI hint only). */
    fun stillReachable(clearedDays: Int, daysLeft: Int): Boolean = clearedDays + daysLeft >= COMPLETION_CLEARED_DAYS

    /** Campaign habits a hunter can take with [activeHabits] already running; 0 = not enough room (needs [MIN_HABITS]). */
    fun habitCount(activeHabits: Int, maxActive: Int): Int {
        val free = maxActive - activeHabits
        return if (free < MIN_HABITS) 0 else minOf(MAX_HABITS, free)
    }
}

/** The campaign scan (GD §6.8): two questions per stat, answered 0 (never) … 3 (almost always). */
object CampaignScan {
    const val MAX_ANSWER = 3

    /** Question ids in display order; the app holds the text. */
    val QUESTIONS: List<Pair<String, Stat>> = listOf(
        "str_move" to Stat.STR, "str_strength" to Stat.STR,
        "int_learn" to Stat.INT, "int_focus" to Stat.INT,
        "wil_follow" to Stat.WIL, "wil_phone" to Stat.WIL,
        "vit_sleep" to Stat.VIT, "vit_fuel" to Stat.VIT,
        "cha_connect" to Stat.CHA, "cha_speak" to Stat.CHA,
    )

    /** Sum per stat, 0..6. Missing answers count as 0. */
    fun scores(answers: Map<String, Int>): Map<Stat, Int> =
        Stat.entries.associateWith { stat ->
            QUESTIONS.filter { it.second == stat }.sumOf { (id, _) -> (answers[id] ?: 0).coerceIn(0, MAX_ANSWER) }
        }

    /** Stats from weakest to strongest; ties keep stat order. */
    fun ranked(scores: Map<Stat, Int>): List<Stat> = Stat.entries.sortedBy { scores[it] ?: 0 }

    fun weakest(scores: Map<Stat, Int>): Stat = ranked(scores).first()

    fun archetype(scores: Map<Stat, Int>): Archetype = Archetype.entries.first { it.stat == weakest(scores) }

    fun isComplete(answers: Map<String, Int>): Boolean = QUESTIONS.all { (id, _) -> answers[id] in 0..MAX_ANSWER }
}

/** One planned campaign habit; [templateId] lets later acts re-price it from the same template. */
data class CampaignHabitPlan(
    val templateId: String,
    val stat: Stat,
    val title: String,
    val unit: String,
    val days: Set<DayOfWeek>,
)

/**
 * Turns the scan into habits (GD §6.8): the weakest stat gets two, the next three weakest one each, so the strongest
 * stat is left to the daily directive. Deterministic per hunter; SELF templates that support every act's difficulty.
 */
object CampaignPlanner {
    private val DAILY = DayOfWeek.entries.toSet()
    private val MON_WED_FRI = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    private val TUE_THU_SAT = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)

    fun plan(templates: List<QuestTemplate>, context: HunterContext, scores: Map<Stat, Int>, count: Int): List<CampaignHabitPlan> {
        if (count <= 0) return emptyList()
        val ranked = CampaignScan.ranked(scores)
        // Slot stats and schedules: weakest twice (daily + MWF), then one each on alternating days.
        val slots = listOf(
            ranked[0] to DAILY,
            ranked[0] to MON_WED_FRI,
            ranked[1] to TUE_THU_SAT,
            ranked[2] to MON_WED_FRI,
            ranked[3] to TUE_THU_SAT,
        ).take(count)
        val pool = DirectiveGenerator.eligible(templates, context.copy(recoveryMode = false)).filter { template ->
            template.verification.source == VerificationSource.SELF &&
                CampaignAct.entries.all { template.targets.containsKey(it.base) }
        }
        val used = HashSet<String>()
        return slots.mapIndexedNotNull { index, (stat, days) ->
            val options = pool.filter { it.stat == stat && it.id !in used }.ifEmpty { return@mapIndexedNotNull null }
            val pick = options[Math.floorMod(Fnv1a.hash64("${context.hunterId}|campaign|$index"), options.size.toLong()).toInt()]
            used += pick.id
            val titleIndex = Math.floorMod(Fnv1a.hash64("${context.hunterId}|campaign-title|${pick.id}"), pick.titles.size.toLong()).toInt()
            CampaignHabitPlan(pick.id, pick.stat, pick.titles[titleIndex], pick.unit, days)
        }
    }

    /** The target a campaign habit uses at [difficulty], from its template. */
    fun target(template: QuestTemplate, difficulty: Difficulty): Int = template.targets.getValue(difficulty)
}
