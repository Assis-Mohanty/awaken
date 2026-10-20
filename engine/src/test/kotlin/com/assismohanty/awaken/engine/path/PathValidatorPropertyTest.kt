package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import kotlin.random.Random

/** The validator is total, and every survivor and every price respects the clamps whatever the model sends. */
class PathValidatorPropertyTest {

    private val random = Random(20261019)

    @Test
    fun `1000 random blueprints never throw and every kept blueprint satisfies all clamps`() {
        var blueprints = 0
        var kept = 0
        while (blueprints < 1_000) {
            val count = random.nextInt(0, 16)
            blueprints += count
            val context = randomContext()
            val response = JsonObject(
                mapOf(
                    "promptVersion" to JsonPrimitive("week_quests.v1"),
                    "suggestAdvance" to pick(JsonPrimitive(true), JsonPrimitive("true"), JsonNull, JsonPrimitive(1)),
                    "blueprints" to JsonArray(List(count) { randomBlueprint() }),
                ),
            ).toString()

            val result = PathValidator.validateWeek(response, context)
            assertThat(result.kept.size + result.drops.size).isEqualTo(count)
            kept += result.kept.size
            assertSatisfiesClamps(result.kept, context)
            PathPricing.price(result.kept).forEach(::assertPricedWithinRules)
        }
        // The generator must exercise the keep path too, or the clamps above prove nothing.
        assertThat(kept).isGreaterThan(100)
    }

    @Test
    fun `no SELF priced blueprint is above NORMAL and every GATE is timer-verified`() {
        repeat(1_000) {
            val blueprint = ValidatedBlueprint(
                index = it,
                title = "t$it",
                description = "",
                stat = Stat.INT,
                unit = PathUnit.entries.random(random),
                target = 1,
                effort = Difficulty.entries.random(random),
                estMinutes = random.nextInt(PathValidator.EST_MINUTES_MIN, PathValidator.EST_MINUTES_MAX + 1),
                mode = PathMode.entries.random(random),
                tags = emptySet(),
                rationale = "",
            )
            assertPricedWithinRules(PathPricing.price(blueprint))
        }
    }

    @Test
    fun `garbage responses never throw`() {
        val garbage = listOf(
            "", "null", "[]", "{", "{\"blueprints\":null}", "{\"blueprints\":{}}", "{\"blueprints\":[1,\"x\",null,[]]}",
            "\u0000\u0001", "{\"blueprints\":[{\"title\":${"9".repeat(400)}}]}", "{\"milestones\":[{}]}",
        )
        garbage.forEach {
            PathValidator.validateWeek(it, PathFixtures.context())
            PathValidator.validatePlan(it)
        }
    }

    private fun assertSatisfiesClamps(kept: List<ValidatedBlueprint>, context: ValidationContext) {
        val path = context.path
        kept.forEach { b ->
            val message = assertWithMessage(b.toString())
            message.that(b.title).isNotEmpty()
            listOf(b.title to PathText.TITLE_MAX, b.description to PathText.DESCRIPTION_MAX, b.rationale to PathText.RATIONALE_MAX)
                .forEach { (text, max) ->
                    message.that(text.codePointCount(0, text.length)).isAtMost(max)
                    message.that(text).doesNotContainMatch("""https?://""")
                    message.that(text).doesNotContainMatch("""\p{Cc}|\s{2,}|^\s|\s$""")
                }
            message.that(b.stat).isAnyOf(path.primaryStat, path.secondaryStat ?: path.primaryStat)
            message.that(b.target).isIn(b.unit.min..b.unit.max)
            message.that(b.estMinutes).isIn(PathValidator.EST_MINUTES_MIN..PathValidator.EST_MINUTES_MAX)
            message.that(PathTags.ALL).containsAtLeastElementsIn(b.tags)
            message.that(b.tags.intersect(context.injuryTags)).isEmpty()
            message.that(EquipmentKeywords.unownedMention("${b.title} ${b.description}", context.ownedEquipment)).isNull()
        }
        assertThat(kept.sumOf { it.estMinutes }.toLong()).isAtMost(Math.round(path.weeklyMinutes * PathValidator.BUDGET_FACTOR))
        assertThat(kept.size).isAtMost(path.weeklyQuestLimit)
        assertThat(kept.map { PathText.duplicateKey(it.title) }).containsNoDuplicates()
        assertThat(kept.map { it.index }).isInOrder()
    }

