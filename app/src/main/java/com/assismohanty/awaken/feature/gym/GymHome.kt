package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SectionHeader
import com.assismohanty.awaken.core.designsystem.components.WeekStrip
import com.assismohanty.awaken.core.designsystem.components.WeekStripDay
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.RoutineDetail
import com.assismohanty.awaken.core.domain.RoutineExerciseDetail
import com.assismohanty.awaken.core.domain.WorkoutSummary
import com.assismohanty.awaken.engine.body.DayPlan
import com.assismohanty.awaken.engine.body.Plan
import java.time.DayOfWeek
import java.time.Duration
import java.time.format.TextStyle

// Gym home (GD §18.1): the week, the selected day, plans and My routines.

@Composable
internal fun GymWeekSection(home: GymHomeUi, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val days = home.week.map { d ->
        val full = d.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val rest = d.plan == DayPlan.Rest
        val description = buildList {
            add(lexString(R.string.gym_day_cd, full, d.date.dayOfMonth.toString()))
            if (d.today) add(lexString(R.string.gym_day_today))
            add(lexString(if (rest) R.string.gym_day_rest else R.string.gym_day_workout))
            if (d.finished) add(lexString(R.string.gym_day_done))
        }.joinToString(", ")
        WeekStripDay(
            date = d.date, label = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), number = d.date.dayOfMonth.toString(),
            today = d.today, selected = d.date == home.selected, done = d.finished, rest = rest, description = description,
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        WeekStrip(days = days, onSelect = { onEvent(GymEvent.SelectDay(it)) })
        DayCard(home, onEvent)
    }
}

/** What the selected day holds: finished workouts, then the routine or a rest day (or an empty week). */
@Composable
private fun DayCard(home: GymHomeUi, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val day = home.week.first { it.date == home.selected }
    home.selectedFinished.forEach { workout ->
        AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
            Text(workout.name.ifBlank { lexString(R.string.gym_workout_default_name) }, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
            Text(
                lexString(R.string.gym_history_meta, formatDate(workout.gameDate), workout.duration.toMinutes().toInt(), lexPlural(R.plurals.gym_sets_count, workout.sets, workout.sets), formatKg(workout.volumeKg)),
                style = AwakenTheme.type.caption,
                color = colors.textSecondary,
            )
        }
    }
    val routine = home.selectedRoutine
    val today = home.week.first { it.today }.date
    when {
        routine != null -> WorkoutDayCard(routine, canStart = day.today && !day.finished, onEvent = onEvent, canSwap = home.canSwap)
        home.routines.isEmpty() && home.selectedFinished.isEmpty() -> AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
            Text(lexString(R.string.gym_week_empty_title), style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
            Text(lexString(R.string.gym_week_empty_body), style = AwakenTheme.type.body, color = colors.textSecondary)
            PrimaryButton(text = lexString(R.string.gym_new_routine), onClick = { onEvent(GymEvent.NewRoutine) }, modifier = Modifier.fillMaxWidth())
        }
        home.selectedFinished.isEmpty() -> RestDayCard(home.canSwap, past = day.date.isBefore(today), onEvent = onEvent)
    }
}

