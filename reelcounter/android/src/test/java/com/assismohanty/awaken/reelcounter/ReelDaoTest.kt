package com.assismohanty.awaken.reelcounter

import androidx.test.core.app.ApplicationProvider
import com.assismohanty.awaken.reelcounter.data.db.ReelDatabase
import com.assismohanty.awaken.reelcounter.data.db.ReelEventEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReelDaoTest {
    private lateinit var db: ReelDatabase
    private val dao get() = db.dao()

    @Before
    fun setUp() {
        db = newDatabase(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private fun event(pkg: String = IG_PKG, session: Long = 1, index: Int = 0, day: String = "2026-11-01", ts: Long = 1_000) =
        ReelEventEntity(pkg = pkg, ts = ts, itemIndex = index, day = day, sessionId = session)

    @Test
    fun duplicateEventIsIgnored() = runTest {
        assertThat(dao.insertEvent(event())).isGreaterThan(0)
        assertThat(dao.insertEvent(event())).isEqualTo(-1)
        assertThat(dao.allEvents()).hasSize(1)
    }

    @Test
    fun sameIndexInAnotherSessionIsKept() = runTest {
        dao.insertEvent(event(session = 1))
        assertThat(dao.insertEvent(event(session = 2))).isGreaterThan(0)
    }

    @Test
    fun ensureIncrementAndEnsureAgainKeepsTheCount() = runTest {
        dao.ensureTotal("2026-11-01", IG_PKG)
        dao.incrementCount("2026-11-01", IG_PKG)
        dao.incrementCount("2026-11-01", IG_PKG)
        dao.ensureTotal("2026-11-01", IG_PKG)
        assertThat(dao.day("2026-11-01").single().count).isEqualTo(2)
    }

    @Test
    fun addSecondsAccumulates() = runTest {
        dao.ensureTotal("2026-11-01", IG_PKG)
        dao.addSeconds("2026-11-01", IG_PKG, 15)
        dao.addSeconds("2026-11-01", IG_PKG, 7)
        assertThat(dao.day("2026-11-01").single().seconds).isEqualTo(22)
    }

    @Test
    fun raiseSnoozesNeverLowers() = runTest {
        dao.ensureTotal("2026-11-01", IG_PKG)
        dao.raiseSnoozes("2026-11-01", IG_PKG, 2)
        dao.raiseSnoozes("2026-11-01", IG_PKG, 1)
        assertThat(dao.day("2026-11-01").single().snoozes).isEqualTo(2)
    }

    @Test
    fun observeRangeIsOrderedByDateAndBounded() = runTest {
        for (d in listOf("2026-11-03", "2026-11-01", "2026-11-02", "2026-10-20")) dao.ensureTotal(d, IG_PKG)
        val rows = dao.observeRange("2026-11-01", "2026-11-03").first()
        assertThat(rows.map { it.date }).containsExactly("2026-11-01", "2026-11-02", "2026-11-03").inOrder()
    }

    @Test
    fun firstDayIsTheEarliestRow() = runTest {
        assertThat(dao.observeFirstDay().first()).isNull()
        dao.ensureTotal("2026-11-02", IG_PKG)
        dao.ensureTotal("2026-10-30", IG_PKG)
        assertThat(dao.observeFirstDay().first()).isEqualTo("2026-10-30")
    }

    @Test
    fun lastEventPerPkgReturnsTheNewestOfEach() = runTest {
        dao.insertEvent(event(index = 0))
        dao.insertEvent(event(index = 1))
        dao.insertEvent(event(pkg = "com.google.android.youtube", index = 5))
        val last = dao.lastEventPerPkg().associateBy { it.pkg }
        assertThat(last).hasSize(2)
        assertThat(last.getValue(IG_PKG).itemIndex).isEqualTo(1)
        assertThat(last.getValue("com.google.android.youtube").itemIndex).isEqualTo(5)
    }
}