    private fun assertPricedWithinRules(priced: PricedBlueprint) {
        when (priced.blueprint.mode) {
            PathMode.SELF -> {
                assertThat(priced.difficulty).isAtMost(Difficulty.NORMAL)
                assertThat(priced.verification).isEqualTo(Verification.SELF)
                assertThat(priced.minDurationSec).isIn(60..900)
                assertThat(priced.gateRank).isNull()
            }
            PathMode.GATE -> {
                assertThat(priced.verification).isEqualTo(Verification.TIMER)
                assertThat(priced.difficulty).isEqualTo(requireNotNull(priced.gateRank).difficulty)
            }
        }
    }

    private fun randomContext(): ValidationContext {
        val primary = Stat.entries.random(random)
        val secondary = (Stat.entries - primary).random(random).takeIf { random.nextBoolean() }
        val path = PathFixtures.path.copy(
            primaryStat = primary,
            secondaryStat = secondary,
            activeDays = DayOfWeek.entries.shuffled(random).take(random.nextInt(1, 8)).toSet(),
            questsPerDay = random.nextInt(1, 3),
            weeklyMinutes = random.nextInt(30, 901),
        )
        return PathFixtures.context(
            path = path,
            ownedEquipment = EquipmentKeywords.TABLE.keys.filter { random.nextBoolean() }.toSet(),
            injuryTags = setOf("impact", "legs", "upper").filter { random.nextInt(4) == 0 }.toSet(),
            recentTitles = List(random.nextInt(0, 3)) { "Quest ${random.nextInt(5)}" },
        )
    }

    private fun randomBlueprint(): JsonElement {
        if (random.nextInt(25) == 0) return pick(JsonPrimitive("junk"), JsonNull, JsonArray(emptyList()), JsonPrimitive(7))
        val fields = mutableMapOf(
            "title" to text(),
            "description" to text(),
            "stat" to enumish(Stat.entries.map { it.name }),
            "unit" to enumish(PathUnit.entries.map { it.name }),
            "target" to number(),
            "effort" to enumish(Difficulty.entries.map { it.name }),
            "estMinutes" to number(),
            "mode" to enumish(PathMode.entries.map { it.name }),
            "tags" to tags(),
            "rationale" to text(),
        )
        // Sometimes a field is missing or has the wrong JSON type.
        if (random.nextInt(10) == 0) fields.remove(fields.keys.random(random))
        if (random.nextInt(10) == 0) fields[fields.keys.random(random)] = pick(JsonNull, JsonPrimitive(false), JsonArray(emptyList()))
        return JsonObject(fields)
    }

    private fun text(): JsonPrimitive {
        val parts = listOf(
            "Graph Hunt", "Read", "Quest ${random.nextInt(5)}", "  ", "\n\t", "\u0000", "‮", "http://evil.example/x",
            "https://a.b", "dumbbell", "gym", "pull-up bar", "😀", "日本語", "x".repeat(random.nextInt(0, 400)), "!!!",
        )
        return JsonPrimitive(List(random.nextInt(0, 6)) { parts.random(random) }.joinToString(""))
    }

    private fun enumish(valid: List<String>): JsonElement = when (random.nextInt(20)) {
        0 -> JsonPrimitive(valid.random(random).lowercase())
        1 -> JsonPrimitive("CALORIES")
        2 -> JsonPrimitive(3)
        else -> JsonPrimitive(valid.random(random))
    }

    private fun number(): JsonElement = when (random.nextInt(12)) {
        0 -> JsonPrimitive(-random.nextLong(0, Long.MAX_VALUE))
        1 -> JsonPrimitive(Long.MAX_VALUE)
        2 -> JsonPrimitive(1e308)
        3 -> JsonPrimitive(-1e308)
        4 -> JsonPrimitive(random.nextDouble(-10.0, 500.0))
        5 -> JsonPrimitive("40")
        else -> JsonPrimitive(random.nextInt(0, 200))
    }

    private fun tags(): JsonElement {
        val vocabulary = PathTags.ALL.toList() + listOf("gym", "IMPACT", "")
        return when (random.nextInt(10)) {
            0 -> JsonPrimitive("impact")
            1 -> JsonNull
            else -> JsonArray(List(random.nextInt(0, 4)) { JsonPrimitive(if (random.nextInt(5) == 0) vocabulary.random(random) else PathTags.ALL.random(random)) })
        }
    }

    private fun <T> pick(vararg options: T): T = options.random(random)
}
