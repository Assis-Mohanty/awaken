package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthSession
import com.assismohanty.awaken.testing.FakePhoneStepsGateway
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.CALCULATOR
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.DOCS
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.INSTAGRAM
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The Today summary tiles' reads (phone feedback Part 2): real numbers or a reason, never 0 for "unknown". */
@RunWith(AndroidJUnit4::class)
class TodaySummaryTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, day start 04:00
    private val screenTime = LoadScreenTimeSummaryUseCase(graph.db.hunterDao(), graph.screenTimeReader, graph.usage, graph.clock)
    private val health = LoadHealthOverviewUseCase(graph.db.hunterDao(), graph.health, FakePhoneStepsGateway(), graph.clock)

    @After
    fun tearDown() = graph.close()

    @Test
    fun withoutUsageAccessTheTileAsksForIt() = runTest {
        graph.seedHunter()
        graph.usage.access = false

        assertThat(screenTime()).isEqualTo(ScreenTimeSummary.NoAccess)
    }

    @Test
    fun unreadableUsageIsNotTrackedNotZero() = runTest {
        graph.seedHunter()
        graph.usage.readable = false

        assertThat(screenTime()).isEqualTo(ScreenTimeSummary.NotTracked)
    }

    @Test
    fun usageSplitsByTodaysCategoryOrTheAppDefault() = runTest {
        graph.seedHunter()
        graph.usage.minutes[INSTAGRAM] = 31
        graph.usage.minutes[DOCS] = 50
        graph.usage.minutes[CALCULATOR] = 4
        graph.usage.minutes["com.android.systemui"] = 600 // not a launcher app: not counted, like the Screen Time screen

        val summary = screenTime() as ScreenTimeSummary.Tracked

        assertThat(summary.minutesByCategory).containsExactly(AppCategory.DISTRACTING, 31, AppCategory.PRODUCTIVE, 50, AppCategory.NEUTRAL, 4)
    }

    @Test
    fun theTileReadsOnlyTodayButJudgesTheSourceOverTheWeek() = runTest {
        graph.seedHunter()
        assertThat(graph.enableHealth()).isEqualTo(EnableHealthResult.Enabled)
        val readsBefore = graph.health.reads
        graph.health.day = HealthDay(
            steps = 4_000,
            sleep = listOf(HealthSession(Instant.parse("2026-10-17T22:30:00Z"), Instant.parse("2026-10-18T06:00:00Z"), origin = "com.fitbit.FitbitMobile")),
        )

        val overview = (health(days = 1) as HealthOverviewResult.Ready).overview

        assertThat(graph.health.reads - readsBefore).isEqualTo(1)
        assertThat(overview.trend).hasSize(1)
        assertThat(overview.trend.single().sleepMinutes).isEqualTo(450)
        assertThat(overview.stepSource).isEqualTo(StepSource.HEALTH_CONNECT)
        assertThat(graph.health.lastWindow?.start).isEqualTo(Instant.parse("2026-10-18T04:00:00Z"))
    }
}
