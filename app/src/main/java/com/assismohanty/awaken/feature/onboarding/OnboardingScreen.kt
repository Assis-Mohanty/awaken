package com.assismohanty.awaken.feature.onboarding

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.TypeOnText
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.feature.settings.HealthActionEffect
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.paths.AiConsentSheet
import java.time.LocalTime

@Composable
fun OnboardingRoute(modifier: Modifier = Modifier, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HealthActionEffect(
        action = state.healthAction,
        permissions = state.healthPermissions,
        onPermissionsResult = { viewModel.onEvent(OnboardingEvent.HealthPermissionsResult(it)) },
        onHandled = { viewModel.onEvent(OnboardingEvent.HealthActionHandled) },
    )
    OnboardingScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Awakening (DESIGN_SYSTEM §4.1): eight steps in one System window, no account. */
@Composable
fun OnboardingScreen(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    val motion = AwakenTheme.motion
    BackHandler(enabled = state.step != OnboardingStep.WELCOME && !state.submitting) { onEvent(OnboardingEvent.Back) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                fadeIn(tween(motion.duration(motion.standardMs), easing = motion.standardEasing)) togetherWith
                    fadeOut(tween(motion.duration(motion.quickMs)))
            },
            label = "onboarding-step",
        ) { step ->
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            ) {
                SystemWindow(title = stringResource(step.titleRes), accent = AwakenTheme.colors.system) {
                    Text(
                        text = stringResource(R.string.onboarding_step, step.ordinal + 1, OnboardingStep.entries.size),
                        style = AwakenTheme.type.caption,
                        color = AwakenTheme.colors.fog,
                    )
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeStep(onEvent)
                        OnboardingStep.NAME -> NameStep(state, onEvent)
                        OnboardingStep.FOCUS -> FocusStep(state, onEvent)
                        OnboardingStep.CALIBRATION -> CalibrationStep(state, onEvent)
                        OnboardingStep.HEALTH -> HealthStep(state, onEvent)
                        OnboardingStep.PATH -> PathStep(state, onEvent)
                        OnboardingStep.REMINDER -> ReminderStep(state, onEvent)
                        OnboardingStep.COMPLETE -> CompleteStep(state, onEvent)
                    }
                }
            }
        }
    }
}

private val OnboardingStep.titleRes: Int
    @StringRes get() = when (this) {
        OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
        OnboardingStep.NAME -> R.string.onboarding_name_title
        OnboardingStep.FOCUS -> R.string.onboarding_focus_title
        OnboardingStep.CALIBRATION -> R.string.onboarding_calibration_title
        OnboardingStep.HEALTH -> R.string.onboarding_health_title
        OnboardingStep.PATH -> R.string.onboarding_path_title
        OnboardingStep.REMINDER -> R.string.onboarding_reminder_title
        OnboardingStep.COMPLETE -> R.string.onboarding_complete_title
    }

@Composable
private fun WelcomeStep(onEvent: (OnboardingEvent) -> Unit) {
    TypeOnText(
        text = stringResource(R.string.onboarding_welcome_body),
        style = AwakenTheme.type.body,
        color = AwakenTheme.colors.bone,
    )
    PrimaryButton(
        text = stringResource(R.string.action_accept),
        onClick = { onEvent(OnboardingEvent.Next) },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
    )
}

