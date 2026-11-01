package com.assismohanty.awaken.feature.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.components.HealthTile
import com.assismohanty.awaken.core.designsystem.components.HealthTileModel
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTile
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTileModel
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.DailyAverage
import com.assismohanty.awaken.core.domain.HealthOverview
import com.assismohanty.awaken.core.domain.HealthOverviewResult
import com.assismohanty.awaken.core.domain.HealthTrendDay
import com.assismohanty.awaken.core.domain.ScreenTimeSummary
import com.assismohanty.awaken.core.domain.StepSource
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.HealthDay
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Today's summary tiles (phone feedback Part 2): unknown is never shown as 0, and each empty state offers its fix. */
@RunWith(AndroidJUnit4::class)
class SummaryTilesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun overview(
        day: HealthDay = HealthDay(),
        source: StepSource = StepSource.HEALTH_CONNECT,
        sleep: Int? = null,
        distance: Boolean = true,
        calories: Boolean = true,
    ) = HealthOverviewResult.Ready(
        HealthOverview(
            today = day,
            trend = listOf(HealthTrendDay(LocalDate.parse("2026-10-18"), day.steps, day.distanceMeters, day.activeKcal, sleep)),
            updatedAt = Instant.EPOCH,
            canDistance = distance,
            canCalories = calories,
            canHeartRate = true,
            stepSource = source,
        ),
    )

    @Test
    fun healthNotConnectedOrNothingRecordedIsNoData() {
        assertThat(HealthOverviewResult.Disabled.toTile()).isEqualTo(HealthTileModel.NoData)
        assertThat(HealthOverviewResult.ReadFailed.toTile()).isEqualTo(HealthTileModel.NoData)
        assertThat(overview(source = StepSource.NONE).toTile()).isEqualTo(HealthTileModel.NoData)
    }

    @Test
    fun noStepSourceOrAMetricNotAllowedIsUnknownNotZero() {
        val tile = overview(HealthDay(steps = 0, distanceMeters = 0.0, activeKcal = 300.0), source = StepSource.NONE, sleep = 420, distance = false).toTile()

        assertThat(tile).isEqualTo(HealthTileModel.Ready(steps = null, distanceKm = null, activeKcal = 300, sleepMinutes = 420))
    }

    @Test
    fun realHealthNumbersPassThrough() {
        val tile = overview(HealthDay(steps = 8_412, distanceMeters = 6_200.0, activeKcal = 412.4), sleep = 452).toTile()

        assertThat(tile).isEqualTo(HealthTileModel.Ready(8_412, 6.2, 412, 452))
    }

    @Test
    fun screenTimeSummaryMapsToTheRing() {
        assertThat(ScreenTimeSummary.NoAccess.toTile()).isEqualTo(ScreenTimeTileModel.NoAccess)
        assertThat(ScreenTimeSummary.NotTracked.toTile()).isEqualTo(ScreenTimeTileModel.NotTracked)
        assertThat(ScreenTimeSummary.Tracked(mapOf(AppCategory.DISTRACTING to 54, AppCategory.PRODUCTIVE to 92)).toTile())
            .isEqualTo(ScreenTimeTileModel.Tracked(54, 92, 0))
    }

    @Test
    fun dailyAverageSkipsDaysWithNothingRecorded() {
        assertThat(DailyAverage.ofWhole(listOf(0L, 0L, 120L, 180L, 0L, 90L, 61L))).isEqualTo(113) // (120+180+90+61)/4 = 112.75
        assertThat(DailyAverage.ofWhole(listOf(0L, 0L))).isNull()
        assertThat(DailyAverage.of(listOf(6.2, 0.0, 3.1))).isWithin(1e-9).of(4.65)
    }

    @Test
    fun withoutUsageAccessTheTileOffersGrantAccessNotZero() {
        var granted = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { ScreenTimeTile(ScreenTimeTileModel.NoAccess, onClick = {}, onGrantAccess = { granted++ }) }
        }

        composeRule.onNodeWithText("0 min").assertDoesNotExist()
        composeRule.onNodeWithText("Grant access").performClick()

        assertThat(granted).isEqualTo(1)
    }

    @Test
    fun withNoHealthDataTheTileOffersASource() {
        var setUp = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { HealthTile(HealthTileModel.NoData, onClick = {}, onSetUp = { setUp++ }) }
        }

        composeRule.onNodeWithText("No data").assertExists()
        composeRule.onNodeWithText("Set up a source").performClick()

        assertThat(setUp).isEqualTo(1)
    }
}
