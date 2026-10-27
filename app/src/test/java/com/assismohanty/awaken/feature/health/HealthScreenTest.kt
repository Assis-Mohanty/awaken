package com.assismohanty.awaken.feature.health

import com.assismohanty.awaken.core.designsystem.lexicon.PresentationMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.HealthOverview
import com.assismohanty.awaken.core.domain.HealthTrendDay
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthSession
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun overview(canMore: Boolean) = HealthOverview(
        today = HealthDay(
            steps = 8_412,
            distanceMeters = if (canMore) 6_240.0 else 0.0,
            exercise = listOf(
                HealthSession(
                    Instant.parse("2026-10-18T05:30:00Z"), Instant.parse("2026-10-18T06:08:00Z"),
                    origin = "com.strava", label = "running",
                ),
            ),
        ),
        trend = (6 downTo 0).map { HealthTrendDay(LocalDate.parse("2026-10-18").minusDays(it.toLong()), 5_000L, 0.0, 0.0) },
        updatedAt = Instant.parse("2026-10-18T10:00:00Z"),
        canDistance = canMore,
        canCalories = canMore,
        canHeartRate = canMore,
    )

    private fun show(state: HealthUiState, events: MutableList<HealthEvent> = mutableListOf(), onBack: () -> Unit = {}) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true, presentation = PresentationMode.SYSTEM) {
                HealthScreen(state = state, onEvent = { events += it }, onBack = onBack, zone = ZoneId.of("UTC"))
            }
        }
    }

    @Test
    fun showsTodaysNumbersWorkoutsAndSources() {
        show(HealthUiState(status = HealthScreenStatus.READY, overview = overview(canMore = true)))

        composeRule.onNodeWithText("8,412").assertIsDisplayed()
        composeRule.onNodeWithText("Running · 38 min").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Records typed in by hand are ignored", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun missingOptionalMetricsSayNotAllowedAndOfferToAllowThem() {
        val events = mutableListOf<HealthEvent>()
        show(HealthUiState(status = HealthScreenStatus.READY, overview = overview(canMore = false)), events)

        composeRule.onNodeWithText("Allow more metrics").performScrollTo().performClick()

        assertThat(events).containsExactly(HealthEvent.AllowMoreMetrics)
    }

    @Test
    fun everyMetricAllowedHidesTheAllowMoreButton() {
        show(HealthUiState(status = HealthScreenStatus.READY, overview = overview(canMore = true)))

        composeRule.onNodeWithText("Allow more metrics").assertDoesNotExist()
    }

    @Test
    fun aFailedReadOffersARetry() {
        val events = mutableListOf<HealthEvent>()
        show(HealthUiState(status = HealthScreenStatus.READ_FAILED), events)

        composeRule.onNodeWithText("Refresh").performClick()

        assertThat(events).containsExactly(HealthEvent.Refresh)
    }

    @Test
    fun backIsReported() {
        var backs = 0
        show(HealthUiState(status = HealthScreenStatus.DISABLED), onBack = { backs++ })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }
}
