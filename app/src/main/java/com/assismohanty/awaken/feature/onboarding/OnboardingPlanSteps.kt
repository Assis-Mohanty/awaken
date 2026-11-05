package com.assismohanty.awaken.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.DayChips
import com.assismohanty.awaken.core.designsystem.components.OnboardingShell
import com.assismohanty.awaken.core.designsystem.components.OnboardingStepBody
import com.assismohanty.awaken.core.designsystem.components.PlanRing
import com.assismohanty.awaken.core.designsystem.components.PlanSummaryCard
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.RulerPicker
import com.assismohanty.awaken.core.designsystem.components.ScreenBackground
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.StepperRow
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.formatMinutes
import com.assismohanty.awaken.core.designsystem.components.formatNumber
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.lexicon.rememberLexResources
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.OnboardingQuestLine
import com.assismohanty.awaken.engine.FocusGoalRules
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.StepGoalRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.onboarding.BodyMath
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.MassUnit
import com.assismohanty.awaken.engine.onboarding.Obstacle
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.Pace
import com.assismohanty.awaken.engine.onboarding.ScreenTimeBand
import com.assismohanty.awaken.engine.onboarding.OnboardingPlan
import com.assismohanty.awaken.engine.onboarding.OnboardingPlanner
import com.assismohanty.awaken.engine.onboarding.OnboardingStep
import com.assismohanty.awaken.engine.onboarding.WeightTargetRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Steps 18 and 19: building the plan and showing it (docs/onboarding/PLAN.md §4, §7). Layout only: the numbers come from the engine.

@Composable
private fun planLines(plan: OnboardingPlan): List<String> = buildList {
    val (first, second) = plan.focusStats
    add(lexString(R.string.onb_build_focus, lexString(first.nameRes()), lexString(second.nameRes())))
    add(lexString(R.string.onb_build_quests))
    add(lexString(R.string.onb_build_steps, formatCount(plan.stepGoal)))
    add(lexString(R.string.onb_build_focus_goal, formatMinutes(plan.focusGoalMinutes)))
    plan.discipline?.let { add(lexString(R.string.onb_build_limits, formatMinutes(it.limitMinutes))) }
    if (plan.phoneDown != null) add(lexString(R.string.onb_build_night))
    if (plan.trainingDays.isNotEmpty()) add(lexString(R.string.onb_build_days))
}

/**
 * The building screen. The ring runs the full OB15 time across the lines of what is being set; each line is a value from the
 * plan, never a made-up step. It only moves on once the first plan's quests have really been worked out.
 */
@Composable
internal fun BuildingStep(state: OnboardingFlowState, onEvent: (OnboardingFlowEvent) -> Unit) {
    val plan = state.plan
    val lines = planLines(plan)
    val reduceMotion = AwakenTheme.motion.reduceMotion
    val ring = remember { Animatable(0f) }
    var lineIndex by remember { mutableIntStateOf(0) }
    var timelineDone by remember { mutableStateOf(false) }
    LaunchedEffect(lines.size) {
        timelineDone = false
        val perLine = OnboardingPlanner.BUILD_MILLIS / lines.size
        lines.indices.forEach { index ->
            lineIndex = index
            val target = (index + 1f) / lines.size
            if (reduceMotion) {
                // No sweep, but the same time on each line so it can be read.
                ring.snapTo(target)
                delay(perLine.toLong())
            } else {
                ring.animateTo(target, tween(perLine, easing = LinearEasing))
            }
        }
        timelineDone = true
    }
    LaunchedEffect(timelineDone, state.build) {
        if (timelineDone && state.build is PlanBuild.Ready) onEvent(OnboardingFlowEvent.BuildFinished)
    }
    BuildingContent(
        progress = ring.value,
        line = lines.getOrElse(lineIndex) { lines.last() },
        failed = state.build == PlanBuild.Failed,
        onRetry = { onEvent(OnboardingFlowEvent.RetryBuild) },
    )
}

