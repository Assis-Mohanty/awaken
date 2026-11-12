package com.assismohanty.awaken.reelcounter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.assismohanty.awaken.reelcounter.core.detect.RawSignal
import com.assismohanty.awaken.reelcounter.core.time.FixedReelClock
import com.assismohanty.awaken.reelcounter.data.DetectorRepository
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.ReelSessionGate
import com.assismohanty.awaken.reelcounter.service.ShortVideoBlocker
import com.assismohanty.awaken.reelcounter.service.StudyGuard
import kotlinx.coroutines.flow.flowOf
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.assismohanty.awaken.reelcounter.data.db.ReelDatabase
import com.assismohanty.awaken.reelcounter.service.CounterRunner
import com.assismohanty.awaken.reelcounter.service.EffectApplier
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CounterRunnerTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: ReelDatabase
    private val overlay = FakeOverlay()
    private val clock = FixedReelClock(T0)
    private val day1 = LocalDate.of(2026, 11, 1)
    private val day2 = LocalDate.of(2026, 11, 2)
    private var stores = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = newDatabase(context)
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.runner(): CounterRunner {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = ReelRepository(db, dispatcher)
        val settings = SettingsStore(newDataStore(dispatcher, java.io.File(folder.root, "s${stores++}.preferences_pb")))
        val detectors = DetectorRepository(context)
        val gate = object : ReelSessionGate {
            override val sessionRunning = flowOf(true)
        }
        val blocker = ShortVideoBlocker(settings, detectors, gate)
        val study = StudyGuard(settings, detectors, gate, blocker)
        return CounterRunner(repo, settings, detectors, EffectApplier(repo, overlay), clock, FixedDayStart, blocker, study, dispatcher)
    }

    // Leaving means another app in front: the same app on a non-reel screen is a tolerated miss.
    private val launcherPkg = "com.android.launcher"

    private fun at(ms: Long): Instant = T0.plusMillis(ms)

    private fun CounterRunner.started(): CounterRunner {
        start { t -> RawSignal.Probe(IG_PKG, pagerScreen(), t) }
        return this
    }

    @Test
    fun enteringAShortVideoScreenCountsTheFirstVideoAfterTheSettleWindow() = runTest {
        val r = runner().started()
        runCurrent()
        r.submitSignal(RawSignal.WindowStateChanged(IG_PKG, pagerScreen(), at(0)))
        runCurrent()
        assertThat(overlay.calls.any { it.startsWith("bubble:INSTAGRAM") }).isTrue()
        r.submitSignal(RawSignal.Probe(IG_PKG, pagerScreen(), at(400)))
        runCurrent()
        assertThat(r.state.value?.countToday(IG)).isEqualTo(1)
        assertThat(db.dao().day(day1.toString()).single().count).isEqualTo(1)
        r.stop()
        runCurrent()
    }

    @Test
    fun leavingTheScreenHidesTheOverlay() = runTest {
        val r = runner().started()
        runCurrent()
        r.submitSignal(RawSignal.WindowStateChanged(IG_PKG, pagerScreen(), at(0)))
        r.submitSignal(RawSignal.WindowStateChanged(launcherPkg, plainScreen(launcherPkg), at(2_000)))
        runCurrent()
        assertThat(overlay.calls.last()).isEqualTo("hideAll")
        assertThat(r.state.value?.active).isNull()
        r.stop()
        runCurrent()
    }

    @Test
    fun crossingTheDayBoundaryRollsTheDayAndNeverRollsBack() = runTest {
        val r = runner().started()
        runCurrent()
        r.submitSignal(RawSignal.WindowStateChanged(IG_PKG, pagerScreen(), at(0)))
        r.submitSignal(RawSignal.Probe(IG_PKG, pagerScreen(), at(400)))
        runCurrent()
        assertThat(r.state.value?.countToday(IG)).isEqualTo(1)

        val nextDay = Instant.parse("2026-11-02T04:00:01Z")
        r.submitSignal(RawSignal.ScreenOff(nextDay))
        runCurrent()
        assertThat(r.state.value?.day).isEqualTo(day2)
        assertThat(r.state.value?.countToday(IG)).isEqualTo(0)

        r.submitSignal(RawSignal.ScreenOff(Instant.parse("2026-11-01T12:00:00Z")))
        runCurrent()
        assertThat(r.state.value?.day).isEqualTo(day2)
        r.stop()
        runCurrent()
    }

    @Test
    fun timeIsFlushedAtFifteenSecondsAndWhenLeaving() = runTest {
        val r = runner().started()
        runCurrent()
        r.submitSignal(RawSignal.WindowStateChanged(IG_PKG, pagerScreen(), at(0)))
        for (s in 1..15) r.submitSignal(RawSignal.Probe(IG_PKG, pagerScreen(), at(s * 1_000L)))
        runCurrent()
        assertThat(db.dao().day(day1.toString()).single().seconds).isEqualTo(15)

        for (s in 16..18) r.submitSignal(RawSignal.Probe(IG_PKG, pagerScreen(), at(s * 1_000L)))
        runCurrent()
        assertThat(db.dao().day(day1.toString()).single().seconds).isEqualTo(15)

        r.submitSignal(RawSignal.WindowStateChanged(launcherPkg, plainScreen(launcherPkg), at(18_500)))
        runCurrent()
        assertThat(db.dao().day(day1.toString()).single().seconds).isEqualTo(18)
        r.stop()
        runCurrent()
    }

    @Test
    fun stoppingFlushesAndHidesEverything() = runTest {
        val r = runner().started()
        runCurrent()
        r.submitSignal(RawSignal.WindowStateChanged(IG_PKG, pagerScreen(), at(0)))
        r.submitSignal(RawSignal.Probe(IG_PKG, pagerScreen(), at(5_000)))
        runCurrent()
        r.stop()
        runCurrent()
        assertThat(overlay.calls.last()).isEqualTo("hideAll")
        assertThat(db.dao().day(day1.toString()).single().seconds).isEqualTo(5)
    }

    @Test
    fun aRestartRestoresTodaysCountAndSession() = runTest {
        val first = runner().started()
        runCurrent()
        first.submitSignal(RawSignal.WindowStateChanged(IG_PKG, pagerScreen(), at(0)))
        first.submitSignal(RawSignal.Probe(IG_PKG, pagerScreen(), at(400)))
        runCurrent()
        first.stop()
        runCurrent()

        val second = runner().started()
        runCurrent()
        assertThat(second.state.value?.countToday(IG)).isEqualTo(1)
        // Coming straight back to the same video must not count it a second time.
        second.submitSignal(RawSignal.WindowStateChanged(IG_PKG, pagerScreen(), at(60_000)))
        second.submitSignal(RawSignal.Probe(IG_PKG, pagerScreen(), at(60_500)))
        runCurrent()
        assertThat(second.state.value?.countToday(IG)).isEqualTo(1)
        second.stop()
        runCurrent()
    }
}
