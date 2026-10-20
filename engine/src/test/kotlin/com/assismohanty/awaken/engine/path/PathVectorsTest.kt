package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.TemplateFixtures
import com.assismohanty.awaken.engine.XpCalculator
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.path.PathFixtures.MONDAY
import com.assismohanty.awaken.engine.path.PathFixtures.context
import com.assismohanty.awaken.engine.path.PathFixtures.path
import com.assismohanty.awaken.engine.path.PathFixtures.priced
import com.assismohanty.awaken.engine.path.PathFixtures.week
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant

/** GAME_DESIGN §12 vectors 16–35 (AI Paths). Responses are hand-written fixtures, never live model calls. */
class PathVectorsTest {

    private fun xp(priced: PricedBlueprint) =
        XpCalculator.questXp(priced.difficulty, streakDays = 0, verification = priced.verification.source, crit = false)

    @Test
    fun `vector 16 - SELF extreme effort is priced NORMAL and earns 60 XP`() {
        val quest = priced("v16_self_extreme.json").single()
        assertThat(quest.difficulty).isEqualTo(Difficulty.NORMAL)
        assertThat(quest.verification).isEqualTo(Verification.SELF)
        assertThat(xp(quest)).isEqualTo(60)
    }

    @Test
    fun `vector 17 - GATE 40 minutes needs a C-Gate, HARD, TIMER`() {
        val quest = priced("v17_gate_40.json").single()
        assertThat(quest.gateRank).isEqualTo(GateRank.C)
        assertThat(quest.gateRank?.durationMinutes).isEqualTo(50)
        assertThat(quest.difficulty).isEqualTo(Difficulty.HARD)
        assertThat(quest.verification).isEqualTo(Verification.TIMER)
    }

    @Test
    fun `vector 18 - GATE 5 minutes is an E-Gate and 300 minutes clamps to 120 and a B-Gate`() {
        val (short, long) = priced("v18_gate_bounds.json")
        assertThat(short.gateRank).isEqualTo(GateRank.E)
        assertThat(long.blueprint.estMinutes).isEqualTo(120)
        assertThat(long.gateRank).isEqualTo(GateRank.B)
    }

    @Test
    fun `vector 19 - unknown unit drops the blueprint (V1)`() {
        val result = week("v19_unknown_unit.json")
        assertThat(result.kept.map { it.index }).containsExactly(1)
        assertThat(result.drops.single().index).isEqualTo(0)
        assertThat(result.drops.single().rule).isEqualTo(DropReason.Rule.V1)
    }

    @Test
    fun `vector 20 - targets clamp per unit (V4)`() {
        val (reps, problems) = week("v20_target_clamps.json").kept
        assertThat(reps.target).isEqualTo(300)
        assertThat(problems.target).isEqualTo(1)
    }

    @Test
    fun `vector 21 - a stat outside the Path becomes the primary stat (V3)`() {
        assertThat(week("v21_stat_clamp.json").kept.single().stat).isEqualTo(Stat.INT)
    }

    @Test
    fun `vector 22 - cumulative estMinutes stops at round(weeklyMinutes x 1_2) (V8)`() {
        val result = week("v22_budget.json", context(path.copy(weeklyMinutes = 300)))
        assertThat(result.kept.map { it.estMinutes }).containsExactly(120, 120, 90).inOrder()
        assertThat(result.drops.single().rule).isEqualTo(DropReason.Rule.V8)
    }

    @Test
    fun `vector 23 - URLs are stripped and whitespace collapsed (V2)`() {
        val kept = week("v23_url_title.json").kept.single()
        assertThat(kept.title).isEqualTo("Read: now")
        assertThat(kept.description).isEqualTo("Read 10 pages of the book at today.")
    }

    @Test
    fun `vector 24 - an Injury Mode tag drops the blueprint (V6)`() {
        val result = week("v24_injury.json", context(injuryTags = setOf("impact")))
        assertThat(result.kept).isEmpty()
        assertThat(result.drops.single().rule).isEqualTo(DropReason.Rule.V6)
    }