@Composable
private fun RestDayCard(canSwap: Boolean, past: Boolean, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
        if (past) {
            Text(lexString(R.string.gym_day_past_none), style = AwakenTheme.type.body, color = colors.textSecondary)
        } else {
            Text(lexString(R.string.gym_rest_day_title), style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
            Text(lexString(R.string.gym_rest_day_body), style = AwakenTheme.type.body, color = colors.textSecondary)
            if (canSwap) {
                SecondaryButton(text = lexString(R.string.gym_day_swap), onClick = { onEvent(GymEvent.SwapWithToday) }, modifier = Modifier.fillMaxWidth())
                Text(lexString(R.string.gym_day_swap_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun WorkoutDayCard(routine: RoutineDetail, canStart: Boolean, canSwap: Boolean, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
        Text(routine.name, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
        Text(routineMeta(routine), style = AwakenTheme.type.caption, color = colors.textSecondary)
        if (routine.warmup.isNotEmpty()) ExerciseSection(R.string.gym_section_warmup, routine.warmup, routine, startOpen = false)
        if (routine.work.isNotEmpty()) ExerciseSection(R.string.gym_section_workout, routine.work, routine, startOpen = true)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(text = lexString(R.string.gym_edit), onClick = { onEvent(GymEvent.OpenEditor(routine.id)) }, modifier = Modifier.weight(1f))
            if (canStart) {
                PrimaryButton(text = lexString(R.string.gym_start), onClick = { onEvent(GymEvent.StartRoutine(routine.id, routine.name)) }, modifier = Modifier.weight(1f))
            }
        }
        if (canSwap) QuietButton(text = lexString(R.string.gym_day_swap), onClick = { onEvent(GymEvent.SwapWithToday) })
    }
}

/** A collapsible Warm-up or Workout list with its duration on the right. */
@Composable
private fun ExerciseSection(titleRes: Int, items: List<RoutineExerciseDetail>, routine: RoutineDetail, startOpen: Boolean) {
    val colors = AwakenTheme.colors
    var open by rememberSaveable(routine.id, titleRes) { mutableStateOf(startOpen) }
    val title = lexString(titleRes)
    val toggle = lexString(if (open) R.string.gym_section_hide else R.string.gym_section_show, title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
            .clickable { open = !open }
            .semantics { contentDescription = toggle },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(title, style = AwakenTheme.type.label, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(lexString(R.string.gym_section_minutes, routine.minutes(items)), style = AwakenTheme.type.caption, color = colors.textSecondary)
        Icon(
            if (open) AwakenGlyphs.ChevronDown else AwakenGlyphs.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(AwakenTheme.dimens.iconSmall),
        )
    }
    if (open) {
        items.forEachIndexed { i, e ->
            if (e.superset != null && items.getOrNull(i - 1)?.superset != e.superset) {
                Text(lexString(R.string.gym_superset_label), style = AwakenTheme.type.caption, color = colors.textSecondary)
            }
            ListRow(title = e.name, secondary = targetText(e))
        }
    }
}

@Composable
internal fun routineMeta(routine: RoutineDetail): String = lexString(
    R.string.gym_routine_meta_full,
    lexPlural(R.plurals.gym_exercises_count, routine.work.size, routine.work.size),
    lexPlural(R.plurals.gym_sets_count, routine.workSets, routine.workSets),
    routine.minutes(),
)

/** "3 × 8–10 · rest 1:30" for one routine exercise. */
@Composable
internal fun targetText(e: RoutineExerciseDetail): String {
    val min = e.repMin
    val max = e.repMax
    val work = when {
        min == null -> lexPlural(R.plurals.gym_sets_count, e.sets, e.sets)
        e.timed -> lexString(R.string.gym_target_time, e.sets, min, max ?: min)
        max == null || max == min -> lexString(R.string.gym_target_reps_single, e.sets, min)
        else -> lexString(R.string.gym_target_reps, e.sets, min, max)
    }
    val rest = e.restSec?.let { lexString(R.string.gym_target_rest, formatElapsed(Duration.ofSeconds(it.toLong()))) }
    val sides = if (e.unilateral) lexString(R.string.gym_unilateral_label) else null
    return listOfNotNull(work, rest, sides).joinToString(" · ")
}

/** The same line for an exercise of a plan, before it becomes a routine. */
@Composable
internal fun planTargetText(item: com.assismohanty.awaken.engine.body.PlanExercise): String {
    val range = if (item.timed) item.seconds else item.reps
    val work = when {
        range.size < 2 -> lexPlural(R.plurals.gym_sets_count, item.sets, item.sets)
        item.timed -> lexString(R.string.gym_target_time, item.sets, range[0], range[1])
        range[0] == range[1] -> lexString(R.string.gym_target_reps_single, item.sets, range[0])
        else -> lexString(R.string.gym_target_reps, item.sets, range[0], range[1])
    }
    return listOf(work, lexString(R.string.gym_target_rest, formatElapsed(Duration.ofSeconds(item.restSec.toLong())))).joinToString(" · ")
}

@Composable
internal fun weekdayShort(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT, currentLocale())

@Composable
private fun levelName(plan: Plan): String = lexString(
    when (plan.level) {
        "beginner" -> R.string.gym_plan_level_beginner
        "intermediate" -> R.string.gym_plan_level_intermediate
        "advanced" -> R.string.gym_plan_level_advanced
        else -> R.string.gym_plan_level_any
    },
)

// Plans

@Composable
internal fun PlansSection(home: GymHomeUi, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(title = lexString(R.string.gym_plans_title))
        Text(lexString(R.string.gym_plans_note), style = AwakenTheme.type.caption, color = colors.textSecondary)
        if (!home.answers.gearKnown) {
            Text(lexString(R.string.gym_plans_gear_ask), style = AwakenTheme.type.body, color = colors.textSecondary)
            QuietButton(text = lexString(R.string.gym_settings_open), onClick = { onEvent(GymEvent.OpenGymSettings) })
        }
        if (home.plans.isEmpty()) {
            if (home.plansLoaded) Text(lexString(R.string.gym_plans_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                home.plans.forEach { fit ->
                    val open = lexString(R.string.gym_plan_open_cd, fit.plan.name)
                    AwakenCard(
                        modifier = Modifier.width(Spacing.xxl * 5).semantics { contentDescription = open },
                        reveal = false,
                        onClick = { onEvent(GymEvent.OpenPlan(fit.plan.id)) },
                    ) {
                        Text(fit.plan.name, style = AwakenTheme.type.windowTitle, color = colors.textPrimary)
                        Text(
                            lexString(R.string.gym_plan_meta, lexPlural(R.plurals.gym_plan_days, fit.plan.dayCount, fit.plan.dayCount), levelName(fit.plan)),
                            style = AwakenTheme.type.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

// My routines

@Composable
internal fun MyRoutinesSection(home: GymHomeUi, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(title = lexString(R.string.gym_routines_title))
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            if (home.routines.isEmpty()) Text(lexString(R.string.gym_routines_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
            home.routines.forEach { routine ->
                val days = if (routine.weekdays.isEmpty()) lexString(R.string.gym_routine_none) else routine.weekdays.sorted().map { weekdayShort(it) }.joinToString(", ")
                val menu = lexString(R.string.gym_routine_menu_cd, routine.name)
                ListRow(
                    title = routine.name,
                    secondary = lexString(R.string.gym_plan_meta, days, lexPlural(R.plurals.gym_exercises_count, routine.work.size, routine.work.size)),
                    onClick = { onEvent(GymEvent.OpenEditor(routine.id)) },
                    trailing = {
                        SecondaryButton(text = lexString(R.string.gym_start), onClick = { onEvent(GymEvent.StartRoutine(routine.id, routine.name)) })
                        IconButton(onClick = { onEvent(GymEvent.OpenRoutineMenu(routine.id)) }, modifier = Modifier.size(AwakenTheme.dimens.touchTarget)) {
                            Icon(AwakenGlyphs.More, contentDescription = menu, tint = colors.textSecondary)
                        }
                    },
                )
            }
            ListRow(
                title = lexString(R.string.gym_new_routine),
                icon = AwakenGlyphs.Plus,
                onClick = { onEvent(GymEvent.NewRoutine) },
            )
        }
        QuietButton(text = lexString(R.string.gym_start_empty), onClick = { onEvent(GymEvent.StartEmpty) }, modifier = Modifier.padding(top = Spacing.xxs))
    }
}

@Composable
internal fun formatKg(kg: Double): String = formatCount(Math.round(kg))

// Previews

internal fun previewAnswers() = com.assismohanty.awaken.core.domain.GymAnswers(
    fitness = com.assismohanty.awaken.engine.onboarding.FitnessLevel.INTERMEDIATE,
    goal = "muscle",
    gear = setOf(
        com.assismohanty.awaken.engine.body.Gear.BARBELL, com.assismohanty.awaken.engine.body.Gear.BENCH,
        com.assismohanty.awaken.engine.body.Gear.DUMBBELLS, com.assismohanty.awaken.engine.body.Gear.CABLES,
    ),
    gearKnown = true,
    injuries = setOf(com.assismohanty.awaken.engine.onboarding.InjuryRegion.KNEE),
    sex = "M",
    bodyWeightKg = 78.0,
    trainingDays = setOf(1, 2, 4, 6),
)

private fun previewRoutine(id: String, name: String, weekdays: Set<Int>) = RoutineDetail(
    id = id,
    name = name,
    exercises = listOf(
        RoutineExerciseDetail(0, "w0", "Push-up", 2, 10, 10, 30, null, false, warmup = true, timed = false),
        RoutineExerciseDetail(1, "e0", "Bench press", 3, 6, 8, 150, null, false, warmup = false, timed = false),
        RoutineExerciseDetail(2, "e1", "Incline dumbbell press", 3, 8, 10, 120, null, false, warmup = false, timed = false),
        RoutineExerciseDetail(3, "e2", "Triceps pushdown", 3, 10, 12, 75, "A", false, warmup = false, timed = false),
        RoutineExerciseDetail(4, "e3", "Skullcrusher", 3, 10, 12, 75, "A", false, warmup = false, timed = false),
        RoutineExerciseDetail(5, "e4", "Plank", 3, 30, 60, 45, null, false, warmup = false, timed = true),
    ),
    weekdays = weekdays,
    sourcePlanId = null,
)

private fun previewPlan(id: String, name: String, days: Int, level: String) = com.assismohanty.awaken.engine.body.PlanFit(
    plan = Plan(
        id = id, name = name, level = level, goal = listOf("muscle"), equipment = listOf("dumbbells"),
        days = List(days) { i ->
            com.assismohanty.awaken.engine.body.PlanDay(
                weekday = if (days == 1) null else i + 1, name = "Day ${i + 1}",
                work = listOf(com.assismohanty.awaken.engine.body.PlanExercise("e0", 3, reps = listOf(8, 10), restSec = 90)),
            )
        },
    ),
    days = List(days) { i ->
        com.assismohanty.awaken.engine.body.PlanDay(
            weekday = if (days == 1) null else i + 1, name = "Day ${i + 1}",
            work = listOf(
                com.assismohanty.awaken.engine.body.PlanExercise("e0", 3, reps = listOf(8, 10), restSec = 90),
                com.assismohanty.awaken.engine.body.PlanExercise("e1", 3, seconds = listOf(30, 60), restSec = 45),
            ),
        )
    },
    swapped = 1,
    dropped = 0,
)

/** The Gym home for previews: Tuesday is today; [restDay] selects Wednesday; [empty] has no routines and a bare week. */
internal fun previewHome(restDay: Boolean = false, empty: Boolean = false, finishedMonday: Boolean = true): GymHomeUi {
    val monday = java.time.LocalDate.of(2026, 10, 26)
    val push = previewRoutine("r1", "Push day", setOf(2, 6))
    val legs = previewRoutine("r2", "Legs", setOf(4))
    val slots = mapOf(1 to "r1", 2 to "r1", 4 to "r2", 6 to "r1")
    val week = (0L..6L).map { i ->
        val date = monday.plusDays(i)
        val plan = if (empty) DayPlan.Rest else slots[date.dayOfWeek.value]?.let { DayPlan.Workout(it) } ?: DayPlan.Rest
        com.assismohanty.awaken.core.domain.WeekDay(date, plan, finished = finishedMonday && i == 0L, today = i == 1L)
    }
    val selected = if (restDay) monday.plusDays(2) else monday.plusDays(1)
    val routines = if (empty) emptyList() else listOf(push, legs)
    return GymHomeUi(
        week = week,
        selected = selected,
        selectedRoutine = if (restDay || empty) null else push,
        canSwap = restDay,
        routines = routines,
        plans = if (empty) emptyList() else listOf(previewPlan("p1", "Push / Pull / Legs", 6, "intermediate"), previewPlan("p2", "Chest and triceps", 1, "any"), previewPlan("p3", "Full body, 3 days", 3, "beginner")),
        answers = previewAnswers(),
        names = mapOf("e0" to "Bench press", "e1" to "Plank"),
    )
}

@com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
@Composable
private fun GymHomeRestDayPreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(previewGymState(home = previewHome(restDay = true)), onEvent = {}) }
}

@com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
@Composable
private fun GymHomeEmptyWeekPreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(previewGymState(home = previewHome(empty = true)), onEvent = {}) }
}

@com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
@Composable
private fun GymPlanDetailPreview() {
    val home = previewHome()
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md)) { PlanDetailContent(PlanDetailUi(home.plans[1], setOf(1, 3), home.names), {}) }
    }
}

@com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
@Composable
private fun GymRoutineEditorPreview() {
    val home = previewHome()
    AwakenTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(Spacing.md)) { RoutineEditorContent(home.routines.first(), {}) }
    }
}
