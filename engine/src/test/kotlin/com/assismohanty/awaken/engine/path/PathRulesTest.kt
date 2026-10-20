package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.QuestOutcome
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathFixtures.MONDAY
import com.assismohanty.awaken.engine.path.PathFixtures.path
import com.assismohanty.awaken.engine.path.PathFixtures.priced
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Scheduling, carry-over, reroll, plan validation, progression edges and the §6.3/§6.4 retrofit. */
class PathRulesTest {

    private val wednesday = MONDAY.plusDays(2)
    private val friday = MONDAY.plusDays(4)

    @Test
    fun `a plan arriving mid-week only fills the remaining days and keeps the rest as reroll stock`() {
        val slots = PathScheduler.assign(priced("v29_round_robin.json"), path, wednesday)
        assertThat(slots.filter { it.assignedDate == wednesday }.map { it.blueprintIndex }).containsExactly(0, 2).inOrder()
        assertThat(slots.filter { it.assignedDate == friday }.map { it.blueprintIndex }).containsExactly(1, 3).inOrder()
        assertThat(slots.filter { it.assignedDate == null }.map { it.blueprintIndex }).containsExactly(4, 5).inOrder()
    }

    @Test
    fun `a plan arriving after the last active day schedules nothing`() {
        val slots = PathScheduler.assign(priced("v29_round_robin.json"), path, MONDAY.plusDays(5))
        assertThat(slots.map { it.assignedDate }.toSet()).containsExactly(null)
    }

    @Test
    fun `an unfinished quest is re-offered once on the next active day, then SKIPPED`() {
        val slot = PathScheduler.assign(priced("v29_round_robin.json"), path, MONDAY).first()
        val carried = PathScheduler.rollover(slot, path, MONDAY)
        assertThat(carried.assignedDate).isEqualTo(wednesday)
        assertThat(carried.carried).isTrue()
        assertThat(carried.outcome).isEqualTo(SlotOutcome.PENDING)
        assertThat(PathScheduler.rollover(carried, path, wednesday).outcome).isEqualTo(SlotOutcome.SKIPPED)
    }

    @Test
    fun `the week boundary ends carry-over and a first offering then EXPIRES`() {
        val fridaySlot = PathScheduler.assign(priced("v29_round_robin.json"), path, MONDAY).first { it.assignedDate == friday }
        assertThat(PathScheduler.rollover(fridaySlot, path, friday).outcome).isEqualTo(SlotOutcome.EXPIRED)
    }

    @Test
    fun `rollover leaves completed slots and slots for other days alone`() {
        val slot = PathScheduler.assign(priced("v29_round_robin.json"), path, MONDAY).first()
        val done = slot.copy(outcome = SlotOutcome.COMPLETED)
        assertThat(PathScheduler.rollover(done, path, MONDAY)).isEqualTo(done)
        assertThat(PathScheduler.rollover(slot, path, wednesday)).isEqualTo(slot)
    }

    @Test
    fun `reroll takes the Path's next unscheduled blueprint, else falls back to templates`() {
        val slots = PathScheduler.assign(priced("v29_round_robin.json"), path, wednesday)
        val rerolled = slots.first { it.assignedDate == wednesday }
        val replacement = PathScheduler.rerollPick(slots, rerolled, wednesday)
        assertThat(replacement?.blueprintIndex).isEqualTo(4)
        assertThat(replacement?.assignedDate).isEqualTo(wednesday)

        val noneLeft = slots.map { if (it.assignedDate == null) it.copy(outcome = SlotOutcome.REROLLED) else it }
        assertThat(PathScheduler.rerollPick(noneLeft, rerolled, wednesday)).isNull()
    }

    @Test
    fun `overflow with no active day left this week becomes unscheduled`() {
        val fridayOnly = path.copy(activeDays = setOf(DayOfWeek.FRIDAY))
        val slots = PathScheduler.assign(priced("v29_round_robin.json", PathFixtures.context(fridayOnly)), fridayOnly, MONDAY)
        val crowded = slots + slots.map { it.copy(id = it.id + "-b", pathId = "path-b") }
        val plan = PathScheduler.planDay(listOf(fridayOnly, fridayOnly.copy(id = "path-b")), crowded, friday)
        assertThat(plan.today).hasSize(PathScheduler.MAX_PATH_QUESTS_PER_DAY)
        assertThat(plan.moved.map { it.assignedDate }.toSet()).containsExactly(null)
    }

    @Test
    fun `paused Paths are not offered`() {
        val slots = PathScheduler.assign(priced("v29_round_robin.json"), path, MONDAY)
        val plan = PathScheduler.planDay(listOf(path.copy(status = PathStatus.PAUSED)), slots, MONDAY)
        assertThat(plan.today).isEmpty()
        assertThat(plan.moved).isEmpty()
    }

