package com.assismohanty.awaken.reelcounter

import androidx.test.core.app.ApplicationProvider
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp.INSTAGRAM as IG
import com.assismohanty.awaken.reelcounter.core.count.CounterEffect
import com.assismohanty.awaken.reelcounter.core.count.CounterState
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.db.ReelDatabase
import com.assismohanty.awaken.reelcounter.service.EffectApplier
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class EffectApplierTest {
    private lateinit var db: ReelDatabase
    private val overlay = FakeOverlay()
    private val widget = FakeWidget()
    private val day = LocalDate.of(2026, 11, 1)
    private val state = CounterState.initial(day, mapOf(IG to 3), emptyMap(), emptyMap())

    @Before
    fun setUp() {
        db = newDatabase(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.applier() = EffectApplier(ReelRepository(db, StandardTestDispatcher(testScheduler)), overlay, widget)

    private suspend fun dbRow() = db.dao().day(day.toString()).singleOrNull()

    @Test
    fun countedIsRecordedAndRefreshesTheWidget() = runTest {
        applier().apply(listOf(CounterEffect.Counted(IG, day, 1, 0, T0, 1)), state)
        assertThat(dbRow()?.count).isEqualTo(1)
        assertThat(widget.requests).isEqualTo(1)
    }

    @Test
    fun nudgeShowsTheOverlay() = runTest {
        applier().apply(listOf(CounterEffect.NudgeRequested(IG, 50, 2)), state)
        assertThat(overlay.calls).containsExactly("nudge:INSTAGRAM:50:2")
    }

    @Test
    fun acceptedSnoozeIsStoredAndHidesTheNudge() = runTest {
        applier().apply(listOf(CounterEffect.SnoozeAccepted(IG, day, 1)), state)
        assertThat(dbRow()?.snoozes).isEqualTo(1)
        assertThat(overlay.calls).containsExactly("hideNudge")
    }

    @Test
    fun rejectedSnoozeShowsTheNudgeWithNoSnoozesLeft() = runTest {
        applier().apply(listOf(CounterEffect.SnoozeRejected(IG)), state)
        assertThat(overlay.calls).containsExactly("nudge:INSTAGRAM:3:0")
    }

    @Test
    fun bubbleShownForAnApp() = runTest {
        applier().apply(listOf(CounterEffect.BubbleChanged(IG)), state)
        assertThat(overlay.calls).containsExactly("bubble:INSTAGRAM")
        assertThat(widget.requests).isEqualTo(1)
    }

    @Test
    fun timeIsBufferedUntilFifteenSeconds() = runTest {
        val a = applier()
        a.apply(List(14) { CounterEffect.TimeAdded(IG, day, 1) }, state)
        assertThat(dbRow()).isNull()
        a.apply(listOf(CounterEffect.TimeAdded(IG, day, 1)), state)
        assertThat(dbRow()?.seconds).isEqualTo(15)
    }

    @Test
    fun hidingTheBubbleFlushesBufferedTimeAndHidesEverything() = runTest {
        val a = applier()
        a.apply(listOf(CounterEffect.TimeAdded(IG, day, 4), CounterEffect.BubbleChanged(null)), state)
        assertThat(dbRow()?.seconds).isEqualTo(4)
        assertThat(overlay.calls).containsExactly("hideAll")
    }

    @Test
    fun aNewDayFlushesTheOldDaysBufferedTime() = runTest {
        val a = applier()
        a.apply(listOf(CounterEffect.TimeAdded(IG, day, 4)), state)
        a.apply(listOf(CounterEffect.TimeAdded(IG, day.plusDays(1), 1)), state)
        assertThat(dbRow()?.seconds).isEqualTo(4)
    }
}
