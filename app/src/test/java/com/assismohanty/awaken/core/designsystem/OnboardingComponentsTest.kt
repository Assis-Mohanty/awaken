package com.assismohanty.awaken.core.designsystem

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.BmiCard
import com.assismohanty.awaken.core.designsystem.components.BodyMapGrid
import com.assismohanty.awaken.core.designsystem.components.DayChips
import com.assismohanty.awaken.core.designsystem.components.OnboardingToggleRow
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.core.designsystem.components.ChoiceItem
import com.assismohanty.awaken.core.designsystem.components.ImageChoiceCard
import com.assismohanty.awaken.core.designsystem.components.ImageChoiceGrid
import com.assismohanty.awaken.core.designsystem.components.StageSlider
import com.assismohanty.awaken.core.designsystem.components.OnboardingShell
import com.assismohanty.awaken.core.designsystem.components.OnboardingStepBody
import com.assismohanty.awaken.core.designsystem.components.RulerPicker
import com.assismohanty.awaken.core.designsystem.components.WheelPicker
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.onboarding.BodyMath
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun customAction(description: String, label: String): Boolean {
        val node = composeRule.onNodeWithContentDescription(description).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        return action.action()
    }

    private fun stateDescription(description: String): String? =
        composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun theShellPinsAContinueButtonThatFollowsValidity() {
        var enabled by mutableStateOfBoolean(false)
        var continued = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                OnboardingShell(
                    stepKey = 1, forward = true, segments = 5, progress = 1.5f, progressDescription = "Step 2 of 22",
                    primaryText = "Continue", onPrimary = { continued++ }, primaryEnabled = enabled,
                ) {}
            }
        }
        composeRule.onNodeWithText("Continue").assertIsNotEnabled().performClick()
        assertThat(continued).isEqualTo(0)
        enabled = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue").assertIsEnabled().performClick()
        assertThat(continued).isEqualTo(1)
    }

    @Test
    fun backAndSkipAppearOnlyWhenGiven() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                OnboardingShell(
                    stepKey = 0, forward = true, segments = 5, progress = 0.5f, progressDescription = "Step 1 of 22",
                    primaryText = "Continue", onPrimary = {},
                ) {}
            }
        }
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithText("Skip").assertDoesNotExist()
    }

    @Test
    fun backAndSkipReportClicks() {
        var back = 0
        var skip = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                OnboardingShell(
                    stepKey = 3, forward = true, segments = 5, progress = 2f, progressDescription = "Step 4 of 22",
                    primaryText = "Continue", onPrimary = {}, onBack = { back++ }, onSkip = { skip++ },
                ) {}
            }
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Skip").performClick()
        assertThat(back).isEqualTo(1)
        assertThat(skip).isEqualTo(1)
    }

    @Test
    fun theProgressBarIsOneNodeWithItsStepText() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                OnboardingShell(
                    stepKey = 1, forward = true, segments = 5, progress = 1f, progressDescription = "Step 2 of 22",
                    primaryText = "Continue", onPrimary = {},
                ) {}
            }
        }
        composeRule.onNodeWithContentDescription("Step 2 of 22").assertExists()
    }



    @Test
    fun theShellDrawsEachStepFromItsOwnKeySoTheOutgoingStepKeepsItsWords() {
        var step by mutableIntStateOf(1)
        val drawn = mutableSetOf<Int>()
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                OnboardingShell(
                    stepKey = step, forward = true, segments = 5, progress = step.toFloat(), progressDescription = "Step",
                    primaryText = "Continue", onPrimary = {},
                ) { key ->
                    drawn += key
                    OnboardingStepBody(title = "Title $key") {}
                }
            }
        }
        composeRule.onNodeWithText("Title 1").assertExists()
        composeRule.runOnIdle { step = 2 }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Title 2").assertExists()
        composeRule.onNodeWithText("Title 1").assertDoesNotExist()
        // Step 1 was drawn as step 1 (never as 2) and step 2 as step 2, so the outgoing one can slide out with its own words.
        assertThat(drawn).containsExactly(1, 2)
    }
    @Test
    fun theWheelReportsItsSelectedValueAndStepsWithTheAccessibilityActions() {
        var selected by mutableIntStateOf(12)
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                WheelPicker(
                    options = (13..100).toList(), selectedIndex = selected, onSelected = { selected = it },
                    label = { it.toString() }, description = "Age", modifier = Modifier.width(200.dp),
                )
            }
        }
        assertThat(stateDescription("Age")).isEqualTo("25")
        composeRule.runOnIdle { customAction("Age", "Next value") }
        composeRule.waitForIdle()
        assertThat(selected).isEqualTo(13)
        composeRule.runOnIdle { customAction("Age", "Previous value") }
        composeRule.waitForIdle()
        assertThat(selected).isEqualTo(12)
        assertThat(stateDescription("Age")).isEqualTo("25")
    }

    @Test
    fun theWheelFollowsAnIndexChangedFromOutside() {
        var selected by mutableIntStateOf(0)
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                WheelPicker(
                    options = (13..100).toList(), selectedIndex = selected, onSelected = { selected = it },
                    label = { it.toString() }, description = "Age",
                )
            }
        }
        selected = 30
        composeRule.waitForIdle()
        assertThat(stateDescription("Age")).isEqualTo("43")
        assertThat(selected).isEqualTo(30)
    }

    @Test
    fun theRulerStepsWithTheAccessibilityActionsAndStaysInsideItsRange() {
        var tick by mutableIntStateOf(120)
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                RulerPicker(
                    first = 120, last = 122, tick = tick, onTick = { tick = it }, majorEvery = 10,
                    valueText = { "$it cm" }, description = "Height", modifier = Modifier.height(200.dp),
                    vertical = true,
                )
            }
        }
        assertThat(stateDescription("Height")).isEqualTo("120 cm")
        composeRule.runOnIdle { customAction("Height", "Previous value") }
        composeRule.waitForIdle()
        assertThat(tick).isEqualTo(120)
        repeat(5) {
            composeRule.runOnIdle { customAction("Height", "Next value") }
            composeRule.waitForIdle()
        }
        assertThat(tick).isEqualTo(122)
        assertThat(stateDescription("Height")).isEqualTo("122 cm")
    }

    @Test
    fun theBmiCardShowsTheScaleBandForAdults() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { BmiCard(BodyMath.bmi(170.0, 70.0, age = 25)) } }
        composeRule.onNode(hasContentDescription("BMI 24.2. Within the usual range")).assertExists()
    }

    @Test
    fun theBmiCardUnderTwentyShowsOnlyTheNumberAndANeutralLine() {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { BmiCard(BodyMath.bmi(170.0, 90.0, age = 16)) } }
        composeRule.onNode(hasContentDescription("BMI 31.1. A rough number from your height and weight.")).assertExists()
        composeRule.onAllNodesWithText("Above the usual range").assertCountEquals(0)
    }

    @Test
    fun aMultiSelectCardIsACheckboxThatReportsClicks() {
        var clicks = 0
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                ImageChoiceCard(label = "Sleep", selected = true, onClick = { clicks++ }, multi = true)
            }
        }
        val node = composeRule.onNodeWithText("Sleep")
        node.assertIsOn()
        node.performClick()
        assertThat(clicks).isEqualTo(1)
        assertThat(node.fetchSemanticsNode().config[SemanticsProperties.Role]).isEqualTo(Role.Checkbox)
    }

    @Test
    fun aSingleSelectCardIsARadioButton() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                ImageChoiceCard(label = "Female", selected = false, onClick = {}, multi = false)
            }
        }
        val node = composeRule.onNodeWithText("Female")
        node.assertIsNotSelected()
        assertThat(node.fetchSemanticsNode().config[SemanticsProperties.Role]).isEqualTo(Role.RadioButton)
    }

    @Test
    fun theGridMarksOnlyTheSelectedKeys() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                ImageChoiceGrid(
                    items = listOf(ChoiceItem("a", "Strength"), ChoiceItem("b", "Focus")),
                    selectedKeys = setOf("b"), onToggle = {}, multi = true,
                )
            }
        }
        composeRule.onNodeWithText("Strength").assertIsOff()
        composeRule.onNodeWithText("Focus").assertIsOn()
    }

    @Test
    fun theStageSliderReadsItsStage() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                StageSlider(
                    stage = 3, onStage = {}, stages = 5, imageFor = { R.drawable.onb_shape_3_n },
                    description = "Body shape", stageText = "Stage 3 of 5", modifier = Modifier.height(300.dp),
                )
            }
        }
        assertThat(stateDescription("Body shape")).isEqualTo("Stage 3 of 5")
    }

    @Test
    fun aSelectedCardOpensItsDetailAndAnUnselectedOneDoesNot() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                ImageChoiceGrid(
                    items = listOf(
                        ChoiceItem("a", "Steady", detail = "Smaller goals"),
                        ChoiceItem("b", "Push hard", detail = "Bigger goals"),
                    ),
                    selectedKeys = setOf("b"), onToggle = {}, multi = false, columns = 1,
                )
            }
        }
        composeRule.onNodeWithText("Bigger goals").assertExists()
        composeRule.onAllNodesWithText("Smaller goals").assertCountEquals(0)
    }

    @Test
    fun theDayChipsReportIsoDaysAndMarkToday() {
        val toggled = mutableListOf<Int>()
        val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                DayChips(
                    selectedDays = setOf(3), today = 2, onToggle = { toggled += it },
                    dayLetter = { names[it - 1].take(1) },
                    dayDescription = { day, isToday -> names[day - 1] + if (isToday) ", today" else "" },
                    todayLabel = "Today",
                )
            }
        }
        composeRule.onNodeWithContentDescription("Monday").performClick()
        composeRule.onNodeWithContentDescription("Sunday").performClick()
        assertThat(toggled).containsExactly(1, 7).inOrder()
        composeRule.onNodeWithContentDescription("Tuesday, today").assertExists()
        composeRule.onNodeWithContentDescription("Wednesday").assertIsOn()
        composeRule.onNodeWithContentDescription("Monday").assertIsOff()
        // Only today carries the visible label.
        composeRule.onAllNodesWithText("Today").assertCountEquals(1)
    }

    @Test
    fun theToggleRowIsASwitchThatReportsTheNewValue() {
        var on by mutableStateOfBoolean(false)
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) { OnboardingToggleRow(title = "Remind me", checked = on, onChange = { on = it }) }
        }
        val node = composeRule.onNodeWithText("Remind me")
        assertThat(node.fetchSemanticsNode().config[SemanticsProperties.Role]).isEqualTo(Role.Switch)
        node.assertIsOff().performClick()
        composeRule.waitForIdle()
        node.assertIsOn()
    }

    @Test
    fun theBodyMapGridMarksTheSelectedRegions() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                BodyMapGrid(
                    options = listOf(ChoiceItem("none", "No injuries"), ChoiceItem("knee", "Knee")),
                    selectedKeys = setOf("knee"), onToggle = {}, highlighted = mapOf(Muscle.QUADRICEPS to 1f),
                    description = "Body map",
                )
            }
        }
        composeRule.onNodeWithText("Knee").assertIsOn()
        composeRule.onNodeWithText("No injuries").assertIsOff()
    }

    private fun mutableStateOfBoolean(initial: Boolean) = androidx.compose.runtime.mutableStateOf(initial)
}
