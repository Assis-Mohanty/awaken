package com.assismohanty.awaken.core.protect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.clock.WallClock
import com.assismohanty.awaken.core.data.usage.ForegroundRead
import com.assismohanty.awaken.core.data.usage.ResumedApp
import com.assismohanty.awaken.core.lock.FocusLockDecider
import com.assismohanty.awaken.core.lock.LockSnapshot
import com.assismohanty.awaken.core.domain.StartGateResult
import com.assismohanty.awaken.engine.BlockDecision
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Focus Lock loop body: which app in front gets covered during a session, and when the cover stays or goes (GD §8.2). */
@RunWith(AndroidJUnit4::class)
class FocusLockWatcherTest {
    private val graph = TestGraph()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var running = true
    private val blocked = setOf("com.whatsapp", "com.instagram.android")
    private val decider = object : FocusLockDecider {
        override fun sessionRunning(snapshot: LockSnapshot, now: Instant) = running
        override fun decide(packageName: String?, snapshot: LockSnapshot, now: Instant) =
            if (packageName in blocked) BlockDecision.BLOCK else BlockDecision.ALLOW
    }
    private val apps = object : ProtectApps {
        override fun alwaysAllowed() = emptySet<String>()
        override fun transientWindows() = setOf(context.packageName, KEYBOARD)
    }
    /** How far the game clock runs ahead of the real one (debug time travel, or the wall clock moving back). */
    private var ahead: Duration = Duration.ZERO
    private val wall = WallClock { graph.clock.instant.minus(ahead) }
    private val watcher = FocusLockWatcher(graph.usage, decider, graph.clock, apps, context, wall)
    private val screen = object : FocusLockScreen {
        val covers = mutableListOf<FocusCover>()
        var uncovers = 0
        override fun cover(cover: FocusCover) { covers += cover }
        override fun uncover() { uncovers += 1 }
    }
    private var tick = 0L

    @Before
    fun setUp() = runBlocking {
        graph.seedHunter()
        val run = (graph.startGate(GateRank.E, Stat.INT, null) as StartGateResult.Started).run
        watcher.snapshot = LockSnapshot(enabled = true, run = run)
    }

    @After
    fun tearDown() = graph.close()

    private fun open(pkg: String) {
        graph.usage.foregroundRead = ForegroundRead.Resumed(listOf(ResumedApp(pkg, graph.clock.instant.plusMillis(++tick))))
    }

    @Test
    fun aBlockedAppIsCoveredWithTheClock() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        assertThat(screen.covers.single().packageName).isEqualTo("com.whatsapp")
        assertThat(watcher.isCovering).isTrue()
    }

    @Test
    fun anAllowedAppIsNotCovered() = runBlocking<Unit> {
        open("com.example.notes")
        watcher.poll(screen)
        assertThat(screen.covers).isEmpty()
    }

    @Test
    fun awakenComingToTheFrontUnderTheCoverKeepsTheCover() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        open(context.packageName)
        watcher.poll(screen)
        assertThat(screen.uncovers).isEqualTo(0)
        assertThat(watcher.isCovering).isTrue()
    }

    @Test
    fun goingToAnAllowedAppDropsTheCover() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        open("com.example.notes")
        watcher.poll(screen)
        assertThat(screen.uncovers).isEqualTo(1)
        assertThat(watcher.isCovering).isFalse()
    }

    @Test
    fun aKeyboardOverTheBlockedAppIsNotLeavingIt() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        open(KEYBOARD)
        watcher.poll(screen)
        assertThat(screen.uncovers).isEqualTo(0)
    }

    @Test
    fun leavingTheCoverOnPurposeDoesNotCoverTheSameAppAgainFromStaleEvents() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        watcher.dismissed(screen)
        graph.usage.foregroundRead = ForegroundRead.NoChange
        watcher.poll(screen)
        assertThat(screen.covers).hasSize(1)
        assertThat(watcher.isCovering).isFalse()
    }

    @Test
    fun aBlockedAppStillInFrontAfterLeavingTheCoverIsCoveredAgainAfterTheGrace() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        // Back to focus, but the phone refused to go home: no new event, the app is still in front.
        watcher.dismissed(screen)
        graph.usage.foregroundRead = ForegroundRead.NoChange
        watcher.poll(screen)
        assertThat(screen.covers).hasSize(1)
        graph.clock.advance(Duration.ofSeconds(3))
        watcher.poll(screen)
        assertThat(screen.covers).hasSize(2)
        assertThat(watcher.isCovering).isTrue()
    }

    @Test
    fun afterLeavingTheCoverAnotherAppThenTheBlockedAppCoversAgain() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        watcher.dismissed(screen)
        open("com.example.notes")
        watcher.poll(screen)
        open("com.whatsapp")
        watcher.poll(screen)
        assertThat(screen.covers).hasSize(2)
        assertThat(watcher.isCovering).isTrue()
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
        open("com.example.notes")
        watcher.poll(screen)
        ahead = Duration.ofHours(1)
        graph.usage.foregroundRead = ForegroundRead.Resumed(listOf(ResumedApp("com.whatsapp", wall.now().plusMillis(++tick))))
        watcher.poll(screen)
        assertThat(screen.covers.single().packageName).isEqualTo("com.whatsapp")
    }

    @Test
    fun whenTheSessionEndsTheCoverGoes() = runBlocking<Unit> {
        open("com.whatsapp")
        watcher.poll(screen)
        running = false
        watcher.poll(screen)
        assertThat(screen.uncovers).isEqualTo(1)
    }

    @Test
    fun withUsageAccessGoneThePollSaysSo() = runBlocking<Unit> {
        graph.usage.foregroundRead = ForegroundRead.NoAccess
        assertThat(watcher.poll(screen)).isEqualTo(PollResult.NoAccess)
    }

    private companion object {
        const val KEYBOARD = "com.google.android.inputmethod.latin"
    }
}