    @Test
    fun `iso week keys use the week-based year`() {
        assertThat(PathScheduler.isoWeek(MONDAY)).isEqualTo("2026-W43")
        assertThat(PathScheduler.isoWeek(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53")
        assertThat(PathScheduler.isoWeek(LocalDate.of(2024, 12, 30))).isEqualTo("2025-W01")
    }

    @Test
    fun `plan validation cleans, drops empty titles, and fails under 4 milestones`() {
        val valid = PathValidator.validatePlan(PathFixtures.text("plan_valid.json")) as PlanValidation.Valid
        assertThat(valid.milestones.map { it.title })
            .containsExactly("Foundations", "Graph Fluency", "DP Depth", "Contest Stamina", "The 1900 Gate").inOrder()
        assertThat(valid.milestones[1].summary).isEqualTo("Shortest paths and topological sort. See")
        assertThat(valid.milestones.map { it.index }).containsExactly(0, 1, 2, 3, 4).inOrder()
        assertThat(PathValidator.validatePlan(PathFixtures.text("plan_too_short.json"))).isEqualTo(PlanValidation.Failed(3))
    }

    @Test
    fun `duplicate titles drop against the batch and the last 14 days (V7)`() {
        val result = PathFixtures.week("v29_round_robin.json", PathFixtures.context(recentTitles = listOf("quest-two!")))
        assertThat(result.drops.single().rule).isEqualTo(DropReason.Rule.V7)
        assertThat(result.drops.single().index).isEqualTo(1)
    }

    @Test
    fun `feedback alone can pull the signal DOWN, and an empty week is HOLD`() {
        val tooHard = List(2) { WeekOutcome("q$it", SlotOutcome.COMPLETED, Feedback.TOO_HARD) }
        assertThat(PathProgression.signal(tooHard)).isEqualTo(ProgressionSignal.DOWN)
        assertThat(PathProgression.signal(emptyList())).isEqualTo(ProgressionSignal.HOLD)
        val rerolledAndReported = listOf(SlotOutcome.REROLLED, SlotOutcome.REPORTED, SlotOutcome.PENDING)
            .map { WeekOutcome("x", it, null) }
        assertThat(PathProgression.stats(rerolledAndReported).total).isEqualTo(0)
    }

    @Test
    fun `stat suggester reads the goal, not the model`() {
        assertThat(StatSuggester.suggest("Reach 1900 on Codeforces")).isEqualTo(Stat.INT)
        assertThat(StatSuggester.suggest("Run a 10k under 50 minutes")).isEqualTo(Stat.VIT)
        assertThat(StatSuggester.suggest("Meditate daily")).isEqualTo(Stat.WIL)
        assertThat(StatSuggester.suggest("Get better at public speaking")).isEqualTo(Stat.CHA)
        assertThat(StatSuggester.suggest("Do 20 pull-ups")).isEqualTo(Stat.STR)
        assertThat(StatSuggester.suggest("Something unrelated")).isNull()
    }

    @Test
    fun `path form limits`() {
        val ok = PathInput("Reach 1900", "", "", Stat.INT, Stat.WIL, setOf(DayOfWeek.MONDAY), 1, 30)
        assertThat(PathRules.problems(ok)).isEmpty()
        assertThat(PathRules.problems(ok.copy(goal = "ab", secondaryStat = Stat.INT, questsPerDay = 3, weeklyMinutes = 901, activeDays = emptySet())))
            .containsExactly(
                PathInputProblem.GOAL_LENGTH, PathInputProblem.SAME_STATS, PathInputProblem.NO_DAYS,
                PathInputProblem.QUESTS_PER_DAY, PathInputProblem.WEEKLY_MINUTES,
            ).inOrder()
        assertThat(PathRules.canCreate(activeOrPlanning = 2, createdToday = 0)).isTrue()
        assertThat(PathRules.canCreate(activeOrPlanning = 3, createdToday = 0)).isFalse()
        assertThat(PathRules.canCreate(activeOrPlanning = 0, createdToday = 1)).isFalse()
    }

    @Test
    fun `reroll targets sides and Path quests, never main`() {
        assertThat(DirectiveGenerator.canReroll(QuestType.SIDE)).isTrue()
        assertThat(DirectiveGenerator.canReroll(QuestType.PATH)).isTrue()
        assertThat(DirectiveGenerator.canReroll(QuestType.MAIN)).isFalse()
        assertThat(DirectiveGenerator.sideCount(0)).isEqualTo(3)
        assertThat(DirectiveGenerator.sideCount(3)).isEqualTo(1)
    }

    @Test
    fun `recalibration ignores Path quests, even a promoted main`() {
        val week = (0L until 7L).map { MONDAY.minusDays(7).plusDays(it) }
        val pathFailures = week.map { QuestOutcome(it, QuestType.MAIN, Stat.INT, QuestStatus.EXPIRED, pathSlotId = "slot-$it") }
        val result = DifficultyAdapter.recalibrate(mapOf(Stat.INT to Difficulty.NORMAL), pathFailures, MONDAY)
        assertThat(result.tiers[Stat.INT]).isEqualTo(Difficulty.NORMAL)
    }
}
