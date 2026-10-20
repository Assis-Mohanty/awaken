package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Directive
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestDraft
import com.assismohanty.awaken.engine.model.QuestTemplate
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.SplittableRandom
import java.util.UUID

/**
 * Deterministic Daily Directive generation (GD §6.3). Same `(hunterId, gameDate)` + same inputs → identical output,
 * including the deterministic quest instance ids (so regeneration is idempotent at the ledger and crit level).
 *
 * Picking is two-stage: a stat by weight (focus 2.0 × neglected 1.5), then a template uniformly within that stat, so
 * a stat with more templates is not favoured. The pool is sorted by id so JSON order never changes results.
 */
object DirectiveGenerator {
    const val SIDE_COUNT = 3
    const val NOVELTY_WINDOW_DAYS = 7L
    const val FOCUS_WEIGHT = 2.0
    const val NEGLECT_WEIGHT = 1.5
    const val NEGLECT_DAYS = 3L
    const val PENALTY_TAG = "penalty"
    const val FREE_REROLLS_PER_DAY = 1
    const val PAID_REROLLS_PER_DAY = 3
    const val REROLL_COST_GOLD = 40
    /** Body-area tags Injury Mode can exclude (DS §4.7), and the default pair (ENGINE_DECISIONS #11, #46). */
    val INJURY_TAGS: List<String> = listOf("impact", "legs", "upper")
    val DEFAULT_INJURY_TAGS: Set<String> = setOf("impact", "legs")
    val RECOVERY_STATS: List<Stat> = listOf(Stat.VIT, Stat.WIL)
    const val RECOVERY_EXCLUDED_TAG = "impact"
    /** A first-day starter must be doable wherever the hunter is when they finish onboarding. */
    const val STARTER_EXCLUDED_TAG = "outdoor"

    /** Game-dates whose template ids form the novelty window for [gameDate]: the 7 days before it. */
    fun noveltyWindow(gameDate: LocalDate): ClosedRange<LocalDate> =
        gameDate.minusDays(NOVELTY_WINDOW_DAYS)..gameDate.minusDays(1)

    /**
     * [pathQuests] are today's Path quests (GD §6.6.6), already validated, priced and capped at
     * [com.assismohanty.awaken.engine.path.PathScheduler.MAX_PATH_QUESTS_PER_DAY], in priority order (Path creation,
     * then blueprint index). With none, the output is exactly the template-only directive (vector 25).
     */
    fun generate(
        templates: List<QuestTemplate>,
        context: HunterContext,
        gameDate: LocalDate,
        pathQuests: List<QuestDraft> = emptyList(),
    ): Directive {
        val rng = SplittableRandom(seed(context.hunterId, gameDate, "directive"))
        val pool = eligible(templates, context).filter { it.id !in context.recentTemplateIds }

        val mainStatPreference = mainStatFor(context, gameDate)
        // Main promotion (GD §6.6.6): a Path quest in today's focus stat, highest priced difficulty, earliest on a tie.
        // It keeps its own priced difficulty and verification (ENGINE_DECISIONS #68).
        val promoted = pathQuests.filter { it.stat == mainStatPreference }.maxByOrNull { it.difficulty.ordinal }
        val main = promoted?.copy(type = QuestType.MAIN) ?: run {
            val mainCandidates = pool.filter { it.stat == mainStatPreference && it.supports(mainDifficulty(context, it.stat)) }
                .ifEmpty { pool.filter { it.supports(mainDifficulty(context, it.stat)) } }
            pickWeighted(mainCandidates, context, gameDate, rng)?.let { template ->
                draft(template, QuestType.MAIN, mainDifficulty(context, template.stat), context, gameDate, "main", rng)
            }
        }
        val paths = pathQuests.filter { it !== promoted }.map { it.copy(type = QuestType.PATH) }
        val sideCount = sideCount(pathQuests.size)

        val used = mutableSetOf<String>().apply { main?.templateId?.let { add(it) } }
        val sides = ArrayList<QuestDraft>(sideCount)
        if (context.firstDirective) {
            // GD §6.3: the first directive guarantees one Easy quest in a focus stat that can be done right now.
            val starters = pool.filter {
                it.stat in context.focusStats && it.verification.source == VerificationSource.SELF &&
                    STARTER_EXCLUDED_TAG !in it.tags && it.supports(Difficulty.EASY) && it.id !in used
            }
            pickWeighted(starters, context, gameDate, rng)?.let { template ->
                used += template.id
                sides += draft(template, QuestType.SIDE, Difficulty.EASY, context, gameDate, "side0", rng)
            }
        }
        for (index in sides.size until sideCount) {
            val template = pickSide(pool, used, main?.stat, context, gameDate, rng) ?: break
            used += template.id
            sides += draft(template, QuestType.SIDE, context.tier(template.stat), context, gameDate, "side$index", rng)
        }
        return Directive(gameDate, main, sides, paths)
    }

