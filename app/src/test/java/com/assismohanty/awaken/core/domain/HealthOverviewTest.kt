package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthSession
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The Health screen's data: trust rules applied, optional metrics reported separately, never any rewards. */
@RunWith(AndroidJUnit4::class)
class HealthOverviewTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, day start 04:00
    private val load = LoadHealthOverviewUseCase(graph.db.hunterDao(), graph.health, graph.clock)

    @After
    fun tearDown() = graph.close()

    private suspend fun connected() {
        graph.seedHunter()
        assertThat(graph.enableHealth()).isEqualTo(EnableHealthResult.Enabled)
    }

    private suspend fun ready(): HealthOverview = (load() as HealthOverviewResult.Ready).overview

    @Test
    fun verifiedQuestsOffMeansNothingIsRead() = runTest {
        graph.seedHunter()

        assertThat(load()).isEqualTo(HealthOverviewResult.Disabled)
        assertThat(graph.health.reads).isEqualTo(0)
    }

    @Test
    fun aLostCorePermissionIsUnavailable() = runTest {
        connected()
        graph.health.granted = graph.health.allPermissions - "read.sleep"

        assertThat(load()).isEqualTo(HealthOverviewResult.Unavailable)
    }

    @Test
    fun healthConnectGoneIsUnavailable() = runTest {
        connected()
        graph.health.status = HealthStatus.NOT_INSTALLED

        assertThat(load()).isEqualTo(HealthOverviewResult.Unavailable)
    }

    @Test
    fun aFailedReadIsReportedNotThrown() = runTest {
        connected()
        graph.health.failReads = true

        assertThat(load()).isEqualTo(HealthOverviewResult.ReadFailed)
    }

    @Test
    fun readsTheLastSevenGameDaysOldestFirstEndingToday() = runTest {
        connected()

        val overview = ready()

        assertThat(overview.trend).hasSize(7)
        assertThat(overview.trend.first().date.toString()).isEqualTo("2026-10-12")
        assertThat(overview.trend.last().date.toString()).isEqualTo("2026-10-18")
        assertThat(overview.updatedAt).isEqualTo(Instant.parse("2026-10-18T10:00:00Z"))
    }

    @Test
    fun manualWorkoutsAndImpossibleStepsNeverReachTheScreen() = runTest {
        connected()
        graph.health.day = HealthDay(
            steps = 80_000,
            exercise = listOf(
                HealthSession(Instant.parse("2026-10-18T07:00:00Z"), Instant.parse("2026-10-18T08:00:00Z"), manualEntry = true),
                HealthSession(Instant.parse("2026-10-18T09:00:00Z"), Instant.parse("2026-10-18T09:30:00Z"), origin = "com.strava"),
            ),
        )

        val overview = ready()

        assertThat(overview.today.steps).isEqualTo(0)
        assertThat(overview.today.exercise.map { it.origin }).containsExactly("com.strava")
    }

    @Test
    fun optionalMetricsAreReportedPerPermission() = runTest {
        connected()
        graph.health.granted = graph.health.requiredPermissions + graph.health.distancePermission

        val overview = ready()

        assertThat(overview.canDistance).isTrue()
        assertThat(overview.canCalories).isFalse()
        assertThat(overview.canHeartRate).isFalse()
    }
}
