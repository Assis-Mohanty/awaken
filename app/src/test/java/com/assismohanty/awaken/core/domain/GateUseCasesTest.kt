package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.testing.FakeClock
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 8: Focus Gate runs end to end on a real (in-memory) database (GD §8, ARCH §3.3). */
@RunWith(AndroidJUnit4::class)
class GateUseCasesTest {
    private val graph = TestGraph()
    private val gateRunDao = graph.db.gateRunDao()
    private val questDao = graph.db.questDao()

    @After
    fun tearDown() = graph.close()

    private suspend fun open(rank: GateRank = GateRank.E, stat: Stat = Stat.INT, label: String? = "DSA – graphs") =
        (graph.startGate(rank, stat, label) as StartGateResult.Started).run

    /** The app goes to the background for [away] (wall and elapsed time move together). */
    private suspend fun away(away: Duration) {
        graph.gateRuns.onAppStopped()
        graph.clock.advance(away)
        graph.uptime.elapsedMs += away.toMillis()
    }

    @Test
    fun aClearedGatePaysTimerXpAndAFragmentOnce() = runTest {
        graph.seedHunter()
        val run = open()
        val before = graph.ledger.balances()

        graph.clock.advance(Duration.ofMinutes(15))
        val check = graph.gateRuns.check()

        assertThat(check).isInstanceOf(GateCheck.Cleared::class.java)
        val outcome = (check as GateCheck.Cleared).outcome
        assertThat(outcome.verification).isEqualTo(VerificationSource.TIMER)
        assertThat(outcome.reward.fragments).isAtLeast(1)
        assertThat(outcome.undoable).isFalse()
        val after = graph.ledger.balances()
        assertThat(after.xpTotal - before.xpTotal).isEqualTo(outcome.reward.xp.toLong())
        assertThat(after.fragments - before.fragments).isEqualTo(outcome.reward.fragments.toLong())
        val rows = graph.db.ledgerDao().bySource(listOf(SourceType.GATE), outcome.completionId)
        assertThat(rows).isNotEmpty()

        // A second check finds no running gate: nothing is paid twice.
        assertThat(graph.gateRuns.check()).isEqualTo(GateCheck.NoRun)
        assertThat(graph.ledger.balances()).isEqualTo(after)
        assertThat(gateRunDao.get(run.id)?.status).isEqualTo(GateRunStatus.CLEARED)
        assertThat(questDao.get(run.questInstanceId)?.status).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun aRunningGateCannotBeClaimedEarly() = runTest {
        graph.seedHunter()
        open()
        graph.clock.advance(Duration.ofMinutes(14))
        assertThat(graph.gateRuns.check()).isInstanceOf(GateCheck.Running::class.java)
    }

    @Test
    fun shortTimeAwayKeepsTheGateOpenAndLongTimeAwayCollapsesIt() = runTest {
        graph.seedHunter()
        val run = open()

        away(Duration.ofSeconds(60))
        assertThat(graph.gateRuns.check()).isInstanceOf(GateCheck.Running::class.java)
        away(Duration.ofSeconds(31))
        val check = graph.gateRuns.check()

        assertThat(check).isInstanceOf(GateCheck.Collapsed::class.java)
        val collapsed = gateRunDao.get(run.id)
        assertThat(collapsed?.status).isEqualTo(GateRunStatus.COLLAPSED)
        assertThat(collapsed?.collapseReason).isEqualTo(GateCollapseReason.BREACH)
        assertThat(collapsed?.backgroundMs).isEqualTo(91_000)
        assertThat(questDao.get(run.questInstanceId)?.status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(graph.db.ledgerDao().all().filter { it.sourceType == SourceType.GATE }).isEmpty()
    }

    @Test
    fun aRebootWhileAwayCollapsesWithDeviceRestarted() = runTest {
        graph.seedHunter()
        val run = open()
        graph.gateRuns.onAppStopped()
        graph.clock.advance(Duration.ofSeconds(20))
        graph.uptime.reboot()

        graph.gateRuns.check()

        assertThat(gateRunDao.get(run.id)?.collapseReason).isEqualTo(GateCollapseReason.DEVICE_RESTARTED)
    }

    @Test
    fun timeAwayDuringThePauseIsFree() = runTest {
        graph.seedHunter()
        val run = open()
        assertThat(graph.gateRuns.pause()).isTrue()
        assertThat(graph.gateRuns.pause()).isFalse()

        away(Duration.ofMinutes(4))
        assertThat(graph.gateRuns.check()).isInstanceOf(GateCheck.Running::class.java)
        assertThat(gateRunDao.get(run.id)?.backgroundMs).isEqualTo(0)

        // The countdown stood still for the 4-minute pause, so the 15-minute gate ends at 19:00, not 15:00.
        assertThat(graph.gateRuns.resume()).isTrue()
        graph.clock.advance(Duration.ofMinutes(14))
        assertThat(graph.gateRuns.check()).isInstanceOf(GateCheck.Running::class.java)
        graph.clock.advance(Duration.ofMinutes(1))
        assertThat(graph.gateRuns.check()).isInstanceOf(GateCheck.Cleared::class.java)
    }

    @Test
    fun abandoningPaysNothing() = runTest {
        graph.seedHunter()
        val run = open()
        graph.gateRuns.abandon()

        assertThat(gateRunDao.get(run.id)?.status).isEqualTo(GateRunStatus.ABANDONED)
        assertThat(questDao.get(run.questInstanceId)?.status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(graph.gateRuns.check()).isEqualTo(GateCheck.NoRun)
    }

    @Test
    fun onlyOneGateRunsAtATime() = runTest {
        graph.seedHunter()
        open()
        assertThat(graph.startGate(GateRank.D, Stat.WIL, null)).isEqualTo(StartGateResult.AlreadyRunning)
    }

    @Test
    fun aGateThatWouldCrossTheDayResetCannotStart() = runTest {
        // 03:00 UTC, day starts 04:00: a B-Gate (90 + 5 min) doesn't fit, an E-Gate (15 + 5) does.
        val late = TestGraph(clock = FakeClock(Instant.parse("2026-10-19T03:00:00Z")))
        try {
            late.seedHunter()
            assertThat(late.startGate(GateRank.B, Stat.INT, null)).isEqualTo(StartGateResult.TooLate)
            assertThat(late.startGate(GateRank.E, Stat.INT, null)).isInstanceOf(StartGateResult.Started::class.java)
        } finally {
            late.close()
        }
    }

    @Test
    fun aClaimAfterTheDayClosedCollapsesWithDayReset() = runTest {
        graph.seedHunter()
        val run = open()
        graph.clock.advance(Duration.ofDays(1))
        graph.runRollover()

        graph.gateRuns.check()

        assertThat(gateRunDao.get(run.id)?.collapseReason).isEqualTo(GateCollapseReason.DAY_RESET)
    }

    @Test
    fun aClearedGateCannotBeUndone() = runTest {
        graph.seedHunter()
        val run = open()
        graph.clock.advance(Duration.ofMinutes(15))
        graph.gateRuns.check()

        assertThat(graph.undoCompletion(run.questInstanceId)).isEqualTo(UndoResult.GateClear)
    }
}
