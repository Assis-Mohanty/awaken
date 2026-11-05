package com.assismohanty.awaken.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.BodyMapGrid
import com.assismohanty.awaken.core.designsystem.components.ChoiceItem
import com.assismohanty.awaken.core.designsystem.components.DayChips
import com.assismohanty.awaken.core.designsystem.components.ImageChoiceGrid
import com.assismohanty.awaken.core.designsystem.components.OnbIllustration
import com.assismohanty.awaken.core.designsystem.components.OnboardingStepBody
import com.assismohanty.awaken.core.designsystem.components.OnboardingToggleRow
import com.assismohanty.awaken.core.designsystem.components.WheelPicker
import com.assismohanty.awaken.core.designsystem.components.formatMinutes
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.rememberLexResources
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.onboarding.EquipmentChoice
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.OnboardingMapping
import com.assismohanty.awaken.engine.onboarding.OnboardingRules
import com.assismohanty.awaken.engine.onboarding.OnboardingStep
import com.assismohanty.awaken.engine.onboarding.Pace
import com.assismohanty.awaken.engine.onboarding.ScreenTimeBand
import com.assismohanty.awaken.engine.onboarding.SleepWheels
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

// Steps 10 to 17 of the quiz flow (docs/onboarding/PLAN.md §4). Layout only: the rules are in :engine.

@Composable
internal fun ScreenTimeStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val options = listOf(
        ScreenTimeBand.UNDER_2H to R.string.onb_screen_under2,
        ScreenTimeBand.H2_TO_4 to R.string.onb_screen_2to4,
        ScreenTimeBand.H4_TO_6 to R.string.onb_screen_4to6,
        ScreenTimeBand.OVER_6H to R.string.onb_screen_over6,
    )
    OnboardingStepBody(title = lexString(R.string.onb_screen_title), subtitle = lexString(R.string.onb_screen_body)) {
        ImageChoiceGrid(
            items = options.map { (band, label) -> ChoiceItem(band.name, lexString(label)) },
            selectedKeys = setOfNotNull(answers.screenTime?.name),
            onToggle = { key -> onEvent(OnboardingFlowEvent.ScreenTimeSelected(ScreenTimeBand.valueOf(key))) },
            multi = false,
            columns = 1,
        )
    }
}

/** A time of day the way the phone writes it ("11:00 PM" or "23:00"). */
private fun timeLabel(minuteOfDay: Int): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE))

@Composable
internal fun SleepStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val dayStart = GameDay.DEFAULT_DAY_START_HOUR
    val (bed, wake) = OnboardingRules.sleepShown(answers.bedtimeMinute, answers.wakeMinute, dayStart)
    val bedtimes = SleepWheels.bedtimes
    // Only wake times that make a window the phone-down night accepts are offered for this bedtime.
    val wakes = SleepWheels.wakeTimesFor(bed, dayStart)
    OnboardingStepBody(title = lexString(R.string.onb_sleep_title), subtitle = lexString(R.string.onb_sleep_body)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(lexString(R.string.onb_sleep_bedtime), style = AwakenTheme.type.label, color = AwakenTheme.colors.textSecondary)
                WheelPicker(
                    options = bedtimes,
                    selectedIndex = bedtimes.indexOf(bed).coerceAtLeast(0),
                    onSelected = { onEvent(OnboardingFlowEvent.BedtimeChanged(bedtimes[it])) },
                    label = ::timeLabel,
                    description = lexString(R.string.onb_sleep_bedtime_cd),
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(lexString(R.string.onb_sleep_wake), style = AwakenTheme.type.label, color = AwakenTheme.colors.textSecondary)
                WheelPicker(
                    options = wakes,
                    selectedIndex = wakes.indexOf(wake).coerceAtLeast(0),
                    onSelected = { onEvent(OnboardingFlowEvent.WakeChanged(wakes[it])) },
                    label = ::timeLabel,
                    description = lexString(R.string.onb_sleep_wake_cd),
                )
            }
        }
        Text(
            text = lexString(R.string.onb_sleep_length, formatMinutes(BedtimeRules.windowMinutes(bed, wake))),
            style = AwakenTheme.type.body,
            color = AwakenTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
    }
}

@Composable
internal fun FitnessStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val options = listOf(
        FitnessLevel.BEGINNER to R.string.onb_fitness_beginner,
        FitnessLevel.INTERMEDIATE to R.string.onb_fitness_intermediate,
        FitnessLevel.ADVANCED to R.string.onb_fitness_advanced,
    )
    OnboardingStepBody(title = lexString(R.string.onb_fitness_title), subtitle = lexString(R.string.onb_fitness_body)) {
        ImageChoiceGrid(
            items = options.map { (level, label) ->
                ChoiceItem(level.name, lexString(label), OnboardingArt.resolve("fitness", level.name.lowercase(), answers.illustrationSet))
            },
            selectedKeys = setOfNotNull(answers.fitness?.name),
            onToggle = { key -> onEvent(OnboardingFlowEvent.FitnessSelected(FitnessLevel.valueOf(key))) },
            multi = false,
        )
    }
}

