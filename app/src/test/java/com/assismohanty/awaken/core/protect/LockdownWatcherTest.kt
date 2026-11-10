package com.assismohanty.awaken.core.protect

import androidx.test.ext.junit.runners.AndroidJUnit4
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

/** The Lockdown loop body: which app in front gets covered (GD §19.2). */
@RunWith(AndroidJUnit4::class)
class LockdownWatcherTest {
    private val graph = TestGraph()
    private val always = setOf(LAUNCHER, "com.assismohanty.awaken")
    private val coverable = setOf(FakeUsageGateway.INSTAGRAM, FakeUsageGateway.DOCS, "com.whatsapp", "com.spotify.music")
    private var overlay = true
    private val env = object : LockdownEnvironment {
        override fun usageAccess() = true
        override fun canAppearOnTop() = overlay
        override fun alwaysAllowed() = always
        override fun isCoverable(packageName: String) = packageName in coverable
    }
    private val dao = graph.db.lockdownDao()
    private val controller = LockdownController(dao, graph.gateRuns, graph.usage, env, graph.transactions, graph.uptime, graph.clock)
    private val watcher = LockdownWatcher(graph.usage, controller, env, graph.clock)
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
    fun anAppThatIsNotAllowedIsCoveredWithTheTimeLeft() = runBlocking<Unit> {
        open("com.whatsapp")
        val result = watcher.poll(screen)
        assertThat(result).isInstanceOf(LockdownTick.Running::class.java)
        assertThat(screen.covers.single().packageName).isEqualTo("com.whatsapp")
        assertThat(screen.covers.single().remaining).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun anAllowedAppTheLauncherAndTheSessionScreenAreNotCovered() = runBlocking<Unit> {
        for (pkg in listOf(FakeUsageGateway.DOCS, LAUNCHER, "com.assismohanty.awaken", "com.android.dialer")) {
            open(pkg)
            watcher.poll(screen)
        }
        assertThat(screen.covers).isEmpty()
    }

    @Test
    fun theCoverGoesWhenTheHunterReturnsToAnAllowedApp() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        open(LAUNCHER)
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
