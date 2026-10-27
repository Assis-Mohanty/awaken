package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.CompletionEntity
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.db.entity.HealthDayCacheEntity
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.insight.BucketInsight
import com.assismohanty.awaken.engine.insight.InsightRule
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Insight history from Room and the foreground Health cache (GD §14.3). */
@RunWith(AndroidJUnit4::class)
class InsightUseCasesTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, a Sunday
    private val today = LocalDate.parse("2026-10-18")

    @After
    fun tearDown() = graph.close()

    private suspend fun profileStartingDaysAgo(days: Long, healthEnabled: Boolean = false) {
        graph.seedHunter()
        val profile = checkNotNull(graph.db.hunterDao().getProfile())
        graph.db.hunterDao().upsertProfile(profile.copy(firstGameDate = today.minusDays(days), healthEnabled = healthEnabled))
    }

    /** A finalized cleared day with [completions] active completions. */
    private suspend fun day(date: LocalDate, completions: Int, outcome: DayOutcome = DayOutcome.CLEARED) {
        graph.db.dayRecordDao().upsert(DayRecordEntity(gameDate = date, cleared = true, outcome = outcome, finalizedAt = Instant.EPOCH))
        repeat(completions) { i ->
            graph.db.completionDao().insert(
                CompletionEntity(
                    id = "$date-$i", questInstanceId = "$date-q$i", attempt = 1, gameDate = date, questType = QuestType.SIDE,
                    stat = Stat.STR, verificationSource = VerificationSource.SELF, crit = false, xp = 10, completedAt = Instant.EPOCH,
                ),
            )
        }
    }

    @Test
    fun aNewHunterSeesHowManyDaysAreNeeded() = runTest {
        profileStartingDaysAgo(10)
        (1L..10L).forEach { day(today.minusDays(it), completions = 2) }
        val profile = checkNotNull(graph.db.hunterDao().getProfile())

        val model = graph.loadInsights(profile, today)

        assertThat(model.insights).isEmpty()
        assertThat(model.daysNeeded).isEqualTo(4)
    }

    @Test
    fun restDaysAreLeftOutAndSleepFromTheCacheProducesAnInsight() = runTest {
        profileStartingDaysAgo(60, healthEnabled = true)
        // 40 analysable days before this week: long sleep → 4 quests, short sleep → 2 quests; plus rest days ignored.
        (7L..46L).forEach { back ->
            val date = today.minusDays(back)
            val long = back % 2 == 0L
            day(date, completions = if (long) 4 else 2)
            graph.db.insightDao().upsertHealthDays(
                listOf(HealthDayCacheEntity(date, steps = null, sleepMinutes = if (long) 480 else 300, readAt = Instant.EPOCH)),
            )
        }
        (47L..50L).forEach { day(today.minusDays(it), completions = 0, outcome = DayOutcome.REST) }
        val profile = checkNotNull(graph.db.hunterDao().getProfile())

        val model = graph.loadInsights(profile, today)

        val sleep = model.insights.filterIsInstance<BucketInsight>().single { it.rule == InsightRule.SLEEP }
        assertThat(sleep.aIsHigher).isTrue()
        assertThat(sleep.percent).isEqualTo(100)
        assertThat(model.daysNeeded).isEqualTo(0)
    }

    @Test
    fun refreshingTheHealthCacheReadsOnlyMissingFinalizedDaysOnce() = runTest {
        profileStartingDaysAgo(3, healthEnabled = true)
        graph.health.day = HealthDay(steps = 8_000)

        graph.refreshHealthCache()
        val firstReads = graph.health.reads
        graph.refreshHealthCache()

        assertThat(firstReads).isEqualTo(3)
        assertThat(graph.health.reads).isEqualTo(3)
        val cached = graph.db.insightDao().healthDays(today.minusDays(10), today)
        assertThat(cached.map { it.gameDate }).containsExactly(today.minusDays(3), today.minusDays(2), today.minusDays(1))
        assertThat(cached.all { it.steps == 8_000L }).isTrue()
    }

    @Test
    fun withHealthOffNothingIsRead() = runTest {
        profileStartingDaysAgo(3, healthEnabled = false)
        graph.refreshHealthCache()
        assertThat(graph.health.reads).isEqualTo(0)
    }
}
