package com.assismohanty.awaken.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.BmiCard
import com.assismohanty.awaken.core.designsystem.components.ChoiceItem
import com.assismohanty.awaken.core.designsystem.components.ImageChoiceGrid
import com.assismohanty.awaken.core.designsystem.components.OnbIllustration
import com.assismohanty.awaken.core.designsystem.components.OnboardingShell
import com.assismohanty.awaken.core.designsystem.components.OnboardingStepBody
import com.assismohanty.awaken.core.designsystem.components.RulerPicker
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.StageSlider
import com.assismohanty.awaken.core.designsystem.components.UnitToggle
import com.assismohanty.awaken.core.designsystem.components.WheelPicker
import com.assismohanty.awaken.core.designsystem.lexicon.LexResources
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.rememberLexResources
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.OnboardingTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.onboarding.BodyMath
import com.assismohanty.awaken.engine.onboarding.IllustrationSet
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.LengthUnit
import com.assismohanty.awaken.engine.onboarding.MassUnit
import com.assismohanty.awaken.engine.onboarding.Obstacle
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.OnboardingRules
import com.assismohanty.awaken.engine.onboarding.OnboardingStep

/** Wires the quiz flow to its ViewModel. Always light, whatever the phone's theme says. */
@Composable
fun OnboardingFlowRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler {
        if (state.step.canGoBack) viewModel.onEvent(OnboardingFlowEvent.Back) else onClose()
    }
    OnboardingTheme {
        ScreenBackground(modifier = modifier) {
            OnboardingFlowScreen(state = state, onEvent = viewModel::onEvent)
        }
    }
}

/** One screen of the quiz flow inside the shared shell. The shell asks for each step by its own number while it slides. */
@Composable
fun OnboardingFlowScreen(
    state: OnboardingFlowState,
    onEvent: (OnboardingFlowEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.loaded) {
        Box(modifier.fillMaxSize())
        return
    }
    val step = state.step
    OnboardingShell(
        stepKey = step.ordinal,
        forward = state.forward,
        segments = OnboardingStep.SEGMENTS,
        progress = step.progress,
        progressDescription = lexString(R.string.onboarding_step, step.ordinal + 1, OnboardingStep.entries.size),
        primaryText = lexString(R.string.action_continue),
        onPrimary = { onEvent(OnboardingFlowEvent.Next) },
        modifier = modifier,
        onBack = if (step.canGoBack) ({ onEvent(OnboardingFlowEvent.Back) }) else null,
        onSkip = if (step.skippable) ({ onEvent(OnboardingFlowEvent.Skip) }) else null,
        primaryEnabled = state.canContinue,
    ) { key ->
        StepBody(OnboardingStep.entries[key], state.answers, onEvent)
    }
}

@Composable
private fun StepBody(step: OnboardingStep, answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    when (step) {
        OnboardingStep.WELCOME -> WelcomeStep()
        OnboardingStep.NAME -> NameStep(answers, onEvent)
        OnboardingStep.GENDER -> GenderStep(answers, onEvent)
        OnboardingStep.AGE -> AgeStep(answers, onEvent)
        OnboardingStep.HEIGHT -> HeightStep(answers, onEvent)
        OnboardingStep.WEIGHT -> WeightStep(answers, onEvent)
        OnboardingStep.BODY_SHAPE -> BodyShapeStep(answers, onEvent)
        OnboardingStep.IMPROVE -> ImproveStep(answers, onEvent)
        OnboardingStep.OBSTACLES -> ObstaclesStep(answers, onEvent)
        else -> LaterStep()
    }
}

