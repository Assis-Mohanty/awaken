package com.assismohanty.awaken.core.protect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.clock.WallClock
import com.assismohanty.awaken.core.data.usage.ForegroundRead
import com.assismohanty.awaken.core.data.usage.ResumedApp
import com.assismohanty.awaken.core.domain.LockdownController
import com.assismohanty.awaken.core.domain.LockdownEnvironment
import com.assismohanty.awaken.core.domain.LockdownTick
import com.assismohanty.awaken.core.domain.StartLockdownResult
import com.assismohanty.awaken.core.domain.StartLockdownUseCase
import com.assismohanty.awaken.testing.FakeUsageGateway
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Lockdown loop body: which app in front gets covered (GD §19.2). It is strict. */
@RunWith(AndroidJUnit4::class)
class LockdownWatcherTest {
    private val graph = TestGraph()
    private val okFromPhone = setOf("com.assismohanty.awaken", "com.google.android.inputmethod.latin", "com.google.android.dialer")
    private val coverable = setOf(FakeUsageGateway.INSTAGRAM, FakeUsageGateway.DOCS, "com.whatsapp", "com.spotify.music")
    private var overlay = true
    private val env = object : LockdownEnvironment {
        override fun usageAccess() = true
        override fun canAppearOnTop() = overlay
        override fun okFromPhone() = okFromPhone
        override fun homePackages() = setOf(LAUNCHER)
        override fun isCoverable(packageName: String) = packageName in coverable
    }
    private val dao = graph.db.lockdownDao()
    /** How far the game clock runs ahead of the real one (debug time travel, or the wall clock moving back). */
    private var ahead: Duration = Duration.ZERO
    private val wall = WallClock { graph.clock.instant.minus(ahead) }
    private val controller = LockdownController(dao, graph.gateRuns, graph.usage, env, graph.ledger, graph.db.hunterDao(), graph.transactions, graph.uptime, graph.clock, wall)
    private val watcher = LockdownWatcher(graph.usage, controller, env, wall)
    private val start = StartLockdownUseCase(graph.startGate, dao, graph.usage, graph.distractingApps, env, graph.transactions, graph.uptime, graph.clock)
    private val screen = object : LockdownScreen {
        val covers = mutableListOf<LockdownCover>()
        var uncovers = 0
        override fun cover(cover: LockdownCover) { covers += cover }
        override fun uncover() { uncovers += 1 }
    }
    private var tick = 0L

    @Before
    fun setUp() = runBlocking {
        graph.seedHunter()
        val started = start(30, setOf(FakeUsageGateway.DOCS))
        assertThat(started).isInstanceOf(StartLockdownResult.Started::class.java)
    }

    @After
    fun tearDown() = graph.close()

    private fun open(pkg: String) {
        graph.usage.foregroundRead = ForegroundRead.Resumed(listOf(ResumedApp(pkg, graph.clock.instant.plusMillis(++tick))))
    }

    @Test
    fun withTheGameClockThreeDaysAheadTheLogIsStillReadInRealTime() = runBlocking<Unit> {
        ahead = Duration.ofDays(3)
        val at = wall.now().plusMillis(++tick)
        graph.usage.foregroundRead = ForegroundRead.Resumed(listOf(ResumedApp("com.whatsapp", at)))
        watcher.poll(screen)
        val (since, until) = graph.usage.foregroundWindows.last()
        assertThat(since).isLessThan(at)
        assertThat(until).isLessThan(graph.clock.instant)
    }

    @Test
    fun aWallClockMovingBackDoesNotHideTheNextApp() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        ahead = Duration.ofHours(1)
        graph.usage.foregroundRead = ForegroundRead.Resumed(listOf(ResumedApp(FakeUsageGateway.DOCS, wall.now().plusMillis(++tick))))
        watcher.poll(screen)
        assertThat(screen.uncovers).isEqualTo(1)
    }

    @Test
    fun anAppThatIsNotAllowedIsCoveredWithTheTimeLeft() = runBlocking<Unit> {
        open("com.whatsapp")
        val result = watcher.poll(screen)
        assertThat(result).isInstanceOf(LockdownTick.Running::class.java)
        assertThat(screen.covers.single().packageName).isEqualTo("com.whatsapp")
        assertThat(screen.covers.single().remaining).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun withNoAppSeenYetTheSessionScreenIsUp() = runBlocking<Unit> {
        graph.usage.foregroundRead = ForegroundRead.NoChange
        watcher.poll(screen)
        assertThat(screen.covers).hasSize(1)
        assertThat(screen.covers.single().packageName).isNull()
    }

    @Test
    fun theHomeScreenAndSettingsAreCoveredToo() = runBlocking<Unit> {
        open(LAUNCHER)
        watcher.poll(screen)
        open("com.android.settings")
        watcher.poll(screen)
        assertThat(screen.covers.map { it.packageName }).containsExactly(LAUNCHER, "com.android.settings").inOrder()
    }

    @Test
    fun theAllowedAppTheSessionScreenTheKeyboardAndThePhoneAreNotCovered() = runBlocking<Unit> {
        for (pkg in listOf(FakeUsageGateway.DOCS, "com.assismohanty.awaken", "com.google.android.inputmethod.latin", "com.google.android.dialer", "com.android.incallui", "com.android.systemui")) {
            open(pkg)
            watcher.poll(screen)
        }
        assertThat(screen.covers).isEmpty()
    }

    @Test
    fun theCoverGoesWhenTheHunterIsBackOnTheSessionScreen() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        open("com.assismohanty.awaken")
        watcher.poll(screen)
        assertThat(screen.uncovers).isEqualTo(1)
    }

    @Test
    fun theCoverStaysAndItsTimeKeepsRunningWhileTheSameAppIsInFront() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        graph.uptime.elapsedMs += 60_000
        graph.usage.foregroundRead = ForegroundRead.NoChange
        watcher.poll(screen)
        assertThat(screen.covers).hasSize(2)
        assertThat(screen.covers.last().remaining).isEqualTo(Duration.ofMinutes(29))
    }

    @Test
    fun whenTheSessionBreaksTheCoverIsTakenDown() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        overlay = false
        graph.uptime.elapsedMs += 6_000
        val result = watcher.poll(screen)
        assertThat(result).isInstanceOf(LockdownTick.Broken::class.java)
        assertThat(screen.uncovers).isEqualTo(1)
    }

    @Test
    fun whenTheSessionCompletesTheCoverIsTakenDown() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        graph.uptime.elapsedMs += 30 * 60_000
        val result = watcher.poll(screen)
        assertThat(result).isInstanceOf(LockdownTick.Completed::class.java)
        assertThat(screen.uncovers).isEqualTo(1)
    }

    @Test
    fun screenOffTakesTheCoverDown() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        watcher.onScreenOff(screen)
        assertThat(screen.uncovers).isEqualTo(1)
    }

    private companion object {
        const val LAUNCHER = "com.sec.android.app.launcher"
    }
}
