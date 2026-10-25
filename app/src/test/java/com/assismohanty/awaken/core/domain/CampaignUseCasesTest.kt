package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.campaign.CampaignAct
import com.assismohanty.awaken.engine.campaign.CampaignScan
import com.assismohanty.awaken.engine.campaign.CampaignStatus
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The 45-day Campaign end to end on a real in-memory database (GD §6.8, ENGINE_DECISIONS #92–#94). */
@RunWith(AndroidJUnit4::class)
class CampaignUseCasesTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC
    private val day1 = LocalDate.parse("2026-10-18")

    /** STR weakest (0), then INT 2, WIL 4, VIT 5, CHA 6. */
    private val answers: Map<String, Int> = CampaignScan.QUESTIONS.associate { (id, stat) ->
        id to when (stat) {
            Stat.STR -> 0
            Stat.INT -> 1
            Stat.WIL -> 2
            Stat.VIT -> if (id.endsWith("sleep")) 3 else 2
            Stat.CHA -> 3
        }
    }

    @After
    fun tearDown() = graph.close()

    private suspend fun started(): String {
        graph.seedHunter()
        graph.rollover()
        return (graph.startCampaign(answers) as StartCampaignResult.Started).campaignId
    }

    private suspend fun campaignHabits(id: String) = graph.db.campaignDao().habits(id)

    @Test
    fun thePreviewNamesTheWeakestStatAndPlansFiveHabits() = runTest {
        graph.seedHunter()
        val preview = checkNotNull(graph.previewCampaign(answers))
        assertThat(preview.weakest).isEqualTo(Stat.STR)
        assertThat(preview.habits).hasSize(5)
        assertThat(preview.habits.count { it.stat == Stat.STR }).isEqualTo(2)
        assertThat(preview.habits.map { it.difficulty }.toSet()).containsExactly(Difficulty.EASY)
        assertThat(preview.endDate).isEqualTo(day1.plusDays(44))
        assertThat(preview.problem).isNull()
    }

    @Test
    fun sealingCreatesTheCampaignAndTodaysHabitQuests() = runTest {
        val id = started()

        val campaign = checkNotNull(graph.db.campaignDao().active())
        assertThat(campaign.id).isEqualTo(id)
        assertThat(campaign.startDate).isEqualTo(day1)
        assertThat(campaign.currentAct).isEqualTo(CampaignAct.FOUNDATION)
        val habits = campaignHabits(id)
        assertThat(habits).hasSize(5)
        assertThat(habits.all { it.templateId != null && it.active }).isTrue()
        // The daily habit is scheduled today, so it joined the directive.
        val todayHabitIds = graph.db.questDao().getForDate(day1).filter { it.type == QuestType.HABIT }.mapNotNull { it.habitId }
        assertThat(todayHabitIds).containsAtLeastElementsIn(habits.filter { it.daysOfWeekMask == 0b1111111 }.map { it.id })
    }

    @Test
    fun aSecondCampaignIsRefusedWhileOneRuns() = runTest {
        started()
        assertThat(graph.startCampaign(answers)).isEqualTo(StartCampaignResult.Refused(CampaignProblem.ACTIVE))
    }

    @Test
    fun anIncompleteScanIsRefused() = runTest {
        graph.seedHunter()
        assertThat(graph.startCampaign(answers - "str_move")).isEqualTo(StartCampaignResult.IncompleteScan)
    }

    @Test
    fun withoutThreeFreeHabitSlotsItCannotStart() = runTest {
        graph.seedHunter()
        repeat(6) { index ->
            graph.db.habitDao().upsert(
                HabitEntity(UUID.randomUUID().toString(), "Habit $index", Stat.WIL, Difficulty.EASY, Verification.SELF, 1, 1, "times", true, Instant.EPOCH),
            )
        }
        assertThat(graph.startCampaign(answers)).isEqualTo(StartCampaignResult.Refused(CampaignProblem.NO_ROOM))
    }

    @Test
    fun campaignHabitsAreReadOnlyInTheEditor() = runTest {
        val id = started()
        val habit = campaignHabits(id).first()
        val result = graph.saveHabit(HabitDraft(habit.id, "Easier", habit.stat, Difficulty.EASY, setOf(java.time.DayOfWeek.MONDAY), 1, habit.unit))
        assertThat(result).isEqualTo(SaveHabitResult.CampaignHabit)
    }

    @Test
    fun day16StartsTheBuildActAndRepricesTheHabits() = runTest {
        val id = started()
        val before = campaignHabits(id).associate { it.id to it.target }

        graph.clock.advance(Duration.ofDays(15))
        graph.rollover()

        val campaign = checkNotNull(graph.db.campaignDao().active())
        assertThat(campaign.currentAct).isEqualTo(CampaignAct.BUILD)
        val after = campaignHabits(id)
        assertThat(after.map { it.difficulty }.toSet()).containsExactly(Difficulty.NORMAL)
        assertThat(after.all { it.target >= checkNotNull(before[it.id]) }).isTrue()
    }

    @Test
    fun clearing32DaysCompletesTheCampaignAndPaysReforgedOnce() = runTest {
        val id = started()
        // Mark 32 of the 45 days cleared as finalized records (the rollover never re-evaluates a finalized day).
        (0 until 45).forEach { offset ->
            val date = day1.plusDays(offset.toLong())
            val cleared = offset < 32
            graph.db.dayRecordDao().upsert(
                DayRecordEntity(gameDate = date, cleared = cleared, outcome = if (cleared) DayOutcome.CLEARED else DayOutcome.BROKEN, finalizedAt = Instant.EPOCH),
            )
        }

        graph.clock.advance(Duration.ofDays(45))
        val outcome = checkNotNull(graph.rollover())

        assertThat(outcome.campaign).isEqualTo(CampaignAdvance.Finished(CampaignStatus.COMPLETED, 32))
        assertThat(graph.db.campaignDao().active()).isNull()
        assertThat(campaignHabits(id).none { it.active }).isTrue()
        assertThat(graph.db.achievementDao().unlockedIds()).contains("reforged")
        val reforgedGold = graph.db.ledgerDao().all().filter { it.sourceType == SourceType.ACHIEVEMENT && it.sourceId == "reforged" && it.kind == LedgerKind.GOLD }
        assertThat(reforgedGold.sumOf { it.delta }).isEqualTo(1_000L)

        graph.rollover()
        assertThat(graph.db.ledgerDao().all().count { it.sourceId == "reforged" && it.kind == LedgerKind.GOLD }).isEqualTo(1)
    }

    @Test
    fun fewerThan32ClearedDaysEndsWithoutTheReward() = runTest {
        started()
        graph.clock.advance(Duration.ofDays(45))
        val outcome = checkNotNull(graph.rollover())

        assertThat((outcome.campaign as CampaignAdvance.Finished).status).isEqualTo(CampaignStatus.ENDED)
        assertThat(graph.db.achievementDao().unlockedIds()).doesNotContain("reforged")
    }

    @Test
    fun abandoningArchivesTheHabitsAndTouchesNothingEarned() = runTest {
        val id = started()
        val balances = graph.ledger.balances()

        graph.abandonCampaign()

        assertThat(graph.db.campaignDao().active()).isNull()
        assertThat(campaignHabits(id).none { it.active }).isTrue()
        assertThat(graph.ledger.balances()).isEqualTo(balances)
    }
}