@Composable
private fun NameStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    val colors = AwakenTheme.colors
    OutlinedTextField(
        value = state.name,
        onValueChange = { onEvent(OnboardingEvent.NameChanged(it)) },
        label = { Text(stringResource(R.string.onboarding_name_label)) },
        isError = state.showNameError,
        supportingText = if (state.showNameError) {
            { Text(stringResource(R.string.onboarding_name_error)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onEvent(OnboardingEvent.Next) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.system,
            unfocusedBorderColor = colors.edge,
            focusedLabelColor = colors.system,
            cursorColor = colors.system,
            errorBorderColor = colors.ember,
            errorLabelColor = colors.ember,
            errorSupportingTextColor = colors.ember,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    StepButtons(onEvent, continueEnabled = state.name.isNotBlank())
}

@Composable
private fun FocusStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Text(stringResource(R.string.onboarding_focus_body), style = AwakenTheme.type.body, color = AwakenTheme.colors.mist)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Stat.entries.forEach { stat ->
            val selected = stat in state.focus
            ChoiceChip(
                text = stringResource(R.string.onboarding_focus_option, stat.label(), stringResource(stat.meaningRes)),
                selected = selected,
                onClick = { onEvent(OnboardingEvent.FocusToggled(stat)) },
                color = AwakenTheme.colors.stat(stat),
                icon = AwakenGlyphs.stat(stat),
                multiSelect = true,
                enabled = selected || state.focus.size < OnboardingUiState.FOCUS_COUNT,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    StepButtons(onEvent, continueEnabled = state.canContinue)
}

private val Stat.meaningRes: Int
    @StringRes get() = when (this) {
        Stat.STR -> R.string.stat_str_meaning
        Stat.INT -> R.string.stat_int_meaning
        Stat.WIL -> R.string.stat_wil_meaning
        Stat.VIT -> R.string.stat_vit_meaning
        Stat.CHA -> R.string.stat_cha_meaning
    }

@Composable
private fun CalibrationStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    CalibrationQuestion.entries.forEach { question ->
        Text(
            text = stringResource(question.promptRes),
            style = AwakenTheme.type.label,
            color = AwakenTheme.colors.bone,
            modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
        )
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Awakening.Answer.entries.forEach { answer ->
                ChoiceChip(
                    text = stringResource(question.answerRes(answer)),
                    selected = state.answers[question] == answer,
                    onClick = { onEvent(OnboardingEvent.Answered(question, answer)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    StepButtons(onEvent, continueEnabled = state.canContinue)
}

private val CalibrationQuestion.promptRes: Int
    @StringRes get() = when (this) {
        CalibrationQuestion.TRAINING -> R.string.calibration_training
        CalibrationQuestion.STUDY -> R.string.calibration_study
        CalibrationQuestion.SLEEP -> R.string.calibration_sleep
    }

@StringRes
private fun CalibrationQuestion.answerRes(answer: Awakening.Answer): Int = when (this) {
    CalibrationQuestion.TRAINING -> when (answer) {
        Awakening.Answer.LOW -> R.string.calibration_training_low
        Awakening.Answer.MID -> R.string.calibration_training_mid
        Awakening.Answer.HIGH -> R.string.calibration_training_high
    }
    CalibrationQuestion.STUDY -> when (answer) {
        Awakening.Answer.LOW -> R.string.calibration_study_low
        Awakening.Answer.MID -> R.string.calibration_study_mid
        Awakening.Answer.HIGH -> R.string.calibration_study_high
    }
    CalibrationQuestion.SLEEP -> when (answer) {
        Awakening.Answer.LOW -> R.string.calibration_sleep_low
        Awakening.Answer.MID -> R.string.calibration_sleep_mid
        Awakening.Answer.HIGH -> R.string.calibration_sleep_high
    }
}

@Composable
private fun HealthStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Text(stringResource(R.string.onboarding_health_body), style = AwakenTheme.type.body, color = AwakenTheme.colors.bone)
    when {
        state.healthNotInstalled -> Text(stringResource(R.string.health_not_installed), style = AwakenTheme.type.label, color = AwakenTheme.colors.mist)
        state.healthDeclined -> Text(stringResource(R.string.health_declined), style = AwakenTheme.type.label, color = AwakenTheme.colors.mist)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PrimaryButton(
            text = stringResource(R.string.action_connect),
            onClick = { onEvent(OnboardingEvent.ConnectHealth) },
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.action_not_now),
            onClick = { onEvent(OnboardingEvent.Next) },
            modifier = Modifier.fillMaxWidth(),
        )
        QuietButton(text = stringResource(R.string.action_back), onClick = { onEvent(OnboardingEvent.Back) })
    }
}

/**
 * Step 6 "Your first Path" (DESIGN_SYSTEM §4.1): optional. [Skip] sits beside [Continue] at the same size, and Continue
 * opens the consent sheet before anything is sent. The first quest never waits on this.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PathStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Text(stringResource(R.string.onboarding_path_body), style = AwakenTheme.type.body, color = colors.mist)
    OutlinedTextField(
        value = state.pathGoal,
        onValueChange = { onEvent(OnboardingEvent.PathGoalChanged(it)) },
        label = { Text(stringResource(R.string.editor_goal_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        colors = onboardingFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(
            R.string.editor_example_cf, R.string.editor_example_run, R.string.editor_example_read, R.string.editor_example_japanese,
        ).forEach { example ->
            val text = stringResource(example)
            ChoiceChip(
                text = text,
                selected = state.pathGoal == text,
                onClick = { onEvent(OnboardingEvent.PathGoalChanged(text)) },
            )
        }
    }
    OutlinedTextField(
        value = state.pathLevel,
        onValueChange = { onEvent(OnboardingEvent.PathLevelChanged(it)) },
        label = { Text(stringResource(R.string.editor_level_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canContinue) onEvent(OnboardingEvent.Next) }),
        colors = onboardingFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SecondaryButton(
            text = stringResource(R.string.action_skip),
            onClick = { onEvent(OnboardingEvent.SkipPath) },
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = { onEvent(OnboardingEvent.Next) },
            enabled = state.canContinue,
            modifier = Modifier.weight(1f),
        )
    }
    QuietButton(text = stringResource(R.string.action_back), onClick = { onEvent(OnboardingEvent.Back) })
    if (state.showConsent) {
        AiConsentSheet(
            onEnable = { onEvent(OnboardingEvent.EnableAiConsent) },
            onNotNow = { onEvent(OnboardingEvent.DeclineAiConsent) },
        )
    }
}

@Composable
private fun onboardingFieldColors() = AwakenTheme.colors.let { colors ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.system,
        unfocusedBorderColor = colors.edge,
        focusedLabelColor = colors.system,
        cursorColor = colors.system,
        errorBorderColor = colors.ember,
        errorLabelColor = colors.ember,
        errorSupportingTextColor = colors.ember,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ReminderStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Text(stringResource(R.string.onboarding_reminder_body), style = AwakenTheme.type.body, color = AwakenTheme.colors.mist)
    val timeState = rememberTimePickerState(
        initialHour = state.reminder.hour,
        initialMinute = state.reminder.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    LaunchedEffect(timeState) {
        snapshotFlow { LocalTime.of(timeState.hour, timeState.minute) }
            .collect { onEvent(OnboardingEvent.ReminderChanged(it)) }
    }
    // TimeInput (not the dial) so the step fits small phones.
    TimeInput(state = timeState, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.sm))
    StepButtons(onEvent, continueEnabled = true)
}

@Composable
private fun CompleteStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Text(stringResource(R.string.onboarding_complete_body), style = AwakenTheme.type.body, color = AwakenTheme.colors.bone)
    if (state.failed) {
        Text(stringResource(R.string.msg_error), style = AwakenTheme.type.label, color = AwakenTheme.colors.ember)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        QuietButton(text = stringResource(R.string.action_back), onClick = { onEvent(OnboardingEvent.Back) }, enabled = !state.submitting)
        PrimaryButton(
            text = stringResource(R.string.action_enter),
            onClick = { onEvent(OnboardingEvent.Finish) },
            enabled = !state.submitting,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StepButtons(onEvent: (OnboardingEvent) -> Unit, continueEnabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        QuietButton(text = stringResource(R.string.action_back), onClick = { onEvent(OnboardingEvent.Back) })
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = { onEvent(OnboardingEvent.Next) },
            enabled = continueEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@PhonePreviews
@Composable
private fun OnboardingWelcomePreview() {
    AwakenTheme { OnboardingScreen(OnboardingUiState(), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun OnboardingNamePreview() {
    AwakenTheme { OnboardingScreen(OnboardingUiState(step = OnboardingStep.NAME, name = "Al", showNameError = true), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun OnboardingFocusPreview() {
    AwakenTheme { OnboardingScreen(OnboardingUiState(step = OnboardingStep.FOCUS, focus = setOf(Stat.STR, Stat.INT)), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun OnboardingCalibrationPreview() {
    AwakenTheme {
        OnboardingScreen(
            OnboardingUiState(step = OnboardingStep.CALIBRATION, answers = mapOf(CalibrationQuestion.TRAINING to Awakening.Answer.MID)),
            onEvent = {},
        )
    }
}

@PhonePreviews
@Composable
private fun OnboardingHealthPreview() {
    AwakenTheme { OnboardingScreen(OnboardingUiState(step = OnboardingStep.HEALTH, healthNotInstalled = true), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun OnboardingPathPreview() {
    AwakenTheme {
        OnboardingScreen(OnboardingUiState(step = OnboardingStep.PATH, pathGoal = "Run a 10K", pathLevel = "5K in 32 minutes"), onEvent = {})
    }
}

@PhonePreviews
@Composable
private fun OnboardingPathConsentPreview() {
    AwakenTheme {
        OnboardingScreen(
            OnboardingUiState(step = OnboardingStep.PATH, pathGoal = "Run a 10K", showConsent = true),
            onEvent = {},
        )
    }
}

@PhonePreviews
@Composable
private fun OnboardingReminderPreview() {
    AwakenTheme { OnboardingScreen(OnboardingUiState(step = OnboardingStep.REMINDER), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun OnboardingCompletePreview() {
    AwakenTheme { OnboardingScreen(OnboardingUiState(step = OnboardingStep.COMPLETE), onEvent = {}) }
}