@Composable
internal fun EquipmentStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val options = listOf(
        EquipmentChoice.BODYWEIGHT to R.string.onb_equipment_bodyweight,
        EquipmentChoice.DUMBBELLS to R.string.onb_equipment_dumbbells,
        EquipmentChoice.GYM to R.string.onb_equipment_gym,
    )
    OnboardingStepBody(title = lexString(R.string.onb_equipment_title), subtitle = lexString(R.string.onb_equipment_body)) {
        ImageChoiceGrid(
            items = options.map { (choice, label) ->
                ChoiceItem(choice.name, lexString(label), OnboardingArt.resolve("equipment", choice.name.lowercase(), null))
            },
            selectedKeys = setOfNotNull(answers.equipment?.name),
            onToggle = { key -> onEvent(OnboardingFlowEvent.EquipmentSelected(EquipmentChoice.valueOf(key))) },
            multi = false,
        )
    }
}

@Composable
internal fun InjuriesStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val options = listOf(
        InjuryRegion.NO_INJURIES to R.string.onb_injury_none,
        InjuryRegion.SHOULDERS to R.string.onb_injury_shoulders,
        InjuryRegion.BACK to R.string.onb_injury_back,
        InjuryRegion.WRIST to R.string.onb_injury_wrist,
        InjuryRegion.KNEE to R.string.onb_injury_knee,
        InjuryRegion.HIP to R.string.onb_injury_hip,
    )
    val lit: Map<Muscle, Float> = answers.injuries.flatMap { OnboardingMapping.injuryMuscles(it) }.associateWith { 1f }
    OnboardingStepBody(title = lexString(R.string.onb_injuries_title), subtitle = lexString(R.string.onb_injuries_body), scrollable = true) {
        BodyMapGrid(
            options = options.map { (region, label) -> ChoiceItem(region.name, lexString(label)) },
            selectedKeys = answers.injuries.map { it.name }.toSet(),
            onToggle = { key -> onEvent(OnboardingFlowEvent.InjuryToggled(InjuryRegion.valueOf(key))) },
            highlighted = lit,
            description = lexString(R.string.onb_injury_map_cd),
        )
    }
}

@Composable
internal fun TrainingDaysStep(answers: OnboardingAnswers, today: DayOfWeek, onEvent: (OnboardingFlowEvent) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val resources = rememberLexResources()
    OnboardingStepBody(title = lexString(R.string.onb_days_title), subtitle = lexString(R.string.onb_days_body)) {
        DayChips(
            selectedDays = answers.trainingDays,
            today = today.value,
            onToggle = { onEvent(OnboardingFlowEvent.TrainingDayToggled(it)) },
            dayLetter = { DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, locale) },
            dayDescription = { day, isToday ->
                val name = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)
                if (isToday) resources.getString(R.string.onb_days_today_cd, name) else name
            },
            todayLabel = lexString(R.string.onb_days_today),
        )
        // A reminder needs days to remind about, so the switch only appears once one is picked.
        if (answers.trainingDays.isNotEmpty()) {
            OnboardingToggleRow(
                title = lexString(R.string.onb_days_remind),
                checked = answers.trainingReminder,
                onChange = { onEvent(OnboardingFlowEvent.TrainingReminderChanged(it)) },
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }
}

@Composable
internal fun PaceStep(answers: OnboardingAnswers, onEvent: (OnboardingFlowEvent) -> Unit) {
    val options = listOf(
        Triple(Pace.STEADY, R.string.onb_pace_steady, R.string.onb_pace_steady_detail),
        Triple(Pace.BALANCED, R.string.onb_pace_balanced, R.string.onb_pace_balanced_detail),
        Triple(Pace.PUSH_HARD, R.string.onb_pace_push, R.string.onb_pace_push_detail),
    )
    OnboardingStepBody(title = lexString(R.string.onb_pace_title), subtitle = lexString(R.string.onb_pace_body)) {
        ImageChoiceGrid(
            items = options.map { (pace, label, detail) -> ChoiceItem(pace.name, lexString(label), detail = lexString(detail)) },
            selectedKeys = setOfNotNull(answers.pace?.name),
            onToggle = { key -> onEvent(OnboardingFlowEvent.PaceSelected(Pace.valueOf(key))) },
            multi = false,
            columns = 1,
        )
    }
}

