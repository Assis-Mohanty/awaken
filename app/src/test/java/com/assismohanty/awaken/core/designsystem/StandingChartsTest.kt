package com.assismohanty.awaken.core.designsystem

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The standing charts are one TalkBack node each: the gauge reads its level, the curve is a slider (GD §18.3). */
@RunWith(AndroidJUnit4::class)
class StandingChartsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theGaugeIsOneNodeWithItsDescription() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                Chart(ChartSpec.LevelGauge(0.5f, listOf("A", "B", "C", "D", "E"), description = "Intermediate level"))
            }
        }
        composeRule.onNodeWithContentDescription("Intermediate level").assertExists()
    }

    @Test
    fun theCurveExposesASliderThatReportsItsValueAndAcceptsSetProgress() {
        var selected = 0.4f
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                Chart(
                    ChartSpec.DistributionCurve(
                        density = List(10) { it / 9f },
                        marker = 0.5f,
                        selected = selected,
                        selectedText = "80 kg, percentile 62",
                        description = "Distribution curve",
                        onSelect = { selected = it },
                    ),
                )
            }
        }
        val node = composeRule.onNodeWithContentDescription("Distribution curve")
        val config = node.fetchSemanticsNode().config
        assertThat(config[SemanticsProperties.StateDescription]).isEqualTo("80 kg, percentile 62")
        assertThat(config[SemanticsProperties.ProgressBarRangeInfo].current).isEqualTo(0.4f)

        node.performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }

        assertThat(selected).isEqualTo(0.75f)
    }
}
