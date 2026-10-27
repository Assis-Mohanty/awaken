package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.repo.Notice
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Recaps end to end (GD §14.6, vector 78). The test clock is 2026-10-18, so September 2026 is the month due. */
@RunWith(AndroidJUnit4::class)
class RecapUseCasesTest {
    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private suspend fun septemberDays(count: Int) {
        graph.seedHunter()
        val profile = checkNotNull(graph.db.hunterDao().getProfile())
        graph.db.hunterDao().upsertProfile(profile.copy(firstGameDate = LocalDate.parse("2026-09-01")))
        (1..count).forEach { day ->
            graph.db.dayRecordDao().upsert(
                DayRecordEntity(
                    gameDate = LocalDate.of(2026, 9, day),
                    cleared = true,
                    outcome = DayOutcome.CLEARED,
                    finalizedAt = Instant.EPOCH,
                ),
            )
        }
    }

    @Test
    fun vector78GeneratingTwiceKeepsOneRowWithTheSamePayload() = runTest {
        septemberDays(10)

        val first = graph.generateRecaps()
        val stored = checkNotNull(graph.db.recapDao().get("M:2026-09"))
        val second = graph.generateRecaps()

        assertThat(first).containsExactly("M:2026-09")
        assertThat(second).isEmpty()
        assertThat(graph.db.recapDao().keys()).containsExactly("M:2026-09")
        assertThat(graph.db.recapDao().get("M:2026-09")).isEqualTo(stored)
    }

    @Test
    fun theRolloverOpensANewRecapOnceAndItCanBeReopened() = runTest {
        septemberDays(10)

        graph.runRollover()
        graph.runRollover()

        val notices = graph.notices.pending.first().filterIsInstance<Notice.RecapReady>()
        assertThat(notices).containsExactly(Notice.RecapReady("M:2026-09"))
        val recap = checkNotNull(LoadRecapUseCase(graph.db.hunterDao(), graph.db.recapDao(), graph.loadInsights)("M:2026-09"))
        assertThat(recap.payload.consistencyCleared).isEqualTo(10)
        assertThat(recap.payload.bestStreak).isEqualTo(10)
    }

    @Test
    fun aMonthWithTooFewDaysGetsNoRecap() = runTest {
        septemberDays(6)
        assertThat(graph.generateRecaps()).isEmpty()
    }
}
