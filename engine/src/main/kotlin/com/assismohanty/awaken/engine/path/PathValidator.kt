package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The gate between model output and the game (GD §6.6.4; CLAUDE.md invariant 5). Pure, total and never throws: any
 * input yields kept blueprints plus a reason for every drop. Rules run in order V1 → V9; a drop rule discards, a clamp
 * rule repairs (ENGINE_DECISIONS #57-#62).
 */
object PathValidator {
    const val EST_MINUTES_MIN = 5
    const val EST_MINUTES_MAX = 120
    const val BUDGET_FACTOR = 1.2
    const val MIN_MILESTONES = 4
    const val MAX_MILESTONES = 10

    private val json = Json { isLenient = false }

    // ---- WEEK_QUESTS ---------------------------------------------------------------------------------------------

    fun validateWeek(responseJson: String, context: ValidationContext): WeekValidation {
        val root = parseObject(responseJson)
            ?: return WeekValidation(emptyList(), listOf(DropReason(-1, DropReason.Rule.V1, "response is not a JSON object")), false)
        val suggestAdvance = (root["suggestAdvance"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: false
        val items = root["blueprints"] as? JsonArray
            ?: return WeekValidation(emptyList(), listOf(DropReason(-1, DropReason.Rule.V1, "no blueprints array")), suggestAdvance)

        val drops = ArrayList<DropReason>()
        // V1-V6: per blueprint, independent of the others.
        val repaired = items.mapIndexedNotNull { index, element ->
            when (val result = checkOne(index, element, context)) {
                is One.Kept -> result.blueprint
                is One.Dropped -> {
                    drops += result.reason
                    null
                }
            }
        }
        // V7: duplicates against this batch (earlier survivors) and the Path's last 14 days.
        val seen = context.recentTitles.map(PathText::duplicateKey).filter { it.isNotEmpty() }.toMutableSet()
        val unique = repaired.filter { blueprint ->
            val key = PathText.duplicateKey(blueprint.title)
            if (key.isNotEmpty() && !seen.add(key)) {
                drops += DropReason(blueprint.index, DropReason.Rule.V7, "duplicate title \"${blueprint.title}\"")
                false
            } else {
                true
            }
        }
        // V8: keep in order while the cumulative estimate fits round(weeklyMinutes × 1.2); the first misfit ends it.
        val budget = Math.round(context.path.weeklyMinutes * BUDGET_FACTOR)
        var used = 0L
        var overBudget = false
        val withinBudget = unique.filter { blueprint ->
            if (!overBudget && used + blueprint.estMinutes <= budget) {
                used += blueprint.estMinutes
                true
            } else {
                overBudget = true
                drops += DropReason(blueprint.index, DropReason.Rule.V8, "over the weekly budget of $budget min")
                false
            }
        }
        // V9: at most n = activeDays × questsPerDay.
        val limit = context.path.weeklyQuestLimit
        withinBudget.drop(limit).forEach { drops += DropReason(it.index, DropReason.Rule.V9, "more than $limit quests") }
        return WeekValidation(withinBudget.take(limit), drops.sortedBy { it.index }, suggestAdvance)
    }

    private sealed interface One {
        data class Kept(val blueprint: ValidatedBlueprint) : One
        data class Dropped(val reason: DropReason) : One
    }

    private fun checkOne(index: Int, element: JsonElement, context: ValidationContext): One {
        fun drop(rule: DropReason.Rule, detail: String) = One.Dropped(DropReason(index, rule, detail))
        val obj = element as? JsonObject ?: return drop(DropReason.Rule.V1, "not an object")

        // V1: every required field present with the right JSON type, every enum known.
        val rawTitle = obj.string("title") ?: return drop(DropReason.Rule.V1, "title missing")
        val rawDescription = obj.string("description") ?: return drop(DropReason.Rule.V1, "description missing")
        val stat = obj.enumValue<Stat>("stat") ?: return drop(DropReason.Rule.V1, "unknown stat")
        val unit = obj.enumValue<PathUnit>("unit") ?: return drop(DropReason.Rule.V1, "unknown unit")
        val effort = obj.enumValue<Difficulty>("effort") ?: return drop(DropReason.Rule.V1, "unknown effort")
        val mode = obj.enumValue<PathMode>("mode") ?: return drop(DropReason.Rule.V1, "unknown mode")
        val target = obj.number("target") ?: return drop(DropReason.Rule.V1, "target missing or not a number")
        val estMinutes = obj.number("estMinutes") ?: return drop(DropReason.Rule.V1, "estMinutes missing or not a number")
        val tags = when (val raw = obj["tags"]) {
            null, JsonNull -> emptySet()
            is JsonArray -> raw.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .let { list -> if (list.any { it == null || it !in PathTags.ALL }) return drop(DropReason.Rule.V1, "unknown tag") else list.filterNotNull().toSet() }
            else -> return drop(DropReason.Rule.V1, "tags not an array")
        }
        val rawRationale = when (val raw = obj["rationale"]) {
            null, JsonNull -> ""
            else -> (raw as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return drop(DropReason.Rule.V1, "rationale not a string")
        }

        // V2: clean strings; an empty title after cleaning drops.
        val title = PathText.clean(rawTitle, PathText.TITLE_MAX)
        if (title.isEmpty()) return drop(DropReason.Rule.V2, "title empty after cleaning")
        val description = PathText.clean(rawDescription, PathText.DESCRIPTION_MAX)
        val rationale = PathText.clean(rawRationale, PathText.RATIONALE_MAX)

        // V3: a stat outside the Path's stats becomes the primary stat.
        val path = context.path
        val keptStat = if (stat == path.primaryStat || stat == path.secondaryStat) stat else path.primaryStat
        // V4-V5: clamp numbers (rounded half-up first; ENGINE_DECISIONS #58).
        val keptTarget = clampRounded(target, unit.min, unit.max)
        val keptMinutes = clampRounded(estMinutes, EST_MINUTES_MIN, EST_MINUTES_MAX)

        // V6: injury tags and unowned equipment drop.
        tags.firstOrNull { it in context.injuryTags }?.let { return drop(DropReason.Rule.V6, "tag \"$it\" excluded by Injury Mode") }
        EquipmentKeywords.unownedMention("$title $description", context.ownedEquipment)
            ?.let { return drop(DropReason.Rule.V6, "needs equipment \"$it\"") }

        return One.Kept(
            ValidatedBlueprint(
                index = index,
                title = title,
                description = description,
                stat = keptStat,
                unit = unit,
                target = keptTarget,
                effort = effort,
                estMinutes = keptMinutes,
                mode = mode,
                tags = tags,
                rationale = rationale,
            ),
        )
    }

    // ---- PLAN_PATH -----------------------------------------------------------------------------------------------

    /** GD §6.6.2: V2 on every field, drop empty titles, keep the first 10; fewer than 4 left fails the plan. */
    fun validatePlan(responseJson: String): PlanValidation {
        val items = parseObject(responseJson)?.get("milestones") as? JsonArray ?: return PlanValidation.Failed(0)
        val milestones = items.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj.string("title")?.let { PathText.clean(it, PathText.TITLE_MAX) }.orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            Triple(
                title,
                obj.string("summary")?.let { PathText.clean(it, PathText.SUMMARY_MAX) }.orEmpty(),
                obj.string("exitCriterion")?.let { PathText.clean(it, PathText.EXIT_CRITERION_MAX) }.orEmpty(),
            )
        }.take(MAX_MILESTONES)
        if (milestones.size < MIN_MILESTONES) return PlanValidation.Failed(milestones.size)
        return PlanValidation.Valid(milestones.mapIndexed { i, (title, summary, exit) -> Milestone(i, title, summary, exit) })
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private fun parseObject(text: String): JsonObject? = try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization reports malformed JSON as SerializationException, an IllegalArgumentException.
        null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private inline fun <reified E : Enum<E>> JsonObject.enumValue(key: String): E? {
        val name = string(key) ?: return null
        return enumValues<E>().firstOrNull { it.name == name }
    }

    /** Half-up rounding, then clamp; infinities and huge values land on the bounds instead of overflowing. */
    private fun clampRounded(value: Double, min: Int, max: Int): Int = when {
        value.isNaN() -> min
        value >= max -> max
        value <= min -> min
        // Math.round is half-up (GD §4.1 rounding); the value is already inside [min, max], so the cast is safe.
        else -> Math.round(value).toInt().coerceIn(min, max)
    }
}
