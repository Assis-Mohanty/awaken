package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.QuitDayVerdict
import com.assismohanty.awaken.engine.QuitKind
import com.assismohanty.awaken.engine.QuitRules
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.INSTAGRAM
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Quit counters end to end on a real in-memory database (GD §16.13, vectors #122-#126). */
@RunWith(AndroidJUnit4::class)
class QuitUseCasesTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, day start 04:00
    private val day1 = LocalDate.parse("2026-10-18")

    @After
    fun tearDown() = graph.close()

    private suspend fun add(kind: QuitKind = QuitKind.SMOKING, name: String = "Smoking", daysAgo: Long = 0, cost: Long? = null, minutes: Int? = null): String {
        val start = graph.clock.now().minus(Duration.ofDays(daysAgo))
        val result = graph.addQuitCounter(kind, name, start, cost, "INR".takeIf { cost != null }, minutes)
        return (result as AddQuitResult.Added).id
    }

    private suspend fun state(id: String): QuitCounterState {
        val counter = checkNotNull(graph.db.quitDao().counter(id))
        return quitState(counter, graph.db.quitDao().relapses(), graph.db.quitDao().days(), graph.clock.now())
    }

    @Test
    fun aRestartKeepsTheBestRunAndStartsTheCurrentOneAgain() = runTest {
        graph.seedHunter()
        val id = add(daysAgo = 8)
        graph.clock.advance(Duration.ofDays(1)) // 9 days in
        assertThat(graph.logQuitRestart(id, trigger = "STRESS", note = "Long week")).isEqualTo(RestartResult.Logged)
        graph.clock.advance(Duration.ofDays(2).plusHours(13))

        val state = state(id)

        assertThat(state.currentDays).isEqualTo(2)
        assertThat(state.bestDays).isEqualTo(9)
        assertThat(state.restarts.single().trigger).isEqualTo("STRESS")
        assertThat(state.restarts.single().note).isEqualTo("Long week")
    }

    @Test
    fun moneySavedAndTimeReclaimedUseTheCurrentRunsWholeDays() = runTest {
        graph.seedHunter()
        val id = add(daysAgo = 2, cost = 15_000, minutes = 20) // 150.00 a day
        graph.clock.advance(Duration.ofHours(13))

        val state = state(id)

        assertThat(state.moneySavedMinor).isEqualTo(30_000)
        assertThat(state.minutesReclaimed).isEqualTo(40)
    }

    @Test
    fun aRestartMustFallBetweenTheStartAndNow() = runTest {
        graph.seedHunter()
        val id = add(daysAgo = 5)

        assertThat(graph.logQuitRestart(id, at = graph.clock.now().plus(Duration.ofHours(1)))).isEqualTo(RestartResult.InvalidTime)
        assertThat(graph.logQuitRestart(id, at = graph.clock.now().minus(Duration.ofDays(6)))).isEqualTo(RestartResult.InvalidTime)
        assertThat(graph.logQuitRestart("missing")).isEqualTo(RestartResult.NotFound)
        assertThat(graph.db.quitDao().relapses()).isEmpty()
    }

    @Test
    fun aNoteIsClippedToOneHundredFortyCharacters() = runTest {
        graph.seedHunter()
        val id = add()
        graph.logQuitRestart(id, note = "x".repeat(300))
        assertThat(graph.db.quitDao().relapses().single().note).hasLength(QuitRules.NOTE_MAX)
    }

    @Test
    fun sixActiveCountersAtMostAndArchivingFreesASlotButKeepsTheHistory() = runTest {
        graph.seedHunter()
        val ids = (1..6).map { add(QuitKind.CUSTOM, "Thing $it") }
        assertThat(graph.addQuitCounter(QuitKind.CUSTOM, "Seventh", null)).isEqualTo(AddQuitResult.LimitReached)

        graph.logQuitRestart(ids[0])
        graph.archiveQuitCounter(ids[0])

        assertThat(graph.addQuitCounter(QuitKind.CUSTOM, "Seventh", null)).isInstanceOf(AddQuitResult.Added::class.java)
        assertThat(graph.db.quitDao().counter(ids[0])?.archivedAt).isNotNull()
        assertThat(graph.db.quitDao().relapses()).hasSize(1) // nothing is deleted
    }

    @Test
    fun namesStartsAndDuplicatesAreChecked() = runTest {
        graph.seedHunter()
        assertThat(graph.addQuitCounter(QuitKind.CUSTOM, "   ", null)).isEqualTo(AddQuitResult.InvalidName)
        assertThat(graph.addQuitCounter(QuitKind.CUSTOM, "Soon", graph.clock.now().plus(Duration.ofDays(1)))).isEqualTo(AddQuitResult.FutureStart)
        val name = "A".repeat(40)
        val id = (graph.addQuitCounter(QuitKind.CUSTOM, name, null) as AddQuitResult.Added).id
        assertThat(graph.db.quitDao().counter(id)?.name).hasLength(QuitRules.NAME_MAX)
        add(QuitKind.DOOMSCROLLING, "Doomscrolling")
        assertThat(graph.addQuitCounter(QuitKind.DOOMSCROLLING, "Doomscrolling", null)).isEqualTo(AddQuitResult.AlreadyExists)
    }

    @Test
    fun vector126_countersNeverMintXpOrGold() = runTest {
        graph.seedHunter()
        add(QuitKind.SMOKING, "Smoking")
        add(QuitKind.ALCOHOL, "Alcohol")

        graph.clock.advance(Duration.ofDays(31))
        graph.recordQuitProgress(day1.plusDays(31))
        graph.recordQuitProgress(day1.plusDays(31))

        // Counters never mint anything: no XP and no gold rows come from them.
        assertThat(graph.db.ledgerDao().all().filter { it.kind == LedgerKind.XP_TOTAL && it.sourceType != SourceType.QUEST }).isEmpty()
        assertThat(graph.db.ledgerDao().all().filter { it.kind == LedgerKind.GOLD && it.sourceType != SourceType.DAY_CLEAR }).isEmpty()
    }

    @Test
    fun vector125_doomscrollingCountsCleanDaysPausesWhenUnreadableAndRestartsOnAnOverDay() = runTest {
        graph.seedHunter()
        val id = add(QuitKind.DOOMSCROLLING, "Doomscrolling")
        val monday = day1.plusDays(1)

        // The day it was made is not counted: only whole days after it.
        graph.usage.minutes[INSTAGRAM] = 10
        graph.recordQuitProgress(day1)
        assertThat(graph.db.quitDao().days()).isEmpty()

        graph.usage.minutes[INSTAGRAM] = 40
        graph.recordQuitProgress(monday)
        graph.usage.readable = false
        graph.recordQuitProgress(monday.plusDays(1))
        graph.usage.readable = true
        graph.usage.minutes[INSTAGRAM] = 55
        graph.recordQuitProgress(monday.plusDays(2))

        val before = state(id)
        assertThat(before.currentDays).isEqualTo(2)
        assertThat(before.bestDays).isEqualTo(2)
        assertThat(graph.db.quitDao().days().map { it.verdict })
            .containsExactly(QuitDayVerdict.CLEAN, QuitDayVerdict.PAUSED, QuitDayVerdict.CLEAN).inOrder()

        graph.usage.minutes[INSTAGRAM] = 75
        graph.recordQuitProgress(monday.plusDays(3))
        graph.recordQuitProgress(monday.plusDays(3)) // a second run changes nothing

        val after = state(id)
        assertThat(after.currentDays).isEqualTo(0)
        assertThat(after.bestDays).isEqualTo(2)
        val restart = after.restarts.single()
        assertThat(restart.auto).isTrue()
        assertThat(restart.minutes).isEqualTo(75)
        assertThat(graph.db.quitDao().days()).hasSize(4)
    }

    @Test
    fun doomscrollingWithNoUsageAccessPausesInsteadOfCounting() = runTest {
        graph.seedHunter()
        val id = add(QuitKind.DOOMSCROLLING, "Doomscrolling")
        graph.usage.access = false

        graph.recordQuitProgress(day1.plusDays(1))

        val state = state(id)
        assertThat(graph.db.quitDao().days().single().verdict).isEqualTo(QuitDayVerdict.PAUSED)
        assertThat(state.currentDays).isEqualTo(0)
        assertThat(state.paused).isTrue()
        assertThat(state.restarts).isEmpty()
    }

    @Test
    fun doomscrollingCannotBeRestartedByHand() = runTest {
        graph.seedHunter()
        val id = add(QuitKind.DOOMSCROLLING, "Doomscrolling")
        assertThat(graph.logQuitRestart(id)).isEqualTo(RestartResult.NotManual)
    }

    @Test
    fun anArchivedDoomscrollingCounterStopsRecordingDays() = runTest {
        graph.seedHunter()
        val id = add(QuitKind.DOOMSCROLLING, "Doomscrolling")
        graph.archiveQuitCounter(id)

        graph.recordQuitProgress(day1.plusDays(1))

        assertThat(graph.db.quitDao().days()).isEmpty()
    }
}
