package com.assismohanty.awaken.reelcounter

import androidx.test.core.app.ApplicationProvider
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.count.CounterEffect
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.db.ReelDatabase
import com.assismohanty.awaken.reelcounter.data.db.ReelEventEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ReelRepositoryTest {
    private lateinit var db: ReelDatabase
    private lateinit var repo: ReelRepository
    private val day = LocalDate.of(2026, 11, 1)

    @Before
    fun setUp() {
        db = newDatabase(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private fun counted(index: Int, session: Long = 100, app: ShortVideoApp = ShortVideoApp.INSTAGRAM) =
        CounterEffect.Counted(app, day, session, index, T0.plusSeconds(index.toLong()), index + 1)

    @Test
    fun recordingTheSameReelTwiceCountsOnce() = runTest {
        repo = ReelRepository(db, StandardTestDispatcher(testScheduler))
        assertThat(repo.recordReel(counted(0))).isTrue()
        assertThat(repo.recordReel(counted(0))).isFalse()
        assertThat(repo.day(day).single().count).isEqualTo(1)
    }

    @Test
    fun differentIndexesAccumulate() = runTest {
        repo = ReelRepository(db, StandardTestDispatcher(testScheduler))
        repo.recordReel(counted(0))
        repo.recordReel(counted(1))
        repo.addSeconds(day, ShortVideoApp.INSTAGRAM, 30)
        val total = repo.day(day).single()
        assertThat(total.count).isEqualTo(2)
        assertThat(total.seconds).isEqualTo(30)
    }

    @Test
    fun tiktokPackagesShareOneApp() = runTest {
        repo = ReelRepository(db, StandardTestDispatcher(testScheduler))
        repo.recordReel(counted(0, app = ShortVideoApp.TIKTOK))
        assertThat(repo.day(day).single().app).isEqualTo(ShortVideoApp.TIKTOK)
        assertThat(db.dao().day(day.toString()).single().pkg).isEqualTo("com.zhiliaoapp.musically")
    }

    @Test
    fun loadRestoreMapsCountsSnoozesAndTheLastSession() = runTest {
        repo = ReelRepository(db, StandardTestDispatcher(testScheduler))
        repo.recordReel(counted(0, session = 7))
        repo.recordReel(counted(1, session = 7))
        repo.setSnoozes(day, ShortVideoApp.INSTAGRAM, 2)
        val restore = repo.loadRestore(day)
        assertThat(restore.counts).containsExactly(ShortVideoApp.INSTAGRAM, 2)
        assertThat(restore.snoozes).containsExactly(ShortVideoApp.INSTAGRAM, 2)
        val session = restore.sessions.getValue(ShortVideoApp.INSTAGRAM)
        assertThat(session.sessionId).isEqualTo(7)
        assertThat(session.highestCounted).isEqualTo(1)
        assertThat(session.lastSeenAt).isEqualTo(T0.plusSeconds(1))
    }

    @Test
    fun unknownPackagesAreSkipped() = runTest {
        repo = ReelRepository(db, StandardTestDispatcher(testScheduler))
        db.dao().ensureTotal(day.toString(), "x.unknown")
        db.dao().insertEvent(ReelEventEntity(pkg = "x.unknown", ts = 1, itemIndex = 0, day = day.toString(), sessionId = 1))
        assertThat(repo.loadRestore(day).counts).isEmpty()
        assertThat(repo.loadRestore(day).sessions).isEmpty()
        assertThat(repo.observeDay(day).first()).isEmpty()
    }

    @Test
    fun exportContainsTotalsAndEvents() = runTest {
        repo = ReelRepository(db, StandardTestDispatcher(testScheduler))
        repo.recordReel(counted(0))
        val doc = repo.exportDocument(T0, ZoneOffset.UTC, LocalTime.of(4, 0), mapOf(ShortVideoApp.INSTAGRAM to 50))
        assertThat(doc.limits).containsExactly("INSTAGRAM", 50)
        assertThat(doc.dailyTotals.single().count).isEqualTo(1)
        assertThat(doc.events.single().itemIndex).isEqualTo(0)
        assertThat(doc.dayStart).isEqualTo("04:00")
    }
}
