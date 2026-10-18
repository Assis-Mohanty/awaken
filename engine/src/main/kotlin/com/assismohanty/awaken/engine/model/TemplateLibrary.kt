package com.assismohanty.awaken.engine.model

import com.assismohanty.awaken.engine.DirectiveGenerator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The bundled `assets/quests/templates.json` (GD §6.2). The vocabularies list every equipment id, tag and unit a template
 * may use, so typos are caught by [validate] rather than silently filtering templates out of generation.
 */
@Serializable
data class TemplateLibrary(
    val version: Int,
    val equipment: List<String>,
    val tags: List<String>,
    val units: List<String>,
    val templates: List<QuestTemplate>,
) {
    /** Templates for normal generation (everything except the penalty set). */
    val regular: List<QuestTemplate> get() = templates.filter { DirectiveGenerator.PENALTY_TAG !in it.tags }

    /** The penalty set (GD §7.3). */
    val penalty: List<QuestTemplate> get() = templates.filter { DirectiveGenerator.PENALTY_TAG in it.tags }

    /** Human-readable problems; empty means the library is valid. */
    fun validate(): List<String> = buildList {
        templates.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("duplicate id: $it") }
        for (t in templates) {
            if (!ID_PATTERN.matches(t.id)) add("${t.id}: id must match ${ID_PATTERN.pattern}")
            if (t.titles.size !in 2..3) add("${t.id}: needs 2–3 titles")
            if (t.titles.any { it.isBlank() }) add("${t.id}: blank title")
            if (t.description.isBlank() || "{target}" !in t.description) add("${t.id}: description must contain {target}")
            if (t.secondaryStat == t.stat) add("${t.id}: secondaryStat equals stat")
            if (t.unit !in units) add("${t.id}: unknown unit ${t.unit}")
            (t.equipment - equipment.toSet()).forEach { add("${t.id}: unknown equipment $it") }
            (t.tags - tags.toSet()).forEach { add("${t.id}: unknown tag $it") }
            if (t.minDurationSec < 0) add("${t.id}: negative minDurationSec")
            if (t.verification == Verification.HEALTH_SLEEP && t.stat != Stat.VIT) add("${t.id}: HEALTH_SLEEP is VIT-only")

            val targets = Difficulty.entries.map { t.targets[it] }
            if (targets.any { it == null || it <= 0 }) {
                add("${t.id}: every difficulty needs a positive target")
            } else {
                val values = targets.filterNotNull()
                // Sleep is a fixed baseline (ENGINE_DECISIONS #18); everything else must get strictly harder.
                val ok = if (t.verification == Verification.HEALTH_SLEEP) {
                    values.zipWithNext().all { (a, b) -> b >= a }
                } else {
                    values.zipWithNext().all { (a, b) -> b > a }
                }
                if (!ok) add("${t.id}: targets must increase EASY → EXTREME")
            }
        }
        val perStat = regular.groupingBy { it.stat }.eachCount()
        Stat.entries.forEach { stat ->
            val count = perStat[stat] ?: 0
            if (count < MIN_PER_STAT) add("$stat has $count regular templates (min $MIN_PER_STAT)")
        }
        if (penalty.isEmpty()) add("no penalty templates")
    }

    companion object {
        const val MIN_PER_STAT = 25
        private val ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): TemplateLibrary = json.decodeFromString(serializer(), text)
    }
}