    /** Sides = `max(1, 3 − pathQuestsToday)` (GD §6.6.6): with 3 Path quests there is still one template side. */
    fun sideCount(pathQuestsToday: Int): Int = maxOf(1, SIDE_COUNT - pathQuestsToday)

    /**
     * Which quests a reroll may target (GD §6.3): a side quest or a non-promoted Path quest; never the main quest, even
     * when it is a promoted Path quest (that one is type MAIN). A Path quest reroll first tries the Path's next
     * unscheduled blueprint ([com.assismohanty.awaken.engine.path.PathScheduler.rerollPick]), then a template side.
     */
    fun canReroll(type: QuestType): Boolean = type == QuestType.SIDE || type == QuestType.PATH

    /**
     * Replaces one side (or Path) quest with a template side. [rerollNumber] is 1-based per day; [alsoExclude] holds
     * templates already rerolled away today. Returns null when nothing eligible remains. The main quest can never be
     * rerolled.
     */
    fun rerollSide(
        templates: List<QuestTemplate>,
        context: HunterContext,
        current: Directive,
        rerollNumber: Int,
        alsoExclude: Set<String> = emptySet(),
    ): QuestDraft? {
        require(rerollNumber in 1..FREE_REROLLS_PER_DAY + PAID_REROLLS_PER_DAY) { "reroll limit exceeded" }
        val gameDate = current.gameDate
        val rng = SplittableRandom(seed(context.hunterId, gameDate, "reroll$rerollNumber"))
        val pool = eligible(templates, context).filter { it.id !in context.recentTemplateIds }
        val used = current.quests.mapNotNullTo(HashSet()) { it.templateId } + alsoExclude
        val template = pickSide(pool, used, current.main?.stat, context, gameDate, rng) ?: return null
        return draft(template, QuestType.SIDE, context.tier(template.stat), context, gameDate, "reroll$rerollNumber", rng)
    }

    /** Gold cost of the [rerollNumber]-th reroll today (1-based): first free, next three 40 each, then not allowed. */
    fun rerollCost(rerollNumber: Int): Int? = when {
        rerollNumber < 1 -> null
        rerollNumber <= FREE_REROLLS_PER_DAY -> 0
        rerollNumber <= FREE_REROLLS_PER_DAY + PAID_REROLLS_PER_DAY -> REROLL_COST_GOLD
        else -> null
    }

    /** Penalty quest after a failed day (GD §7.3): Normal difficulty, from templates tagged `penalty`. */
    fun penaltyQuest(templates: List<QuestTemplate>, context: HunterContext, gameDate: LocalDate): QuestDraft? {
        val rng = SplittableRandom(seed(context.hunterId, gameDate, "penalty"))
        val candidates = templates
            .filter { PENALTY_TAG in it.tags && it.isAllowed(context) && it.supports(Difficulty.NORMAL) }
            .sortedBy { it.id }
        if (candidates.isEmpty()) return null
        val template = candidates[rng.nextInt(candidates.size)]
        return draft(template, QuestType.PENALTY, Difficulty.NORMAL, context, gameDate, "penalty", rng)
    }

    /**
     * Today's Rank Trial stage quests (GD §5, ENGINE_DECISIONS #31): one Hard TRIAL quest per open stage [stats].
     * Equipment/injury/Health filters apply; recovery mode and the novelty window do not (a trial is a chosen gate).
     * SELF templates are preferred so every stage can be cleared by hand; slot `trial-<STAT>` keeps ids stable per day.
     */
    fun trialStages(templates: List<QuestTemplate>, context: HunterContext, stats: List<Stat>, gameDate: LocalDate): List<QuestDraft> {
        val pool = eligible(templates, context.copy(recoveryMode = false)).filter { it.supports(Difficulty.HARD) }
        return stats.mapNotNull { stat ->
            val inStat = pool.filter { it.stat == stat }
            val candidates = inStat.filter { it.verification.source == VerificationSource.SELF }.ifEmpty { inStat }
            if (candidates.isEmpty()) return@mapNotNull null
            val slot = "trial-${stat.name}"
            val rng = SplittableRandom(seed(context.hunterId, gameDate, slot))
            draft(candidates[rng.nextInt(candidates.size)], QuestType.TRIAL, Difficulty.HARD, context, gameDate, slot, rng)
        }
    }