    @Test
    fun `vector 25 - all blueprints dropped leaves the template-only directive`() {
        val result = week("v25_all_dropped.json", context(injuryTags = setOf("impact", "legs")))
        assertThat(result.kept).isEmpty()
        assertThat(result.drops.map { it.rule }).containsExactly(DropReason.Rule.V6, DropReason.Rule.V6, DropReason.Rule.V2)
        assertThat(PathScheduler.assign(PathPricing.price(result.kept), path, MONDAY)).isEmpty()

        val templates = TemplateFixtures.library()
        val hunter = HunterContext(hunterId = "hunter-1", focusStats = listOf(Stat.INT, Stat.WIL))
        val withNoPaths = DirectiveGenerator.generate(templates, hunter, MONDAY, pathQuests = emptyList())
        assertThat(withNoPaths).isEqualTo(DirectiveGenerator.generate(templates, hunter, MONDAY))
        assertThat(withNoPaths.sides).hasSize(3)
        assertThat(withNoPaths.paths).isEmpty()
    }

    @Test
    fun `vector 26 - 6 completed, 1 expired, one too-easy is UP`() {
        val lastWeek = List(6) { WeekOutcome("q$it", SlotOutcome.COMPLETED, if (it == 0) Feedback.TOO_EASY else null) } +
            WeekOutcome("q6", SlotOutcome.EXPIRED, null)
        val stats = PathProgression.stats(lastWeek)
        assertThat(stats.completionRate).isWithin(0.001).of(0.857)
        assertThat(stats.total).isEqualTo(7)
        assertThat(PathProgression.signal(lastWeek)).isEqualTo(ProgressionSignal.UP)
    }

    @Test
    fun `vector 27 - 2 completed, 4 skipped is DOWN`() {
        val lastWeek = List(2) { WeekOutcome("c$it", SlotOutcome.COMPLETED, null) } +
            List(4) { WeekOutcome("s$it", SlotOutcome.SKIPPED, null) }
        assertThat(PathProgression.stats(lastWeek).completionRate).isWithin(0.001).of(0.333)
        assertThat(PathProgression.signal(lastWeek)).isEqualTo(ProgressionSignal.DOWN)
    }

    @Test
    fun `vector 28 - fewer than 4 counted quests is HOLD`() {
        val lastWeek = List(3) { WeekOutcome("c$it", SlotOutcome.COMPLETED, null) }
        assertThat(PathProgression.signal(lastWeek)).isEqualTo(ProgressionSignal.HOLD)
    }

    @Test
    fun `vector 29 - round-robin over Mon Wed Fri with 2 per day`() {
        val slots = PathScheduler.assign(priced("v29_round_robin.json"), path, MONDAY)
        val byDay = slots.groupBy({ it.assignedDate?.dayOfWeek }, { it.blueprintIndex + 1 })
        assertThat(byDay[DayOfWeek.MONDAY]).containsExactly(1, 4).inOrder()
        assertThat(byDay[DayOfWeek.WEDNESDAY]).containsExactly(2, 5).inOrder()
        assertThat(byDay[DayOfWeek.FRIDAY]).containsExactly(3, 6).inOrder()
    }

    @Test
    fun `vector 30 - the Path quest in today's focus stat is promoted to main and sides drop to 1`() {
        val hunter = HunterContext(hunterId = "hunter-1", focusStats = listOf(Stat.INT, Stat.WIL))
        // focusStats[gameDay % 2] == INT on an even epoch day.
        val day = if (MONDAY.toEpochDay() % 2 == 0L) MONDAY else MONDAY.plusDays(1)
        val onlyToday = path.copy(activeDays = setOf(day.dayOfWeek))
        val slots = PathScheduler.assign(priced("v30_two_stats.json", context(onlyToday)), onlyToday, day)
        val today = PathScheduler.planDay(listOf(onlyToday), slots, day).today
        assertThat(today.map { it.priced.blueprint.stat }).containsExactly(Stat.WIL, Stat.INT).inOrder()

        val directive = DirectiveGenerator.generate(
            TemplateFixtures.library(), hunter, day, today.map { PathScheduler.questDraft(it, day) },
        )
        val main = requireNotNull(directive.main)
        assertThat(main.type).isEqualTo(QuestType.MAIN)
        assertThat(main.stat).isEqualTo(Stat.INT)
        assertThat(main.pathSlotId).isEqualTo(today.single { it.priced.blueprint.stat == Stat.INT }.id)
        assertThat(main.difficulty).isEqualTo(Difficulty.HARD)
        assertThat(main.verification).isEqualTo(Verification.TIMER)
        assertThat(directive.paths.map { it.stat }).containsExactly(Stat.WIL)
        assertThat(directive.paths.single().type).isEqualTo(QuestType.PATH)
        assertThat(directive.sides).hasSize(1)
        assertThat(directive.quests).hasSize(3)
    }