@Composable
private fun WelcomeStep() {
    OnboardingStepBody(title = lexString(R.string.onb_welcome_title), subtitle = lexString(R.string.onb_welcome_body)) {
        OnbIllustration(
            image = OnboardingArt.resolve("welcome", "hero", null),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun NameStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val colors = AwakenTheme.colors
    OnboardingStepBody(title = lexString(R.string.onb_name_title), subtitle = lexString(R.string.onb_name_body)) {
        OutlinedTextField(
            value = answers.name,
            onValueChange = { onEvent(OnboardingFlowEvent.NameChanged(it)) },
            label = { Text(lexString(R.string.onboarding_name_label)) },
            supportingText = { Text(lexString(R.string.onb_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(OnboardingFlowEvent.Next) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.divider,
                focusedLabelColor = colors.accent,
                cursorColor = colors.accent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GenderStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val options = listOf(
        IllustrationSet.MALE to lexString(R.string.onb_gender_male),
        IllustrationSet.FEMALE to lexString(R.string.onb_gender_female),
        IllustrationSet.NEUTRAL to lexString(R.string.onb_gender_neutral),
    )
    OnboardingStepBody(title = lexString(R.string.onb_gender_title), subtitle = lexString(R.string.onb_gender_body)) {
        ImageChoiceGrid(
            items = options.map { (set, label) -> ChoiceItem(set.name, label) },
            selectedKeys = setOfNotNull(answers.illustrationSet?.name),
            onToggle = { key -> onEvent(OnboardingFlowEvent.GenderSelected(IllustrationSet.valueOf(key))) },
            multi = false,
            columns = 1,
        )
    }
}

@Composable
private fun AgeStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val ages = (BodyMath.AGE_MIN..BodyMath.AGE_MAX).toList()
    OnboardingStepBody(title = lexString(R.string.onb_age_title), subtitle = lexString(R.string.onb_age_body)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            WheelPicker(
                options = ages,
                selectedIndex = (answers.age ?: BodyMath.DEFAULT_AGE) - BodyMath.AGE_MIN,
                onSelected = { onEvent(OnboardingFlowEvent.AgeChanged(ages[it])) },
                label = { it.toString() },
                description = lexString(R.string.onb_age_cd),
            )
        }
    }
}

/** The height of a ruler tick as the person reads it: "170" or "5′7″". */
private fun heightText(resources: LexResources, unit: LengthUnit, tick: Int): String = when (unit) {
    LengthUnit.CM -> tick.toString()
    LengthUnit.FEET_INCHES -> BodyMath.feetAndInches(tick).let { (feet, inches) -> resources.getString(R.string.onb_height_ftin, feet, inches) }
}

@Composable
private fun HeightStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val unit = answers.heightUnit
    val scale = BodyMath.heightScale(unit)
    val tick = scale.tickOf(answers.heightCm ?: BodyMath.DEFAULT_HEIGHT_CM)
    val unitName = lexString(if (unit == LengthUnit.CM) R.string.onb_unit_cm else R.string.onb_unit_ft)
    val resources = rememberLexResources()
    val shown = heightText(resources, unit, tick)
    val cmText = lexString(R.string.onb_unit_cm)
    val ftText = lexString(R.string.onb_unit_ft)
    OnboardingStepBody(title = lexString(R.string.onb_height_title), subtitle = lexString(R.string.onb_height_body)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                BigValue(value = shown, unit = if (unit == LengthUnit.CM) unitName else "")
                UnitToggle(
                    options = listOf(cmText, ftText),
                    selected = if (unit == LengthUnit.CM) 0 else 1,
                    onSelect = { onEvent(OnboardingFlowEvent.HeightUnitChanged(if (it == 0) LengthUnit.CM else LengthUnit.FEET_INCHES)) },
                )
                OnbIllustration(
                    image = OnboardingArt.resolve("height", "profile", answers.illustrationSet),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            RulerPicker(
                first = scale.first,
                last = scale.last,
                tick = tick,
                onTick = { onEvent(OnboardingFlowEvent.HeightChanged(scale.valueAt(it))) },
                majorEvery = if (unit == LengthUnit.CM) CM_MAJOR else BodyMath.INCHES_PER_FOOT,
                valueText = { "${heightText(resources, unit, it)} $unitName".trim() },
                description = lexString(R.string.onb_height_cd),
                vertical = true,
                majorLabel = { if (unit == LengthUnit.CM) it.toString() else "${it / BodyMath.INCHES_PER_FOOT}′" },
            )
        }
    }
}

@Composable
private fun WeightStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val unit = answers.weightUnit
    val scale = BodyMath.weightScale(unit)
    val kg = answers.weightKg ?: BodyMath.DEFAULT_WEIGHT_KG
    val tick = scale.tickOf(kg)
    val unitName = lexString(if (unit == MassUnit.KG) R.string.onb_unit_kg else R.string.onb_unit_lb)
    val shown = BodyMath.weightTickLabel(unit, tick)
    val kgText = lexString(R.string.onb_unit_kg)
    val lbText = lexString(R.string.onb_unit_lb)
    OnboardingStepBody(title = lexString(R.string.onb_weight_title), subtitle = lexString(R.string.onb_weight_body), scrollable = true) {
        BigValue(value = shown, unit = unitName)
        Box(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
            UnitToggle(
                options = listOf(kgText, lbText),
                selected = if (unit == MassUnit.KG) 0 else 1,
                onSelect = { onEvent(OnboardingFlowEvent.WeightUnitChanged(if (it == 0) MassUnit.KG else MassUnit.LB)) },
            )
        }
        RulerPicker(
            first = scale.first,
            last = scale.last,
            tick = tick,
            onTick = { onEvent(OnboardingFlowEvent.WeightChanged(scale.valueAt(it))) },
            majorEvery = if (unit == MassUnit.KG) KG_MAJOR_TICKS else LB_MAJOR,
            valueText = { "${BodyMath.weightTickLabel(unit, it)} $unitName" },
            description = lexString(R.string.onb_weight_cd),
            majorLabel = { if (unit == MassUnit.KG) (it / 2).toString() else it.toString() },
            modifier = Modifier.padding(vertical = Spacing.md),
        )
        // The scale has a height to work from; without one there is nothing honest to show.
        answers.heightCm?.let { BmiCard(BodyMath.bmi(it, scale.valueAt(tick), answers.age)) }
    }
}

@Composable
private fun BodyShapeStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val stage = answers.bodyStage ?: OnboardingRules.DEFAULT_BODY_STAGE
    OnboardingStepBody(title = lexString(R.string.onb_shape_title), subtitle = lexString(R.string.onb_shape_body)) {
        StageSlider(
            stage = stage,
            onStage = { onEvent(OnboardingFlowEvent.StageChanged(it)) },
            stages = OnboardingRules.BODY_STAGES,
            imageFor = { OnboardingArt.resolve("shape", it.toString(), answers.illustrationSet) },
            description = lexString(R.string.onb_shape_cd),
            stageText = lexString(R.string.onb_shape_stage, stage, OnboardingRules.BODY_STAGES),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun ImproveStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val options = listOf(
        Triple(ImproveGoal.STRENGTH, R.string.onb_improve_strength, "strength"),
        Triple(ImproveGoal.FOCUS, R.string.onb_improve_focus, "focus"),
        Triple(ImproveGoal.SLEEP, R.string.onb_improve_sleep, "sleep"),
        Triple(ImproveGoal.LESS_SCREEN_TIME, R.string.onb_improve_screen, "screen"),
        Triple(ImproveGoal.DISCIPLINE, R.string.onb_improve_discipline, "discipline"),
        Triple(ImproveGoal.FITNESS, R.string.onb_improve_fitness, "fitness"),
        Triple(ImproveGoal.LEARNING, R.string.onb_improve_learning, "learning"),
    )
    OnboardingStepBody(title = lexString(R.string.onb_improve_title), subtitle = lexString(R.string.onb_improve_body)) {
        ImageChoiceGrid(
            items = options.map { (goal, label, art) ->
                ChoiceItem(goal.name, lexString(label), OnboardingArt.resolve("improve", art, null))
            },
            selectedKeys = answers.improve.map { it.name }.toSet(),
            onToggle = { key -> onEvent(OnboardingFlowEvent.ImproveToggled(ImproveGoal.valueOf(key))) },
            multi = true,
        )
    }
}

@Composable
private fun ObstaclesStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val options = listOf(
        Triple(Obstacle.TOO_MUCH_SCROLLING, R.string.onb_obstacle_scrolling, "scrolling"),
        Triple(Obstacle.POOR_SLEEP, R.string.onb_obstacle_sleep, "sleep"),
        Triple(Obstacle.SITTING_ALL_DAY, R.string.onb_obstacle_sitting, "sitting"),
        Triple(Obstacle.PROCRASTINATION, R.string.onb_obstacle_procrastination, "procrastination"),
        Triple(Obstacle.NONE, R.string.onb_obstacle_none, "none"),
    )
    OnboardingStepBody(title = lexString(R.string.onb_obstacles_title), subtitle = lexString(R.string.onb_obstacles_body)) {
        ImageChoiceGrid(
            items = options.map { (obstacle, label, art) ->
                ChoiceItem(obstacle.name, lexString(label), OnboardingArt.resolve("obstacle", art, null))
            },
            selectedKeys = answers.obstacles.map { it.name }.toSet(),
            onToggle = { key -> onEvent(OnboardingFlowEvent.ObstacleToggled(Obstacle.valueOf(key))) },
            multi = true,
        )
    }
}

/** Steps after the first nine arrive in the next batch. */
@Composable
private fun LaterStep() {
    OnboardingStepBody(title = lexString(R.string.onb_later_title), subtitle = lexString(R.string.onb_later_body)) {}
}

private const val CM_MAJOR = 10
private const val KG_MAJOR_TICKS = 10
private const val LB_MAJOR = 10

// region previews

private fun preview(step: OnboardingStep, answers: OnboardingAnswers = OnboardingAnswers()) =
    OnboardingFlowState(loaded = true, step = step, answers = answers)

private val sample = OnboardingAnswers(
    name = "Sam", illustrationSet = IllustrationSet.NEUTRAL, age = 30, heightCm = 170.0, weightKg = 70.0,
    heightUnit = LengthUnit.CM, weightUnit = MassUnit.KG, bodyStage = 3,
)

@Composable
private fun PreviewFlow(state: OnboardingFlowState) {
    AwakenTheme(reduceMotion = true) { ScreenBackground { OnboardingFlowScreen(state, onEvent = {}) } }
}

@PhonePreviews
@Composable
private fun OnboardingWelcomePreview() {
    AwakenTheme(reduceMotion = true) { ScreenBackground { OnboardingFlowScreen(preview(OnboardingStep.WELCOME), onEvent = {}) } }
}

@PhonePreviews
@Composable
private fun OnboardingNamePreview() = PreviewFlow(preview(OnboardingStep.NAME, sample))

/** Nothing typed yet: Continue is off and there is no Skip on this step. */
@PhonePreviews
@Composable
private fun OnboardingNameEmptyPreview() = PreviewFlow(preview(OnboardingStep.NAME))

@PhonePreviews
@Composable
private fun OnboardingGenderPreview() = PreviewFlow(preview(OnboardingStep.GENDER, sample.copy(illustrationSet = IllustrationSet.FEMALE)))

@PhonePreviews
@Composable
private fun OnboardingAgePreview() = PreviewFlow(preview(OnboardingStep.AGE, sample))

@PhonePreviews
@Composable
private fun OnboardingHeightPreview() = PreviewFlow(preview(OnboardingStep.HEIGHT, sample))

@PhonePreviews
@Composable
private fun OnboardingHeightFeetPreview() =
    PreviewFlow(preview(OnboardingStep.HEIGHT, sample.copy(heightUnit = LengthUnit.FEET_INCHES, heightCm = 175.26)))

@PhonePreviews
@Composable
private fun OnboardingWeightPreview() = PreviewFlow(preview(OnboardingStep.WEIGHT, sample))

/** Under 20: the BMI card shows the number and one neutral line, no scale. */
@PhonePreviews
@Composable
private fun OnboardingWeightYoungPreview() = PreviewFlow(preview(OnboardingStep.WEIGHT, sample.copy(age = 16, weightUnit = MassUnit.LB)))

@PhonePreviews
@Composable
private fun OnboardingBodyShapePreview() = PreviewFlow(preview(OnboardingStep.BODY_SHAPE, sample))

@PhonePreviews
@Composable
private fun OnboardingImprovePreview() =
    PreviewFlow(preview(OnboardingStep.IMPROVE, sample.copy(improve = listOf(ImproveGoal.SLEEP, ImproveGoal.FOCUS))))

@PhonePreviews
@Composable
private fun OnboardingObstaclesPreview() =
    PreviewFlow(preview(OnboardingStep.OBSTACLES, sample.copy(obstacles = setOf(Obstacle.TOO_MUCH_SCROLLING))))

// endregion
