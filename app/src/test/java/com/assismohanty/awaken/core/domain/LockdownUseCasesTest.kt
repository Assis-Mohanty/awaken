package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.core.data.usage.OpenedRead
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.LockdownBreak
import com.assismohanty.awaken.engine.LockdownStatus
import com.assismohanty.awaken.testing.FakeUsageGateway
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Lockdown start, time, completion, the emergency exit and the gaps (GD §19.2), on a real in-memory database. */
@RunWith(AndroidJUnit4::class)
class LockdownUseCasesTest {
    private val graph = TestGraph()
    private val dao = graph.db.lockdownDao()
    private val gateDao = graph.db.gateRunDao()
    private var usageAccess = true
    private var overlay = true
    private val always = setOf("com.sec.android.app.launcher", "com.assismohanty.awaken")
    private val coverable = setOf(FakeUsageGateway.INSTAGRAM, FakeUsageGateway.DOCS, FakeUsageGateway.CALCULATOR, "com.whatsapp")
    private val env = object : LockdownEnvironment {
        override fun usageAccess() = usageAccess
        override fun canAppearOnTop() = overlay
        override fun alwaysAllowed() = always
        override fun isCoverable(packageName: String) = packageName in coverable
    }
    private val start = StartLockdownUseCase(graph.startGate, dao, graph.usage, graph.distractingApps, env, graph.transactions, graph.uptime, graph.clock)
    private val controller = LockdownController(dao, graph.gateRuns, graph.usage, env, graph.transactions, graph.uptime, graph.clock)

    @Before
    fun setUp() = runBlocking { graph.seedHunter() }

    @After
    fun tearDown() = graph.close()

    private fun startOk(minutes: Int = 30, allowed: Set<String> = setOf(FakeUsageGateway.DOCS)): StartLockdownResult.Started =
        runBlocking { start(minutes, allowed) } as StartLockdownResult.Started

    private fun passMinutes(minutes: Long) {
        graph.uptime.elapsedMs += minutes * 60_000
        graph.clock.advance(Duration.ofMinutes(minutes))
    }

    @Test
    fun startingOpensAFlaggedGateRunAndARunningSession() = runBlocking<Unit> {
        val started = startOk(45)
        assertThat(started.session.plannedMs).isEqualTo(45 * 60_000L)
        assertThat(started.session.allowedPackages).containsExactly(FakeUsageGateway.DOCS)
        val run = gateDao.running()
        assertThat(run?.lockdown).isTrue()
        assertThat(run?.id).isEqualTo(started.session.gateRunId)
        assertThat(dao.running()?.status).isEqualTo(LockdownStatus.RUNNING)
    }

    @Test
    fun theRulesAreChecked() = runBlocking<Unit> {
        assertThat(start(31, emptySet())).isEqualTo(StartLockdownResult.InvalidMinutes)
        assertThat(start(10, emptySet())).isEqualTo(StartLockdownResult.InvalidMinutes)
        assertThat(start(30, setOf("a", "b", "c", "d"))).isEqualTo(StartLockdownResult.TooManyApps)
        // Instagram is a social app: it can't be allowed (LD7).
        assertThat(start(30, setOf(FakeUsageGateway.INSTAGRAM))).isEqualTo(StartLockdownResult.AppNotAllowed(FakeUsageGateway.INSTAGRAM))
        usageAccess = false
        assertThat(start(30, emptySet())).isEqualTo(StartLockdownResult.NeedsPermissions)
        usageAccess = true
        overlay = false
        assertThat(start(30, emptySet())).isEqualTo(StartLockdownResult.NeedsPermissions)
        assertThat(dao.running()).isNull()
    }

    @Test
    fun onlyOneLockdownRunsAtATime() = runBlocking<Unit> {
        startOk()
        assertThat(start(30, emptySet())).isEqualTo(StartLockdownResult.AlreadyRunning)
    }

    @Test
    fun vector190FullLengthServedPaysAsACustomRun() = runBlocking<Unit> {
        startOk(30)
        val before = graph.ledger.balances()
        passMinutes(29)
        assertThat(controller.tick()).isInstanceOf(LockdownTick.Running::class.java)
        passMinutes(1)
        val done = controller.tick() as LockdownTick.Completed
        assertThat(done.outcome).isInstanceOf(GateCheck.Cleared::class.java)
        assertThat(done.session.status).isEqualTo(LockdownStatus.COMPLETED)
        assertThat(graph.ledger.balances()).isNotEqualTo(before)
        assertThat(gateDao.running()).isNull()
        assertThat(gateDao.get(done.session.gateRunId)?.status).isEqualTo(GateRunStatus.CLEARED)
        // Paid once: asking again changes nothing.
        val after = graph.ledger.balances()
        assertThat(controller.tick()).isEqualTo(LockdownTick.None)
        assertThat(graph.ledger.balances()).isEqualTo(after)
    }

    @Test
    fun vector187TheWallClockMovingForwardDoesNotShortenTheSession() = runBlocking<Unit> {
        val started = startOk(30)
        graph.uptime.elapsedMs += 10 * 60_000
        graph.clock.advance(Duration.ofHours(3))
        assertThat(controller.remaining(started.session)).isEqualTo(Duration.ofMinutes(20))
        val tick = controller.tick() as LockdownTick.Running
        assertThat(tick.remaining).isEqualTo(Duration.ofMinutes(20))
        // The heartbeat noticed the clock moved differently from the monotonic one.
        assertThat(dao.running()?.clockChanged).isTrue()
    }

