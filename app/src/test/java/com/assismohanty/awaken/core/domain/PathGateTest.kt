package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathInput
import com.assismohanty.awaken.engine.path.PathMode
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.SlotOutcome
import com.assismohanty.awaken.testing.FakeClock
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** GATE-mode Path quests (GD §6.6.3-6.6.4, DS §4.3, vector 33): the gate IS the quest, so it pays exactly once. */
@RunWith(AndroidJUnit4::class)
class PathGateTest {
    private val graph = TestGraph(clock = FakeClock(Instant.parse("2026-10-21T10:00:00Z")))
    private val pathDao = graph.pathDao
    private val questDao = graph.db.questDao()

    @After
    fun tearDown() = graph.close()

    private val everyDay = PathInput(
        goal = "Reach 1900 on Codeforces",
        currentLevel = "Rated 1400",
        notes = "",
        primaryStat = Stat.INT,
        secondaryStat = null,
        activeDays = DayOfWeek.entries.toSet(),
        questsPerDay = 1,
        weeklyMinutes = 420,
    )

    /** An ACTIVE Path whose week holds both kinds of quest; returns today's GATE-mode and SELF-mode quest ids. */
    private suspend fun gateAndSelfQuests(): Pair<String, String> {
        graph.seedHunter()
        graph.grantConsent()
        val pathId = (graph.createPath(everyDay) as CreatePathResult.Created).pathId
        graph.generateDirective(graph.today())
        graph.runPathRequest(PathJob(pathId, PathRequestMode.PLAN_THEN_WEEK, "r"), attempt = 0)
        // Bring one slot of each mode to today (the fake Oracle makes every third blueprint a Focus Gate).
        val slots = pathDao.slotsForWeek(pathId, PathScheduler.isoWeek(graph.today()))
        val gateSlot = slots.first { it.mode == PathMode.GATE }
        pathDao.upsertSlot(gateSlot.copy(assignedGameDate = graph.today()))
        val self = slots.first { it.mode == PathMode.SELF && it.assignedGameDate != graph.today() }
        pathDao.upsertSlot(self.copy(assignedGameDate = graph.today()))
        graph.generateDirective(graph.today())
        val quests = questDao.getForDate(graph.today()).filter { it.pathSlotId != null }
        val gate = quests.first { pathDao.slot(it.pathSlotId!!)?.mode == PathMode.GATE }
        val selfQuest = quests.first { pathDao.slot(it.pathSlotId!!)?.mode == PathMode.SELF }
        return gate.id to selfQuest.id
    }


    @Test
    fun enteringTheGateLinksTheRunToTheQuestWithThePricedRank() = runTest {
        val (gateQuestId, _) = gateAndSelfQuests()
        val quest = questDao.get(gateQuestId)!!
        val priced = pathDao.slot(quest.pathSlotId!!)!!

        val started = graph.startPathGate(gateQuestId) as StartPathGateResult.Started

        assertThat(started.run.questInstanceId).isEqualTo(gateQuestId)
        assertThat(started.run.gateRank).isEqualTo(priced.gateRank)
        assertThat(started.run.gateRank).isEqualTo(GateRank.B) // 60 min estimate → the smallest gate that covers it
        assertThat(started.run.stat).isEqualTo(quest.stat)
        assertThat(questDao.get(gateQuestId)?.status).isEqualTo(QuestStatus.STARTED)
    }

    @Test
    fun clearingThePathGatePaysExactlyOnceAsThePathQuest() = runTest {
        val (gateQuestId, _) = gateAndSelfQuests()
        graph.startPathGate(gateQuestId)

        graph.clock.advance(Duration.ofMinutes(91))
        val check = graph.gateRuns.check()

        assertThat(check).isInstanceOf(GateCheck.Cleared::class.java)
        assertThat(graph.db.completionDao().countFor(gateQuestId)).isEqualTo(1)
        val rows = graph.db.ledgerDao().all()
        assertThat(rows.filter { it.kind == LedgerKind.XP_TOTAL }).hasSize(1)
        // Paid as the Path quest: no separate gate reward row anywhere.
        assertThat(rows.map { it.sourceType }).doesNotContain(SourceType.GATE)
        assertThat(rows.filter { it.kind == LedgerKind.XP_TOTAL }.map { it.sourceType }).containsExactly(SourceType.QUEST)
        assertThat(pathDao.slot(questDao.get(gateQuestId)!!.pathSlotId!!)?.outcome).isEqualTo(SlotOutcome.COMPLETED)
        assertThat(graph.db.gateRunDao().running()).isNull()
        // Timed work is final.
        assertThat(graph.undoCompletion(gateQuestId)).isEqualTo(UndoResult.GateClear)
    }

    @Test
    fun leavingTheGateKeepsThePathQuestOpenForAnotherGo() = runTest {
        val (gateQuestId, _) = gateAndSelfQuests()
        val first = graph.startPathGate(gateQuestId) as StartPathGateResult.Started

        graph.gateRuns.abandon()

        assertThat(graph.db.gateRunDao().get(first.run.id)?.status).isEqualTo(GateRunStatus.ABANDONED)
        assertThat(questDao.get(gateQuestId)?.status).isEqualTo(QuestStatus.ACTIVE)
        assertThat(graph.db.ledgerDao().all().filter { it.kind == LedgerKind.XP_TOTAL }).isEmpty()
        assertThat(graph.startPathGate(gateQuestId)).isInstanceOf(StartPathGateResult.Started::class.java)
    }

    @Test
    fun aSelfPathQuestHasNoGate() = runTest {
        val (gateQuestId, selfQuestId) = gateAndSelfQuests()

        assertThat(graph.startPathGate(selfQuestId)).isEqualTo(StartPathGateResult.NotAGateQuest)
        // And a gate quest can't be ticked off by hand: it needs its verifier (GD §6.5).
        assertThat(graph.completeQuest(gateQuestId)).isEqualTo(CompleteQuestResult.VerificationRequired)
    }

    @Test
    fun onlyOneGateRunsAtATime() = runTest {
        val (gateQuestId, _) = gateAndSelfQuests()
        graph.startPathGate(gateQuestId)

        assertThat(graph.startPathGate(gateQuestId)).isEqualTo(StartPathGateResult.AlreadyRunning)
    }
}