/** The building screen without its timeline, so it can be drawn at a given moment. */
@Composable
internal fun BuildingContent(progress: Float, line: String, failed: Boolean, onRetry: () -> Unit) {
    OnboardingStepBody(title = lexString(R.string.onb_build_title), subtitle = lexString(R.string.onb_build_body)) {
        if (failed) {
            Text(lexString(R.string.onb_build_failed), style = AwakenTheme.type.body, color = AwakenTheme.colors.textPrimary)
            SecondaryButton(text = lexString(R.string.onb_build_retry), onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
        } else {
            val percent = (progress * PERCENT).roundToInt()
            PlanRing(
                progress = progress,
                percentText = "$percent%",
                line = line,
                description = lexString(R.string.onb_build_cd, percent),
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
    }
}

/** The plan, one card per thing that was set, each with an Edit where the person can change it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanStep(state: OnboardingFlowState, onEvent: (OnboardingFlowEvent) -> Unit) {
    val plan = state.plan
    val locale = LocalLocale.current.platformLocale
    val resources = rememberLexResources()
    // One card is open for editing at a time ("" = none); saved so a rotation keeps it open.
    var editing by rememberSaveable { mutableStateOf("") }
    fun toggle(key: String) {
        editing = if (editing == key) "" else key
    }
    OnboardingStepBody(title = lexString(R.string.onb_plan_title), subtitle = lexString(R.string.onb_plan_body), scrollable = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            if (state.commitFailed) {
                Text(lexString(R.string.onb_plan_save_failed), style = AwakenTheme.type.title, color = AwakenTheme.colors.textPrimary)
            }

            val (first, second) = plan.focusStats
            PlanSummaryCard(
                title = lexString(R.string.onb_plan_focus_title),
                value = lexString(R.string.onb_plan_focus_value, lexString(first.nameRes()), lexString(second.nameRes())),
                note = lexString(R.string.onb_plan_focus_note),
                editLabel = lexString(if (editing == KEY_FOCUS) R.string.onb_plan_done else R.string.onb_plan_edit),
                onEditToggle = { toggle(KEY_FOCUS) },
                expanded = editing == KEY_FOCUS,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Stat.entries.forEach { stat ->
                        ChoiceChip(
                            text = lexString(stat.nameRes()),
                            selected = stat in plan.focusStats,
                            onClick = { onEvent(OnboardingFlowEvent.PlanFocusStatTapped(stat)) },
                            multiSelect = true,
                        )
                    }
                }
            }

            QuestsCard(state.build)

            PlanSummaryCard(
                title = lexString(R.string.onb_plan_focus_goal_title),
                value = lexString(R.string.onb_plan_per_day, formatMinutes(plan.focusGoalMinutes)),
                editLabel = lexString(if (editing == KEY_FOCUS_GOAL) R.string.onb_plan_done else R.string.onb_plan_edit),
                onEditToggle = { toggle(KEY_FOCUS_GOAL) },
                expanded = editing == KEY_FOCUS_GOAL,
            ) {
                StepperRow(
                    valueText = formatMinutes(plan.focusGoalMinutes),
                    onLess = { onEvent(OnboardingFlowEvent.PlanFocusGoalChanged(plan.focusGoalMinutes - FocusGoalRules.GOAL_STEP)) },
                    onMore = { onEvent(OnboardingFlowEvent.PlanFocusGoalChanged(plan.focusGoalMinutes + FocusGoalRules.GOAL_STEP)) },
                    canLess = plan.focusGoalMinutes > FocusGoalRules.MIN_GOAL,
                    canMore = plan.focusGoalMinutes < FocusGoalRules.MAX_GOAL,
                    lessDescription = lexString(R.string.onb_plan_less),
                    moreDescription = lexString(R.string.onb_plan_more),
                )
            }

            PlanSummaryCard(
                title = lexString(R.string.onb_plan_steps_title),
                value = lexString(R.string.onb_plan_steps_value, formatCount(plan.stepGoal)),
                editLabel = lexString(if (editing == KEY_STEPS) R.string.onb_plan_done else R.string.onb_plan_edit),
                onEditToggle = { toggle(KEY_STEPS) },
                expanded = editing == KEY_STEPS,
            ) {
                StepperRow(
                    valueText = formatCount(plan.stepGoal),
                    onLess = { onEvent(OnboardingFlowEvent.PlanStepGoalChanged(plan.stepGoal - StepGoalRules.GOAL_STEP)) },
                    onMore = { onEvent(OnboardingFlowEvent.PlanStepGoalChanged(plan.stepGoal + StepGoalRules.GOAL_STEP)) },
                    canLess = plan.stepGoal > StepGoalRules.MIN_GOAL,
                    canMore = plan.stepGoal < StepGoalRules.MAX_GOAL,
                    lessDescription = lexString(R.string.onb_plan_less),
                    moreDescription = lexString(R.string.onb_plan_more),
                )
            }

            val dayNames = (1..DAYS_IN_WEEK).associateWith { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
            PlanSummaryCard(
                title = lexString(R.string.onb_plan_days_title),
                value = plan.trainingDays.sorted().joinToString(", ") { dayNames.getValue(it) }.ifEmpty { lexString(R.string.onb_plan_days_none) },
                note = if (plan.trainingReminder) lexString(R.string.onb_plan_days_reminder) else null,
                editLabel = lexString(if (editing == KEY_DAYS) R.string.onb_plan_done else R.string.onb_plan_edit),
                onEditToggle = { toggle(KEY_DAYS) },
                expanded = editing == KEY_DAYS,
            ) {
                DayChips(
                    selectedDays = plan.trainingDays,
                    today = state.today.value,
                    onToggle = { onEvent(OnboardingFlowEvent.PlanTrainingDayToggled(it)) },
                    dayLetter = { DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, locale) },
                    dayDescription = { day, isToday ->
                        val name = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)
                        if (isToday) resources.getString(R.string.onb_days_today_cd, name) else name
                    },
                    todayLabel = lexString(R.string.onb_days_today),
                )
            }

            val discipline = plan.discipline
            if (discipline != null) {
                PlanSummaryCard(
                    title = lexString(R.string.onb_plan_limits_title),
                    value = lexString(R.string.onb_plan_limits_value, formatMinutes(discipline.limitMinutes)),
                    note = lexString(R.string.onb_plan_limits_note),
                    editLabel = lexString(if (editing == KEY_LIMITS) R.string.onb_plan_done else R.string.onb_plan_edit),
                    onEditToggle = { toggle(KEY_LIMITS) },
                    expanded = editing == KEY_LIMITS,
                ) {
                    StepperRow(
                        valueText = formatMinutes(discipline.limitMinutes),
                        onLess = { onEvent(OnboardingFlowEvent.PlanAppLimitChanged(discipline.limitMinutes - ScreenTimeRules.LIMIT_STEP_MINUTES)) },
                        onMore = { onEvent(OnboardingFlowEvent.PlanAppLimitChanged(discipline.limitMinutes + ScreenTimeRules.LIMIT_STEP_MINUTES)) },
                        canLess = discipline.limitMinutes > ScreenTimeRules.MIN_LIMIT_MINUTES,
                        canMore = discipline.limitMinutes < ScreenTimeRules.MAX_LIMIT_MINUTES,
                        lessDescription = lexString(R.string.onb_plan_less),
                        moreDescription = lexString(R.string.onb_plan_more),
                    )
                }
            } else if (plan.needsUsageAccess) {
                PlanSummaryCard(
                    title = lexString(R.string.onb_plan_limits_title),
                    value = lexString(R.string.onb_plan_limits_off),
                    note = lexString(R.string.onb_plan_needs_usage),
                ) {}
                SecondaryButton(
                    text = lexString(R.string.onb_plan_grant_usage),
                    onClick = { onEvent(OnboardingFlowEvent.OpenUsageSettings) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            plan.phoneDown?.let { night ->
                PlanSummaryCard(
                    title = lexString(R.string.onb_plan_night_title),
                    value = lexString(R.string.onb_plan_night_value, timeText(night.bedtimeMinute, locale), timeText(night.wakeMinute, locale)),
                    note = lexString(R.string.onb_plan_night_note),
                )
            }

            if (plan.suggestReelCounter) {
                PlanSummaryCard(
                    title = lexString(R.string.onb_plan_reel_title),
                    value = lexString(R.string.onb_plan_reel_value),
                    note = lexString(R.string.onb_plan_reel_note),
                )
            }

            if (WeightTargetRules.isOffered(state.answers.age, state.answers.heightCm, state.answers.weightKg)) {
                TargetCard(state, onEvent, editing == KEY_TARGET) { toggle(KEY_TARGET) }
            }
        }
    }
}

@Composable
private fun QuestsCard(build: PlanBuild) {
    val quests = (build as? PlanBuild.Ready)?.quests
    PlanSummaryCard(
        title = lexString(R.string.onb_plan_quests_title),
        value = when {
            quests != null -> lexPlural(R.plurals.onb_plan_quests_value, quests.size, quests.size)
            build == PlanBuild.Failed -> lexString(R.string.onb_plan_quests_unavailable)
            else -> lexString(R.string.onb_plan_quests_working)
        },
        note = quests?.joinToString("\n") { it.title },
    )
}

private fun timeText(minuteOfDay: Int, locale: java.util.Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE))

/** A weight the way the person entered it: whole pounds, or kilograms with a half only when needed. */
@Composable
private fun weightText(kg: Double, unit: MassUnit): String = when (unit) {
    MassUnit.KG -> "${formatNumber(kg)} ${lexString(R.string.onb_unit_kg)}"
    MassUnit.LB -> "${(kg / BodyMath.KG_PER_LB).roundToInt()} ${lexString(R.string.onb_unit_lb)}"
}

/**
 * The optional goal weight (docs/onboarding/PLAN.md Q3, GD §17 OB17-OB18): a target and a date from a few presets, and a
 * smooth estimate between today's weight and the target. Only offered to adults with a height and weight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetCard(state: OnboardingFlowState, onEvent: (OnboardingFlowEvent) -> Unit, open: Boolean, onToggle: () -> Unit) {
    val answers = state.answers
    val colors = AwakenTheme.colors
    val locale = LocalLocale.current.platformLocale
    val unit = answers.weightUnit
    val unitText = lexString(if (unit == MassUnit.KG) R.string.onb_unit_kg else R.string.onb_unit_lb)
    val heightCm = answers.heightCm ?: return
    val weightKg = answers.weightKg ?: return
    val target = answers.target
    val scale = BodyMath.weightScale(unit)
    val floorKg = WeightTargetRules.allowedRangeKg(heightCm).start
    // The ruler starts at the first mark at or above the lowest weight allowed for this height.
    val minTick = generateSequence(scale.tickOf(floorKg)) { it + 1 }.first { scale.valueAt(it) >= floorKg }
    var tick by rememberSaveable { mutableIntStateOf(scale.tickOf(target?.weightKg ?: weightKg).coerceIn(minTick, scale.last)) }
    var weeks by rememberSaveable { mutableIntStateOf(DEFAULT_TARGET_WEEKS) }
    val date = target?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() }
    val dateText = date?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(it) }

    PlanSummaryCard(
        title = lexString(R.string.onb_target_title),
        value = if (target == null) lexString(R.string.onb_target_optional) else weightText(target.weightKg, unit),
        note = if (target != null && dateText != null) lexString(R.string.onb_target_summary, weightText(weightKg, unit), weightText(target.weightKg, unit), dateText) else null,
        editLabel = lexString(if (open) R.string.onb_plan_done else if (target == null) R.string.onb_target_add else R.string.onb_plan_edit),
        onEditToggle = onToggle,
        expanded = open,
    ) {
        BigValue(value = BodyMath.weightTickLabel(unit, tick), unit = unitText)
        RulerPicker(
            first = minTick,
            last = scale.last,
            tick = tick,
            onTick = { tick = it },
            majorEvery = 10,
            valueText = { "${BodyMath.weightTickLabel(unit, it)} $unitText" },
            description = lexString(R.string.onb_target_ruler_cd),
            majorLabel = { if (unit == MassUnit.KG) (it / 2).toString() else it.toString() },
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        Text(lexString(R.string.onb_target_weeks_title), style = AwakenTheme.type.label, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            WeightTargetRules.WEEK_CHOICES.forEach { choice ->
                ChoiceChip(
                    text = lexPlural(R.plurals.onb_target_weeks, choice, choice),
                    selected = weeks == choice,
                    onClick = { weeks = choice },
                )
            }
        }
        PrimaryButton(
            text = lexString(R.string.onb_target_set),
            onClick = { onEvent(OnboardingFlowEvent.TargetChosen(scale.valueAt(tick), weeks)) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        if (target != null) {
            QuietButton(text = lexString(R.string.onb_target_remove), onClick = { onEvent(OnboardingFlowEvent.TargetCleared) }, modifier = Modifier.fillMaxWidth())
        }
    }
    // The estimate is drawn from the saved target, not from the ruler: it only changes when a target is set.
    if (target != null && dateText != null && target.weightKg != weightKg) {
        AwakenCard(modifier = Modifier.fillMaxWidth(), reveal = false) {
            Chart(
                ChartSpec.TrendLine(
                    values = WeightTargetRules.curve(weightKg, target.weightKg).map { it.toFloat() },
                    labels = listOf(lexString(R.string.onb_target_today), dateText),
                    description = lexString(R.string.onb_target_curve_cd, weightText(weightKg, unit), weightText(target.weightKg, unit), dateText),
                    color = colors.accent,
                ),
            )
            Text(lexString(R.string.onb_target_caption), style = AwakenTheme.type.caption, color = colors.textSecondary)
            Text(lexString(R.string.onb_target_note), style = AwakenTheme.type.caption, color = colors.textMuted)
        }
    }
}

private const val KEY_FOCUS = "focus"
private const val KEY_FOCUS_GOAL = "focusGoal"
private const val KEY_STEPS = "steps"
private const val KEY_DAYS = "days"
private const val KEY_LIMITS = "limits"
private const val KEY_TARGET = "target"
private const val DAYS_IN_WEEK = 7
private const val DEFAULT_TARGET_WEEKS = 8
private const val PERCENT = 100
private const val SECONDS_PER_MINUTE = 60L

// region previews

private val previewQuests = listOf(
    OnboardingQuestLine("Do 3 sets of push-ups", Stat.STR, Difficulty.NORMAL),
    OnboardingQuestLine("Walk 6,000 steps", Stat.VIT, Difficulty.EASY),
    OnboardingQuestLine("Read for 20 minutes", Stat.INT, Difficulty.EASY),
    OnboardingQuestLine("Put your phone away for an hour", Stat.WIL, Difficulty.NORMAL),
)

private val planAnswers = previewAnswers.copy(
    improve = listOf(ImproveGoal.SLEEP, ImproveGoal.FOCUS),
    obstacles = setOf(
        Obstacle.POOR_SLEEP,
        Obstacle.TOO_MUCH_SCROLLING,
    ),
    screenTime = ScreenTimeBand.H2_TO_4,
    fitness = FitnessLevel.INTERMEDIATE,
    trainingDays = setOf(1, 3, 5), trainingReminder = true, pace = Pace.BALANCED,
    bedtimeMinute = 23 * 60, wakeMinute = 6 * 60 + 30,
)

private fun planState(
    answers: OnboardingAnswers = planAnswers,
    permissions: OnboardingPermissionState = OnboardingPermissionState(usage = true),
    build: PlanBuild = PlanBuild.Ready(previewQuests),
    commitFailed: Boolean = false,
) = previewFlowState(OnboardingStep.PLAN, answers, permissions).copy(build = build, commitFailed = commitFailed)

/** The building screen part-way through: 62% and the line being set. Drawn without the timeline so it is the same every time. */
@PhonePreviews
@Composable
private fun OnboardingBuildingPreview() {
    AwakenTheme(reduceMotion = true) {
        ScreenBackground {
            OnboardingShell(
                stepKey = OnboardingStep.BUILDING.ordinal, forward = true, segments = OnboardingStep.SEGMENTS,
                progress = OnboardingStep.BUILDING.progress, progressDescription = "Step 20 of 21", primaryText = "", onPrimary = {},
                showPrimary = false,
            ) {
                BuildingContent(progress = 0.62f, line = "Setting your step goal: 8,000", failed = false, onRetry = {})
            }
        }
    }
}

@PhonePreviews
@Composable
private fun OnboardingBuildingFailedPreview() {
    AwakenTheme(reduceMotion = true) {
        ScreenBackground {
            OnboardingShell(
                stepKey = OnboardingStep.BUILDING.ordinal, forward = true, segments = OnboardingStep.SEGMENTS,
                progress = OnboardingStep.BUILDING.progress, progressDescription = "Step 20 of 21", primaryText = "", onPrimary = {},
                showPrimary = false,
            ) {
                BuildingContent(progress = 0.62f, line = "", failed = true, onRetry = {})
            }
        }
    }
}

@PhonePreviews
@Composable
private fun OnboardingPlanPreview() = PreviewFlow(planState())

/** No usage access: limits and phone-down nights are off, and the plan says why and offers the setting. */
@PhonePreviews
@Composable
private fun OnboardingPlanNeedsUsagePreview() = PreviewFlow(planState(permissions = OnboardingPermissionState(usage = false)))

/** Skipped almost everything: the plan is the defaults, with only the cards that always apply. */
@PhonePreviews
@Composable
private fun OnboardingPlanDefaultsPreview() =
    PreviewFlow(planState(answers = OnboardingAnswers(), build = PlanBuild.Running))

@PhonePreviews
@Composable
private fun OnboardingPlanTargetPreview() = PreviewFlow(
    planState(
        answers = planAnswers.copy(
            target = WeightTargetRules.target(66.0, 12, LocalDate.of(2026, 11, 3)),
        ),
    ),
)

@PhonePreviews
@Composable
private fun OnboardingPlanSaveFailedPreview() = PreviewFlow(planState(commitFailed = true))


// endregion
