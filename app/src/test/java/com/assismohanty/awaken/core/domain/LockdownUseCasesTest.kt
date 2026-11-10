package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.usage.OpenedRead
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.ExitCheck
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.LockdownBreak
import com.assismohanty.awaken.engine.LockdownStatus
import com.assismohanty.awaken.engine.ShopRules
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.testing.FakeUsageGateway
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Lockdown start, time, completion, the shield exit and the gaps (GD §19.2), on a real in-memory database. */
@RunWith(AndroidJUnit4::class)
class LockdownUseCasesTest {
    private val graph = TestGraph()
    private val dao = graph.db.lockdownDao()
    private val gateDao = graph.db.gateRunDao()
    private var usageAccess = true
    private var overlay = true
    private val launcher = "com.sec.android.app.launcher"
    private val okFromPhone = setOf("com.assismohanty.awaken")
    private val coverable = setOf(FakeUsageGateway.INSTAGRAM, FakeUsageGateway.DOCS, FakeUsageGateway.CALCULATOR, "com.whatsapp")
    private val env = object : LockdownEnvironment {
        override fun usageAccess() = usageAccess
        override fun canAppearOnTop() = overlay
        override fun okFromPhone() = okFromPhone
        override fun homePackages() = setOf(launcher)
        override fun isCoverable(packageName: String) = packageName in coverable
    }
    private val start = StartLockdownUseCase(graph.startGate, dao, graph.usage, graph.distractingApps, env, graph.transactions, graph.uptime, graph.clock)
    private val controller = LockdownController(dao, graph.gateRuns, graph.usage, env, graph.ledger, graph.db.hunterDao(), graph.transactions, graph.uptime, graph.clock)

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

    private fun give(kind: LedgerKind, amount: Long, itemId: String = "") = runBlocking {
        graph.ledger.insertIdempotent(
            listOf(
                LedgerEntryEntity(
                    kind = kind, itemId = itemId, delta = amount, sourceType = SourceType.SHOP, sourceId = "test:${kind.name}:$itemId:$amount:${graph.clock.instant}",
                    gameDate = LocalDate.parse("2026-10-18"), createdAt = graph.clock.instant,
                ),
            ),
        )
    }

    private fun giveShields(n: Long) = give(LedgerKind.ITEM, n, ShopRules.LOCKDOWN_SHIELD_ID)