    /** Templates usable for normal generation before the novelty filter. */
    fun eligible(templates: List<QuestTemplate>, context: HunterContext): List<QuestTemplate> =
        templates.filter { PENALTY_TAG !in it.tags && it.isAllowed(context) }.sortedBy { it.id }

    private fun QuestTemplate.isAllowed(context: HunterContext): Boolean {
        if (!context.ownedEquipment.containsAll(equipment)) return false
        if (tags.any { it in context.injuryTags }) return false
        if (!context.healthAvailable && verification.source == VerificationSource.HEALTH) return false
        if (context.recoveryMode && (stat !in RECOVERY_STATS || RECOVERY_EXCLUDED_TAG in tags)) return false
        return true
    }

    private fun QuestTemplate.supports(difficulty: Difficulty): Boolean = targets.containsKey(difficulty)

    private fun mainStatFor(context: HunterContext, gameDate: LocalDate): Stat {
        val rotation = if (context.recoveryMode) RECOVERY_STATS else context.focusStats
        return rotation[Math.floorMod(gameDate.toEpochDay(), rotation.size.toLong()).toInt()]
    }

    private fun mainDifficulty(context: HunterContext, stat: Stat): Difficulty = context.tier(stat).harder()

    private fun pickSide(
        pool: List<QuestTemplate>,
        used: Set<String>,
        mainStat: Stat?,
        context: HunterContext,
        gameDate: LocalDate,
        rng: SplittableRandom,
    ): QuestTemplate? {
        val candidates = pool.filter { it.id !in used && it.supports(context.tier(it.stat)) }
        val avoidingMainStat = candidates.filter { it.stat != mainStat }
        return pickWeighted(avoidingMainStat.ifEmpty { candidates }, context, gameDate, rng)
    }

    /** Stat weight for side generation (GD §6.1): focus 2.0 × neglected 1.5, else 1.0. */
    fun weight(stat: Stat, context: HunterContext, gameDate: LocalDate): Double {
        var weight = 1.0
        if (stat in context.focusStats) weight *= FOCUS_WEIGHT
        if (isNeglected(stat, context, gameDate)) weight *= NEGLECT_WEIGHT
        return weight
    }

    /** A stat is neglected when it has had no completion for 3+ game-days (or never). */
    fun isNeglected(stat: Stat, context: HunterContext, gameDate: LocalDate): Boolean {
        val last = context.lastActiveDates[stat] ?: return true
        return ChronoUnit.DAYS.between(last, gameDate) >= NEGLECT_DAYS
    }

    private fun pickWeighted(
        candidates: List<QuestTemplate>,
        context: HunterContext,
        gameDate: LocalDate,
        rng: SplittableRandom,
    ): QuestTemplate? {
        if (candidates.isEmpty()) return null
        val byStat = candidates.groupBy { it.stat }
        val stats = Stat.entries.filter { it in byStat }
        val weights = stats.map { weight(it, context, gameDate) }
        var roll = rng.nextDouble() * weights.sum()
        var chosen = stats.last()
        for ((i, stat) in stats.withIndex()) {
            roll -= weights[i]
            if (roll < 0) {
                chosen = stat
                break
            }
        }
        val options = byStat.getValue(chosen)
        return options[rng.nextInt(options.size)]
    }

    private fun draft(
        template: QuestTemplate,
        type: QuestType,
        difficulty: Difficulty,
        context: HunterContext,
        gameDate: LocalDate,
        slot: String,
        rng: SplittableRandom,
    ): QuestDraft {
        val target = template.targets.getValue(difficulty)
        return QuestDraft(
            questInstanceId = instanceId(context.hunterId, gameDate, slot),
            gameDate = gameDate,
            type = type,
            templateId = template.id,
            stat = template.stat,
            secondaryStat = template.secondaryStat,
            title = template.titles[rng.nextInt(template.titles.size)],
            description = template.description.replace("{target}", target.toString()),
            unit = template.unit,
            target = target,
            difficulty = difficulty,
            verification = template.verification,
            minDurationSec = template.minDurationSec,
        )
    }

    /** Deterministic name-based UUID per `(hunterId, gameDate, slot)`. */
    fun instanceId(hunterId: String, gameDate: LocalDate, slot: String): String =
        UUID.nameUUIDFromBytes("$hunterId|$gameDate|$slot".encodeToByteArray()).toString()

    /** `hash(hunterId, gameDate)` (GD §6.3), namespaced by purpose so rerolls don't replay the directive's rolls. */
    private fun seed(hunterId: String, gameDate: LocalDate, purpose: String): Long =
        Fnv1a.hash64("$hunterId|$gameDate|$purpose")
}
