package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Directive
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DirectiveGeneratorTest {

    private val templates = TemplateFixtures.library()
    private val date = LocalDate.of(2026, 10, 18)
    private val context = HunterContext(hunterId = "hunter-1", focusStats = listOf(Stat.STR, Stat.INT))

    @Test
    fun `same inputs produce identical output regardless of template order`() {
        val a = DirectiveGenerator.generate(templates, context, date)
        val b = DirectiveGenerator.generate(templates.shuffled(kotlin.random.Random(7)), context, date)
        assertThat(b).isEqualTo(a)
    }

    @Test
    fun `different hunters or dates produce different directives`() {
        val base = DirectiveGenerator.generate(templates, context, date)
        val otherHunter = DirectiveGenerator.generate(templates, context.copy(hunterId = "hunter-2"), date)
        val otherDay = DirectiveGenerator.generate(templates, context, date.plusDays(2))
        assertThat(otherHunter.quests.map { it.templateId }).isNotEqualTo(base.quests.map { it.templateId })
        assertThat(otherDay.quests.map { it.templateId }).isNotEqualTo(base.quests.map { it.templateId })
    }

    @Test
    fun `main is hard in the rotating focus stat and sides are normal in other stats`() {
        for (offset in 0L..1L) {
            val day = date.plusDays(offset)
            val directive = DirectiveGenerator.generate(templates, context, day)
            val main = requireNotNull(directive.main)
            assertThat(main.type).isEqualTo(QuestType.MAIN)
            assertThat(main.stat).isEqualTo(context.focusStats[Math.floorMod(day.toEpochDay(), 2L).toInt()])
            assertThat(main.difficulty).isEqualTo(Difficulty.HARD)
            assertThat(main.target).isEqualTo(40)
            assertThat(directive.sides).hasSize(3)
            directive.sides.forEach {
                assertThat(it.type).isEqualTo(QuestType.SIDE)
                assertThat(it.difficulty).isEqualTo(Difficulty.NORMAL)
                assertThat(it.stat).isNotEqualTo(main.stat)
                assertThat(it.description).isEqualTo("Do 20 things.")
            }
        }
    }

    @Test
    fun `tiers drive difficulty and main is one above its stat tier, capped at extreme`() {
        val tiered = context.copy(tiers = Stat.entries.associateWith { Difficulty.EXTREME })
        val directive = DirectiveGenerator.generate(templates, tiered, date)
        assertThat(directive.main?.difficulty).isEqualTo(Difficulty.EXTREME)
        directive.sides.forEach { assertThat(it.difficulty).isEqualTo(Difficulty.EXTREME) }
    }

    @Test
    fun `no template repeats within 7 days over a simulated 30-day run`() {
        val history = ArrayList<Directive>()
        var lastActive = emptyMap<Stat, LocalDate>()
        repeat(30) { dayIndex ->
            val day = date.plusDays(dayIndex.toLong())
            val window = DirectiveGenerator.noveltyWindow(day)
            val recent = history.filter { it.gameDate in window }.flatMap { d -> d.quests.map { it.templateId } }.toSet()
            val directive = DirectiveGenerator.generate(
                templates,
                context.copy(recentTemplateIds = recent, lastActiveDates = lastActive),
                day,
            )

            assertThat(directive.quests).hasSize(4)
            directive.quests.forEach { assertThat(it.templateId).isNotIn(recent) }
            history += directive
            lastActive = lastActive + directive.quests.associate { it.stat to day }
        }
    }

    @Test
    fun `respects equipment filter`() {
        val withoutGear = (0L until 30L).flatMap { DirectiveGenerator.generate(templates, context, date.plusDays(it)).quests }
        assertThat(withoutGear.map { it.templateId }).containsNoneIn(templates.filter { it.equipment.isNotEmpty() }.map { it.id })

        val pool = DirectiveGenerator.eligible(templates, context.copy(ownedEquipment = setOf("dumbbell")))
        assertThat(pool).hasSize(templates.size)
    }

    @Test
    fun `injury mode excludes impact and legs tags`() {
        val injured = context.copy(injuryMode = true)
        val picked = (0L until 30L).flatMap { DirectiveGenerator.generate(templates, injured, date.plusDays(it)).quests }
        val byId = templates.associateBy { it.id }
        picked.forEach { draft ->
            assertThat(byId.getValue(draft.templateId).tags).containsNoneOf("impact", "legs")
        }
        assertThat(picked.count { it.stat == Stat.STR }).isGreaterThan(0)
    }

    @Test
    fun `health templates are excluded when Health Connect is unavailable`() {
        val pool = DirectiveGenerator.eligible(templates, context.copy(healthAvailable = false))
        assertThat(pool.map { it.verification.source }).doesNotContain(VerificationSource.HEALTH)
    }

    @Test
    fun `recovery mode generates only VIT and WIL without impact`() {
        val directive = DirectiveGenerator.generate(templates, context.copy(recoveryMode = true), date)
        assertThat(directive.quests).hasSize(4)
        directive.quests.forEach { assertThat(it.stat).isAnyOf(Stat.VIT, Stat.WIL) }
    }

    @Test
    fun `returns 4 quests from a 4-template pool even when no template matches the main focus stat`() {
        val pool = (0 until 4).map { TemplateFixtures.template("cha_$it", Stat.CHA) }
        val directive = DirectiveGenerator.generate(pool, context, date)
        assertThat(directive.quests).hasSize(4)
        assertThat(directive.quests.map { it.templateId }).containsExactlyElementsIn(pool.map { it.id })
    }

    @Test
    fun `returns as many quests as the pool allows when it is smaller than 4`() {
        val pool = (0 until 2).map { TemplateFixtures.template("vit_$it", Stat.VIT) }
        assertThat(DirectiveGenerator.generate(pool, context, date).quests).hasSize(2)
        assertThat(DirectiveGenerator.generate(emptyList(), context, date).quests).isEmpty()
    }

    @Test
    fun `focus and neglect weights multiply`() {
        val recentAll = context.copy(lastActiveDates = Stat.entries.associateWith { date })
        assertThat(DirectiveGenerator.weight(Stat.STR, recentAll, date)).isEqualTo(2.0)
        assertThat(DirectiveGenerator.weight(Stat.CHA, recentAll, date)).isEqualTo(1.0)
        assertThat(DirectiveGenerator.weight(Stat.STR, context, date)).isEqualTo(3.0)
        assertThat(DirectiveGenerator.weight(Stat.CHA, recentAll, date.plusDays(3))).isEqualTo(1.5)
        assertThat(DirectiveGenerator.weight(Stat.CHA, recentAll, date.plusDays(2))).isEqualTo(1.0)
    }

    @Test
    fun `the off-duty focus stat is picked about twice as often as a non-focus stat for sides`() {
        // Every stat active "today" (relative to each generated day) so only the focus weight applies.
        val sides = (0L until 2_000L).map { date.plusDays(it) }
            .map { day -> DirectiveGenerator.generate(templates, context.copy(lastActiveDates = Stat.entries.associateWith { day }), day) }
            .filter { it.main?.stat == Stat.INT }
            .flatMap { it.sides }
        val counts = sides.groupingBy { it.stat }.eachCount()
        // On INT-main days, side weights are STR 2 : WIL 1 : VIT 1 : CHA 1.
        val ratio = counts.getValue(Stat.STR).toDouble() / counts.getValue(Stat.CHA)
        assertThat(ratio).isWithin(0.4).of(2.0)
        assertThat(counts).doesNotContainKey(Stat.INT)
    }

    @Test
    fun `reroll is deterministic, avoids current quests and keeps the main quest`() {
        val directive = DirectiveGenerator.generate(templates, context, date)
        val first = requireNotNull(DirectiveGenerator.rerollSide(templates, context, directive, rerollNumber = 1))
        val again = DirectiveGenerator.rerollSide(templates, context, directive, rerollNumber = 1)

        assertThat(again).isEqualTo(first)
        assertThat(first.type).isEqualTo(QuestType.SIDE)
        assertThat(first.templateId).isNotIn(directive.quests.map { it.templateId })
        assertThat(first.questInstanceId).isNotIn(directive.quests.map { it.questInstanceId })
    }

    @Test
    fun `reroll pricing - first free, next three cost 40, then blocked`() {
        assertThat((1..5).map { DirectiveGenerator.rerollCost(it) }).containsExactly(0, 40, 40, 40, null).inOrder()
    }

    @Test
    fun `penalty quest comes only from penalty-tagged templates at normal difficulty`() {
        val penalty = TemplateFixtures.template("penalty_walk", Stat.VIT, tags = listOf("penalty"))
        val library = templates + penalty

        val draft = requireNotNull(DirectiveGenerator.penaltyQuest(library, context, date))
        assertThat(draft.templateId).isEqualTo("penalty_walk")
        assertThat(draft.difficulty).isEqualTo(Difficulty.NORMAL)
        assertThat(draft.type).isEqualTo(QuestType.PENALTY)
        val normal = (0L until 60L).flatMap { DirectiveGenerator.generate(library, context, date.plusDays(it)).quests }
        assertThat(normal.map { it.templateId }).doesNotContain("penalty_walk")
    }
}
