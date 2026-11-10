package com.assismohanty.awaken.core.protect

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.repo.PauseStore
import com.assismohanty.awaken.core.data.usage.ForegroundRead
import com.assismohanty.awaken.core.data.usage.ResumedApp
import com.assismohanty.awaken.engine.PauseOutcome
import com.assismohanty.awaken.engine.PauseRules
import com.assismohanty.awaken.engine.PauseVariant
import com.assismohanty.awaken.testing.FakeUsageGateway
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The Pause decision loop body (GD §19.1) against a real in-memory database and the fake usage log. */
@RunWith(AndroidJUnit4::class)
class PauseWatcherTest {
    private val graph = TestGraph()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val store = PauseStore(PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "pause-${UUID.randomUUID()}.preferences_pb") })
    private val screen = RecordingScreen()
    private var covering = false
    private val apps = object : ProtectApps {
        override fun alwaysAllowed() = setOf(LAUNCHER)
        override fun transientWindows() = setOf(KEYBOARD)
    }
    private val watcher = PauseWatcher(graph.usage, store, graph.db.pauseDao(), graph.db.hunterDao(), graph.clock, apps, { _, _ -> covering }, Dispatchers.Unconfined)

    private class RecordingScreen : PauseScreen {
        val shown = mutableListOf<PauseRequest>()
        var hidden = 0
        var home = 0
        override fun show(request: PauseRequest) { shown += request }
        override fun hide() { hidden += 1 }
        override fun goHome() { home += 1 }
    }

    @After
    fun tearDown() {
        scope.cancel()
        graph.close()
    }

    private var tick = 0L

    /** The log shows [pkgs] coming to the front, one millisecond apart and after everything seen before. */
    private fun onAll(vararg pkgs: String) {
        graph.usage.foregroundRead = ForegroundRead.Resumed(pkgs.map { ResumedApp(it, graph.clock.instant.plusMillis(++tick)) })
        runBlocking { watcher.poll(screen) }
    }

    private fun on(pkg: String) = onAll(pkg)

    private fun chooseInstagram() = runBlocking {
        store.setTargets(setOf(INSTAGRAM))
        store.setEnabled(true)
    }

    private fun rows() = runBlocking { graph.db.pauseDao().countOn(INSTAGRAM, graph.clock.instant.atZone(graph.clock.zone()).toLocalDate()) }

    @Test
    fun aChosenAppOpenedFromTheLauncherIsPausedForTheBaseDelay() {
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        val request = screen.shown.single()
        assertThat(request.packageName).isEqualTo(INSTAGRAM)
        assertThat(request.opensToday).isEqualTo(1)
        assertThat(request.plan.variants.filterIsInstance<PauseVariant.Breathing>().single().seconds).isEqualTo(3)
    }

    @Test
    fun anAppThatIsNotChosenIsLeftAlone() {
        chooseInstagram()
        on(LAUNCHER)
        on("com.whatsapp")
        assertThat(screen.shown).isEmpty()
    }

    @Test
    fun pauseOffMeansNoPause() {
        chooseInstagram()
        runBlocking { store.setEnabled(false) }
        on(LAUNCHER)
        on(INSTAGRAM)
        assertThat(screen.shown).isEmpty()
    }

    @Test
    fun withTheDefaultSettingsEveryOpenPausesEvenRightAfterContinuing() = runBlocking<Unit> {
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        watcher.onContinue(screen)
        on("com.whatsapp")
        on(INSTAGRAM)
        assertThat(screen.shown).hasSize(2)
    }

    @Test
    fun switchingAwayAndBackInsideOneLookStillPausesAgain() = runBlocking<Unit> {
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        watcher.onContinue(screen)
        onAll("com.whatsapp", INSTAGRAM)
        assertThat(screen.shown).hasSize(2)
    }

    @Test
    fun aLookThatRepeatsWhatWasAlreadyHandledChangesNothing() = runBlocking<Unit> {
        chooseInstagram()
        onAll(LAUNCHER, INSTAGRAM)
        watcher.onContinue(screen)
        val before = screen.shown.size
        // The same two entries again (the windows overlap), with the same times.
        graph.usage.foregroundRead = ForegroundRead.Resumed(
            listOf(ResumedApp(LAUNCHER, graph.clock.instant.plusMillis(tick - 1)), ResumedApp(INSTAGRAM, graph.clock.instant.plusMillis(tick))),
        )
        watcher.poll(screen)
        assertThat(screen.shown).hasSize(before)
    }

    @Test
    fun continuingRecordsItAndQuietsTheAppForFiveMinutes() = runBlocking<Unit> {
        store.setCooldown(5)
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        watcher.onContinue(screen)
        assertThat(screen.hidden).isEqualTo(1)
        assertThat(rows()).isEqualTo(1)

        graph.clock.advance(Duration.ofMinutes(4))
        on(LAUNCHER)
        on(INSTAGRAM)
        assertThat(screen.shown).hasSize(1)

        graph.clock.advance(Duration.ofMinutes(1))
        on(LAUNCHER)
        on(INSTAGRAM)
        assertThat(screen.shown).hasSize(2)
        // Two pauses so far today, so the third open would be the next n; this one was the second.
        assertThat(screen.shown.last().opensToday).isEqualTo(2)
    }

    @Test
    fun notNowGoesHomeStartsNoCooldownAndTheNextOpenIsTheNextPause() = runBlocking<Unit> {
        store.setCooldown(5)
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        watcher.onNotNow(screen)
        assertThat(screen.home).isEqualTo(1)
        assertThat(graph.db.pauseDao().lastContinued(INSTAGRAM)).isNull()

        on(LAUNCHER)
        on(INSTAGRAM)
        assertThat(screen.shown).hasSize(2)
        assertThat(screen.shown.last().opensToday).isEqualTo(2)
        // The second pause is longer: 3 s + 2 s.
        assertThat(screen.shown.last().plan.variants.filterIsInstance<PauseVariant.Breathing>().single().seconds).isEqualTo(5)
    }

    @Test
    fun theChosenReasonIsKeptWithThePause() = runBlocking<Unit> {
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        watcher.onIntention(PauseRules.Intention.LOOK_UP)
        watcher.onContinue(screen)
        val day = graph.clock.instant.atZone(graph.clock.zone()).toLocalDate()
        val saved = graph.db.pauseDao().since(day)
        assertThat(saved.single().intention).isEqualTo(PauseRules.Intention.LOOK_UP)
        assertThat(saved.single().outcome).isEqualTo(PauseOutcome.CONTINUED)
    }

    @Test
    fun changingTheActivityInsideTheSameAppIsNotANewOpen() {
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        onAll(INSTAGRAM)
        assertThat(screen.shown).hasSize(1)
    }

    @Test
    fun aKeyboardOverTheAppIsNotLeavingIt() = runBlocking<Unit> {
        store.setCooldown(5)
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        watcher.onContinue(screen)
        graph.clock.advance(Duration.ofMinutes(10))
        on(KEYBOARD)
        on(INSTAGRAM)
        assertThat(screen.shown).hasSize(1)
    }

    @Test
    fun leavingWhileThePauseIsUpTakesItDownAndRecordsNothing() {
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        on(LAUNCHER)
        assertThat(screen.hidden).isEqualTo(1)
        assertThat(watcher.isShowing).isFalse()
        assertThat(rows()).isEqualTo(0)
    }

    @Test
    fun aKeyboardDoesNotTakeThePauseDown() {
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        on(KEYBOARD)
        assertThat(watcher.isShowing).isTrue()
    }

    @Test
    fun aFocusLockCoverMeansPauseStaysOut() {
        chooseInstagram()
        covering = true
        on(LAUNCHER)
        on(INSTAGRAM)
        assertThat(screen.shown).isEmpty()
    }

    @Test
    fun screenOffTakesThePauseDownAndTheNextUnlockIntoTheAppIsAFreshOpen() = runBlocking<Unit> {
        store.setCooldown(5)
        chooseInstagram()
        on(LAUNCHER)
        on(INSTAGRAM)
        watcher.onContinue(screen)
        watcher.onScreenOff(screen)
        graph.clock.advance(Duration.ofMinutes(6))
        on(INSTAGRAM)
        assertThat(screen.shown).hasSize(2)
    }

    @Test
    fun noUsageAccessStopsTheWatcherInsteadOfPretendingNothingHappened() = runBlocking<Unit> {
        graph.usage.access = false
        assertThat(watcher.poll(screen)).isEqualTo(PollResult.NoAccess)
    }

    @Test
    fun anUnreadableLogChangesNothing() = runBlocking<Unit> {
        chooseInstagram()
        graph.usage.foregroundRead = ForegroundRead.Unavailable
        assertThat(watcher.poll(screen)).isEqualTo(PollResult.Ok(changed = false))
        assertThat(screen.shown).isEmpty()
    }

    @Test
    fun rowsOlderThanEightGameDaysAreDeletedOncePerDay() = runBlocking<Unit> {
        chooseInstagram()
        val dao = graph.db.pauseDao()
        val today = graph.clock.instant.atZone(graph.clock.zone()).toLocalDate()
        dao.insert(com.assismohanty.awaken.core.data.db.entity.PauseEventEntity(packageName = INSTAGRAM, gameDay = today.minusDays(9), occurredAt = graph.clock.instant, outcome = PauseOutcome.NOT_NOW, delaySeconds = 3))
        dao.insert(com.assismohanty.awaken.core.data.db.entity.PauseEventEntity(packageName = INSTAGRAM, gameDay = today.minusDays(7), occurredAt = graph.clock.instant, outcome = PauseOutcome.NOT_NOW, delaySeconds = 3))
        on(LAUNCHER)
        assertThat(dao.since(today.minusDays(30)).map { it.gameDay }).containsExactly(today.minusDays(7))
    }

    private companion object {
        const val INSTAGRAM = FakeUsageGateway.INSTAGRAM
        const val LAUNCHER = "com.sec.android.app.launcher"
        const val KEYBOARD = "com.keyboard"
    }
}

private suspend fun com.assismohanty.awaken.core.data.db.dao.PauseDao.since(day: java.time.LocalDate) = observeSince(day).first()