/** One permission, explained before the system asks. The button and "Not now" are the shell's. */
@Composable
internal fun PermissionStep(step: OnboardingStep, permissions: OnboardingPermissionState) {
    val (title, body, art) = when (step) {
        OnboardingStep.HEALTH -> Triple(R.string.onb_health_title, R.string.onb_health_body, "health")
        OnboardingStep.USAGE -> Triple(R.string.onb_usage_title, R.string.onb_usage_body, "usage")
        else -> Triple(R.string.onb_notifications_title, R.string.onb_notifications_body, "notifications")
    }
    val note: Int? = when (step) {
        OnboardingStep.HEALTH -> when {
            permissions.health -> R.string.onb_permission_allowed
            permissions.healthNotInstalled -> R.string.health_not_installed
            permissions.healthDeclined -> R.string.health_declined
            else -> null
        }
        OnboardingStep.USAGE -> if (permissions.usage) R.string.onb_permission_allowed else R.string.screentime_access_steps
        else -> if (permissions.notifications) R.string.onb_permission_allowed else null
    }
    OnboardingStepBody(title = lexString(title), subtitle = lexString(body), scrollable = true) {
        OnbIllustration(
            image = OnboardingArt.resolve("permission", art, null),
            modifier = Modifier.fillMaxWidth().height(AwakenTheme.dimens.onbPermissionImage),
        )
        if (note != null) {
            Text(
                text = lexString(note),
                style = AwakenTheme.type.body,
                color = AwakenTheme.colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }
}

private const val SECONDS_PER_MINUTE = 60L

// region previews

@PhonePreviews
@Composable
private fun OnboardingScreenTimePreview() =
    PreviewFlow(previewFlowState(OnboardingStep.SCREEN_TIME, previewAnswers.copy(screenTime = ScreenTimeBand.H2_TO_4)))

@PhonePreviews
@Composable
private fun OnboardingSleepPreview() = PreviewFlow(previewFlowState(OnboardingStep.SLEEP, previewAnswers.copy(bedtimeMinute = 23 * 60, wakeMinute = 6 * 60 + 30)))

@PhonePreviews
@Composable
private fun OnboardingFitnessPreview() =
    PreviewFlow(previewFlowState(OnboardingStep.FITNESS, previewAnswers.copy(fitness = FitnessLevel.INTERMEDIATE)))

@PhonePreviews
@Composable
private fun OnboardingEquipmentPreview() =
    PreviewFlow(previewFlowState(OnboardingStep.EQUIPMENT, previewAnswers.copy(equipment = EquipmentChoice.DUMBBELLS)))

@PhonePreviews
@Composable
private fun OnboardingInjuriesPreview() =
    PreviewFlow(previewFlowState(OnboardingStep.INJURIES, previewAnswers.copy(injuries = setOf(InjuryRegion.SHOULDERS, InjuryRegion.KNEE))))

@PhonePreviews
@Composable
private fun OnboardingTrainingDaysPreview() =
    PreviewFlow(previewFlowState(OnboardingStep.TRAINING_DAYS, previewAnswers.copy(trainingDays = setOf(1, 3, 5), trainingReminder = true)))

/** Nothing picked yet: no reminder switch, because there is nothing to remind about. */
@PhonePreviews
@Composable
private fun OnboardingTrainingDaysEmptyPreview() = PreviewFlow(previewFlowState(OnboardingStep.TRAINING_DAYS, previewAnswers))

/** The selected pace opens its one-line explanation. */
@PhonePreviews
@Composable
private fun OnboardingPacePreview() = PreviewFlow(previewFlowState(OnboardingStep.PACE, previewAnswers.copy(pace = Pace.BALANCED)))

@PhonePreviews
@Composable
private fun OnboardingHealthPreview() = PreviewFlow(previewFlowState(OnboardingStep.HEALTH))

@PhonePreviews
@Composable
private fun OnboardingHealthDeclinedPreview() =
    PreviewFlow(previewFlowState(OnboardingStep.HEALTH, permissions = OnboardingPermissionState(healthDeclined = true)))

@PhonePreviews
@Composable
private fun OnboardingUsagePreview() = PreviewFlow(previewFlowState(OnboardingStep.USAGE))

/** Already allowed: the same button now just continues. */
@PhonePreviews
@Composable
private fun OnboardingUsageAllowedPreview() =
    PreviewFlow(previewFlowState(OnboardingStep.USAGE, permissions = OnboardingPermissionState(usage = true)))

@PhonePreviews
@Composable
private fun OnboardingNotificationsPreview() = PreviewFlow(previewFlowState(OnboardingStep.NOTIFICATIONS))

// endregion