    private fun shields(): Long = runBlocking { graph.ledger.balances().item(ShopRules.LOCKDOWN_SHIELD_ID) }

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
        controller.tick()
        graph.uptime.reboot()
        graph.clock.advance(Duration.ofHours(8))
        val saved = dao.running()!!
        assertThat(saved.servedMs).isEqualTo(10 * 60_000L)
        assertThat(controller.remaining(saved)).isEqualTo(Duration.ofMinutes(20))
    }

    @Test
    fun vector191AShieldLetsYouLeaveAndIsSpentWithNoReward() = runBlocking<Unit> {
        giveShields(2)
        startOk(30)
        passMinutes(5)
        val before = graph.ledger.balances()
        val left = controller.leaveWithShield() as LockdownExit.Left
        val broken = left.tick as LockdownTick.Broken
        assertThat(broken.reason).isEqualTo(LockdownBreak.EMERGENCY_EXIT)
        assertThat(shields()).isEqualTo(1)
        // Only the shield changed: no XP, no gold.
        val after = graph.ledger.balances()
        assertThat(after.gold).isEqualTo(before.gold)
        assertThat(after.xpTotal).isEqualTo(before.xpTotal)
        val run = gateDao.get(broken.session.gateRunId)
        assertThat(run?.status).isEqualTo(GateRunStatus.COLLAPSED)
        assertThat(run?.collapseReason).isEqualTo(GateCollapseReason.LOCKDOWN_BROKEN)
        assertThat(dao.running()).isNull()
    }

    @Test
    fun withNoShieldThereIsNoWayOut() = runBlocking<Unit> {
        startOk(30)
        assertThat(controller.leaveWithShield()).isEqualTo(LockdownExit.Refused(ExitCheck.NO_SHIELD))
        assertThat(dao.running()?.status).isEqualTo(LockdownStatus.RUNNING)
        assertThat(gateDao.running()?.lockdown).isTrue()
    }

    @Test
    fun onlyOneShieldExitPerGameDayHoweverManyShieldsRemain() = runBlocking<Unit> {
        giveShields(5)
        startOk(30)
        assertThat(controller.leaveWithShield()).isInstanceOf(LockdownExit.Left::class.java)
        assertThat(controller.shieldStatus()).isEqualTo(ShieldStatus(shields = 4, usedToday = true))
        // No wait before the next Lockdown, but it can not be left today.
        startOk(30)
        assertThat(controller.leaveWithShield()).isEqualTo(LockdownExit.Refused(ExitCheck.USED_TODAY))
        assertThat(shields()).isEqualTo(4)
        assertThat(dao.running()?.status).isEqualTo(LockdownStatus.RUNNING)
    }

    @Test
    fun theShieldComesBackToUseOnTheNextGameDay() = runBlocking<Unit> {
        giveShields(3)
        startOk(30)
        controller.leaveWithShield()
        assertThat(controller.shieldStatus().usedToday).isTrue()
        // The game-day starts at 04:00; from 10:00 that is 18 hours away.
        passMinutes(18 * 60)
        assertThat(controller.shieldStatus()).isEqualTo(ShieldStatus(shields = 2, usedToday = false))
        startOk(30)
        assertThat(controller.leaveWithShield()).isInstanceOf(LockdownExit.Left::class.java)
        assertThat(shields()).isEqualTo(1)
    }

    @Test
    fun leavingWithNoSessionDoesNothing() = runBlocking<Unit> {
        giveShields(1)
        assertThat(controller.leaveWithShield()).isEqualTo(LockdownExit.NoSession)
        assertThat(shields()).isEqualTo(1)
    }

    @Test
    fun aLockdownShieldIsBoughtForOneThousandGoldAndHasNoLimit() = runBlocking<Unit> {
        give(LedgerKind.GOLD, 2_500)
        val first = graph.purchaseItem(ShopRules.LOCKDOWN_SHIELD_ID, "p1")
        assertThat(first).isInstanceOf(PurchaseResult.Purchased::class.java)
        val second = graph.purchaseItem(ShopRules.LOCKDOWN_SHIELD_ID, "p2")
        assertThat(second).isInstanceOf(PurchaseResult.Purchased::class.java)
        assertThat(shields()).isEqualTo(2)
        assertThat(graph.ledger.balances().gold).isEqualTo(500)
        // Not enough gold left for a third.
        assertThat(graph.purchaseItem(ShopRules.LOCKDOWN_SHIELD_ID, "p3")).isEqualTo(PurchaseResult.Rejected(BuyCheck.InsufficientGold(500)))
        assertThat(shields()).isEqualTo(2)
        // A retried tap with the same key charges nothing twice (with gold to spare, so only the key stops it).
        give(LedgerKind.GOLD, 1_000)
        assertThat(graph.purchaseItem(ShopRules.LOCKDOWN_SHIELD_ID, "p1")).isEqualTo(PurchaseResult.Duplicate)
        assertThat(graph.ledger.balances().gold).isEqualTo(1_500)
        assertThat(shields()).isEqualTo(2)
    }

    @Test
    fun vector193LosingAPermissionBreaksItAtTheNextCheck() = runBlocking<Unit> {
        startOk(30)
        passMinutes(1)
        overlay = false
        val broken = controller.tick() as LockdownTick.Broken
        assertThat(broken.reason).isEqualTo(LockdownBreak.PERMISSION_REVOKED)
        assertThat(gateDao.running()).isNull()
        // A broken Lockdown starts no wait.
        overlay = true
        assertThat(start(30, emptySet())).isInstanceOf(StartLockdownResult.Started::class.java)
    }

    @Test
    fun aLockdownIsNeverPausedAbandonedEndedEarlyOrCollapsedByTheGateRules() = runBlocking<Unit> {
        startOk(30)
        assertThat(graph.gateRuns.pause()).isFalse()
        graph.gateRuns.abandon()
        assertThat(graph.gateRuns.endEarly()).isInstanceOf(GateCheck.Running::class.java)
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
        // Only an allowed app and AWAKEN: carry on, and the gap is written down.
        graph.usage.opened.addAll(listOf(FakeUsageGateway.DOCS, "com.assismohanty.awaken"))
        val ok = controller.onWatcherStart() as LockdownTick.Running
        assertThat(ok.session.uncoveredMs).isEqualTo(5 * 60_000L)

        // The home screen counts as another app now: it is covered too.
        graph.clock.advance(Duration.ofMinutes(5))
        graph.usage.opened.add(launcher)
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
