package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.engine.ConsistencyScore
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.MissLine
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Consistency, the "never miss twice" line and the never_twice achievement on a real database (GD §14.2). */
@RunWith(AndroidJUnit4::class)
class ConsistencyUseCasesTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC
    private val today = LocalDate.parse("2026-10-18")

    @After
    fun tearDown() = graph.close()

    /** Finalized history ending yesterday, oldest first. */
    private suspend fun history(vararg outcomes: DayOutcome) {
        outcomes.reversed().forEachIndexed { i, outcome ->
            graph.db.dayRecordDao().upsert(
                DayRecordEntity(
                    gameDate = today.minusDays(i + 1L),
                    cleared = outcome == DayOutcome.CLEARED,
                    outcome = outcome,
                    finalizedAt = Instant.EPOCH,
                ),
            )
        }
    }

    @Test
    fun todayShowsTheScoreAndTheMissLineAfterABrokenDay() = runTest {
        graph.seedHunter()
        graph.db.hunterDao().upsertProfile(checkNotNull(graph.db.hunterDao().getProfile()).copy(firstGameDate = today.minusDays(10)))
        history(DayOutcome.CLEARED, DayOutcome.REST, DayOutcome.CLEARED, DayOutcome.BROKEN)
        graph.rollover()

        val model = checkNotNull(graph.observeToday(flowOf(Unit)).first())
        assertThat(model.consistency).isEqualTo(ConsistencyScore(cleared = 2, eligible = 3))
        assertThat(model.missLine).isEqualTo(MissLine.MISSED_YESTERDAY)
    }

    @Test
    fun theFifthRecoveryUnlocksNeverTwiceAtRolloverOnce() = runTest {
        graph.seedHunter()
        // Four recoveries already, and yesterday was missed.
        history(*Array(4) { listOf(DayOutcome.BROKEN, DayOutcome.CLEARED) }.flatMap { it }.toTypedArray(), DayOutcome.BROKEN)
        graph.rollover()
        val main = graph.db.questDao().getForDate(today).first { it.type == QuestType.MAIN }
        graph.completeQuest(main.id)
        assertThat(graph.db.achievementDao().unlockedIds()).doesNotContain("never_twice")

        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()

        assertThat(graph.db.achievementDao().unlockedIds()).contains("never_twice")
        val gold = graph.db.ledgerDao().all().filter {
            it.kind == LedgerKind.GOLD && it.sourceType == SourceType.ACHIEVEMENT && it.sourceId == "never_twice"
        }
        assertThat(gold.map { it.delta }).containsExactly(50L)
    }
}