    @Test
    fun vector188TheWallClockMovingBackDoesNotLengthenIt() = runBlocking<Unit> {
        val started = startOk(30)
        graph.uptime.elapsedMs += 10 * 60_000
        graph.clock.advance(Duration.ofHours(-2))
        assertThat(controller.remaining(started.session)).isEqualTo(Duration.ofMinutes(20))
    }

    @Test
    fun vector189AfterARebootItResumesFromTheLastSavedTotal() = runBlocking<Unit> {
        startOk(30)
        passMinutes(10)
        controller.tick() // the heartbeat saves 10 minutes
        graph.uptime.reboot()
        graph.clock.advance(Duration.ofHours(8))
        val saved = dao.running()!!
        assertThat(saved.servedMs).isEqualTo(10 * 60_000L)
        assertThat(controller.remaining(saved)).isEqualTo(Duration.ofMinutes(20))
    }

    @Test
    fun vector191TheEmergencyExitBreaksItWithNoRewardAndStartsTheWait() = runBlocking<Unit> {
        startOk(30)
        passMinutes(5)
        val before = graph.ledger.balances()
        val broken = controller.emergencyExit() as LockdownTick.Broken
        assertThat(broken.reason).isEqualTo(LockdownBreak.EMERGENCY_EXIT)
        assertThat(graph.ledger.balances()).isEqualTo(before)
        val run = gateDao.get(broken.session.gateRunId)
        assertThat(run?.status).isEqualTo(GateRunStatus.COLLAPSED)
        assertThat(run?.collapseReason).isEqualTo(GateCollapseReason.LOCKDOWN_BROKEN)
        // Vector 194: an hour of waiting.
        passMinutes(59)
        assertThat(start(30, emptySet())).isInstanceOf(StartLockdownResult.InCooldown::class.java)
        passMinutes(1)
        assertThat(start(30, emptySet())).isInstanceOf(StartLockdownResult.Started::class.java)
    }

    @Test
    fun vector193LosingAPermissionBreaksItAtTheNextCheck() = runBlocking<Unit> {
        startOk(30)
        passMinutes(1)
        overlay = false
        val broken = controller.tick() as LockdownTick.Broken
        assertThat(broken.reason).isEqualTo(LockdownBreak.PERMISSION_REVOKED)
        assertThat(gateDao.running()).isNull()
    }

    @Test
    fun aLockdownIsNeverPausedAbandonedEndedEarlyOrCollapsedByTheGateRules() = runBlocking<Unit> {
        startOk(30)
        assertThat(graph.gateRuns.pause()).isFalse()
        graph.gateRuns.abandon()
        assertThat(graph.gateRuns.endEarly()).isInstanceOf(GateCheck.Running::class.java)
        // Hours away, a restart and a changed day change nothing for the gate rules.
        graph.gateRuns.onAppStopped()
        graph.uptime.reboot()
        graph.clock.advance(Duration.ofHours(30))
        assertThat(graph.gateRuns.check()).isInstanceOf(GateCheck.Running::class.java)
        assertThat(dao.running()?.status).isEqualTo(LockdownStatus.RUNNING)
    }

    @Test
    fun vector196AGapUpToNinetySecondsIsTolerated() = runBlocking<Unit> {
        startOk(30)
        graph.clock.advance(Duration.ofSeconds(90))
        graph.usage.opened.add("com.whatsapp")
        assertThat(controller.onWatcherStart()).isInstanceOf(LockdownTick.Running::class.java)
    }

    @Test
    fun vector195AGapPastNinetySecondsIsJudgedByTheUsageLog() = runBlocking<Unit> {
        startOk(30, setOf(FakeUsageGateway.DOCS))
        graph.clock.advance(Duration.ofMinutes(5))
        // Only an allowed app and the launcher: carry on, and the gap is written down.
        graph.usage.opened.addAll(listOf(FakeUsageGateway.DOCS, "com.sec.android.app.launcher"))
        val ok = controller.onWatcherStart() as LockdownTick.Running
        assertThat(ok.session.uncoveredMs).isEqualTo(5 * 60_000L)

        // Another app opened during a later gap: it breaks.
        graph.clock.advance(Duration.ofMinutes(5))
        graph.usage.opened.add("com.whatsapp")
        val broken = controller.onWatcherStart() as LockdownTick.Broken
        assertThat(broken.reason).isEqualTo(LockdownBreak.UNCOVERED)
    }

    @Test
    fun aGapWithAnUnreadableLogBreaksItFailingClosed() = runBlocking<Unit> {
        startOk(30)
        graph.clock.advance(Duration.ofMinutes(5))
        graph.usage.readable = false
        val broken = controller.onWatcherStart() as LockdownTick.Broken
        assertThat(broken.reason).isEqualTo(LockdownBreak.UNCOVERED)
        assertThat(graph.usage.opened(graph.clock.now(), graph.clock.now())).isEqualTo(OpenedRead.Unavailable)
    }
}