    @Test
    fun `vector 31 - at most 3 Path quests a day, older Path first, the 4th moves to its next active day`() {
        val older = path.copy(id = "path-older", activeDays = setOf(DayOfWeek.MONDAY))
        val newer = path.copy(
            id = "path-newer",
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            createdAt = Instant.parse("2026-10-10T08:00:00Z"),
        )
        val olderSlots = PathScheduler.assign(priced("v30_two_stats.json", context(older)), older, MONDAY)
        val newerSlots = PathScheduler.assign(priced("v29_round_robin.json", context(newer)), newer, MONDAY)
        assertThat(olderSlots.count { it.assignedDate == MONDAY }).isEqualTo(2)
        assertThat(newerSlots.count { it.assignedDate == MONDAY }).isEqualTo(2)

        // Newer Path passed first to prove ordering is by creation time, not input order.
        val plan = PathScheduler.planDay(listOf(newer, older), newerSlots + olderSlots, MONDAY)
        assertThat(plan.today).hasSize(3)
        assertThat(plan.today.map { it.pathId }).containsExactly("path-older", "path-older", "path-newer").inOrder()
        val moved = plan.moved.single()
        assertThat(moved.pathId).isEqualTo("path-newer")
        assertThat(moved.assignedDate).isEqualTo(MONDAY.plusDays(2))
        assertThat(moved.carried).isFalse()
    }

    @Test
    fun `vector 32 - applying the same week response twice yields identical slots`() {
        val first = PathScheduler.assign(priced("v29_round_robin.json"), path, MONDAY)
        val second = PathScheduler.assign(priced("v29_round_robin.json"), path, MONDAY)
        assertThat(second).isEqualTo(first)
        assertThat(first.map { Triple(it.pathId, it.isoWeek, it.blueprintIndex) }).containsNoDuplicates()
        assertThat(first.map { it.id }).containsNoDuplicates()
        assertThat(first.map { it.isoWeek }.toSet()).containsExactly("2026-W43")
    }

    @Test
    fun `vector 33 - a D-Gate Path quest pays one quest reward of 69 XP`() {
        val slot = PathScheduler.assign(priced("v33_gate_d.json"), path, MONDAY).single()
        assertThat(slot.priced.gateRank).isEqualTo(GateRank.D)
        assertThat(slot.priced.difficulty).isEqualTo(Difficulty.NORMAL)
        val quest = PathScheduler.questDraft(slot, MONDAY)
        assertThat(quest.verification).isEqualTo(Verification.TIMER)
        assertThat(xp(slot.priced)).isEqualTo(69)
    }

    @Test
    fun `vector 34 - suggestAdvance is not offered with only 3 completions since the milestone began`() {
        val result = week("v34_suggest_advance.json")
        assertThat(result.suggestAdvance).isTrue()
        val perfectWeek = PathProgression.stats(List(5) { WeekOutcome("q$it", SlotOutcome.COMPLETED, null) })
        assertThat(PathProgression.canOfferAdvance(result.suggestAdvance, 3, perfectWeek)).isFalse()
        assertThat(PathProgression.canOfferAdvance(result.suggestAdvance, 4, perfectWeek)).isTrue()
    }

    @Test
    fun `vector 35 - SELF minDurationSec is a quarter of the estimate, clamped 60 to 900`() {
        val (short, long) = priced("v35_min_duration.json")
        assertThat(short.blueprint.estMinutes).isEqualTo(5)
        assertThat(short.minDurationSec).isEqualTo(75)
        assertThat(long.minDurationSec).isEqualTo(900)
    }
}
