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
            if (t.titles.size !in 1..3) add("${t.id}: needs 1–3 titles")
            t.titles.forEach { title -> titleProblems(title).forEach { add("${t.id}: title \"$title\" $it") } }
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

        /** GD §6.2 title rules: plain, action-first, sentence case, no numbers (the target line shows them). */
        const val MAX_TITLE_LENGTH = 40
        val TITLE_WORDS = 2..5

        /** Fantasy words that crept into the first library ("Fuel the Forge", "Arch of Power"). Whole words, any case. */
        val BANNED_TITLE_WORDS = setOf(
            "forge", "arcane", "descent", "power", "shadow", "gate", "gates", "realm", "rite", "ritual", "quest",
            "trial", "iron", "steel", "oath", "sanctum", "banner", "ally", "allies", "kin", "tome", "scroll",
            "hunter", "dungeon", "raid", "guild", "vigil", "ember", "flame", "storm", "siege", "spire", "throne",
            "vanguard", "protocol", "slay", "dragon", "blade", "shield", "battle", "fate", "vessel", "ration",
            "ledger", "herald", "bard", "envoy", "missive",
        )

        /** Problems with one title; empty means it follows the rules. */
        fun titleProblems(title: String): List<String> = buildList {
            if (title.isBlank()) {
                add("is blank")
                return@buildList
            }
            if (title.length > MAX_TITLE_LENGTH) add("is longer than $MAX_TITLE_LENGTH chars")
            val words = title.trim().split(Regex("\\s+"))
            if (words.size !in TITLE_WORDS) add("must have ${TITLE_WORDS.first}–${TITLE_WORDS.last} words")
            if (title.any { it.isDigit() } || "{" in title) add("must not contain numbers or placeholders")
            if (!title.first().isUpperCase() || words.drop(1).any { it.first().isUpperCase() }) add("must be sentence case")
            Regex("\\p{L}+").findAll(title.lowercase()).map { it.value }.filter { it in BANNED_TITLE_WORDS }
                .forEach { add("uses banned word \"$it\"") }
        }
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): TemplateLibrary = json.decodeFromString(serializer(), text)
    }
}
