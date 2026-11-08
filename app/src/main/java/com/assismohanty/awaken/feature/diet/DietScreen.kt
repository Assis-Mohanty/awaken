package com.assismohanty.awaken.feature.diet

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.Meal
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.components.symbolRes
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.DiaryDay
import com.assismohanty.awaken.core.domain.DiaryEntry
import com.assismohanty.awaken.core.domain.DietPlan
import com.assismohanty.awaken.core.domain.DietSetup
import com.assismohanty.awaken.engine.body.ActivityLevel
import com.assismohanty.awaken.engine.body.BodyProfile
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.NutritionMath
import com.assismohanty.awaken.engine.body.Nutrients
import com.assismohanty.awaken.engine.body.Sex
import com.assismohanty.awaken.engine.body.TargetKind
import com.assismohanty.awaken.engine.body.WeightGoal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DietRoute(modifier: Modifier = Modifier, viewModel: DietViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DietScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Diet (DESIGN_SYSTEM §4.12): the budget setup once, then the day's energy, meals and nutrients. */
@Composable
fun DietScreen(state: DietUiState, onEvent: (DietEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    LargeTitleScaffold(title = lexString(R.string.diet_title), modifier = modifier) {
        if (state.loading) {
            SkeletonCard()
            return@LargeTitleScaffold
        }
        val setup = state.setup
        val plan = state.plan
        when {
            setup != null -> SetupWindow(setup, canCancel = plan != null, onEvent = onEvent)
            plan != null -> Diary(state, plan, onEvent)
        }
        Text(lexString(R.string.diet_tracker_note), style = AwakenTheme.type.caption, color = colors.textMuted)
    }
    state.search?.let { FoodSearchSheet(it, onEvent) }
    state.amount?.let { AmountSheet(it, onEvent) }
    if (state.targets) state.plan?.let { TargetsSheet(it, onEvent) }
}

// Setup

@Composable
private fun SetupWindow(form: SetupForm, canCancel: Boolean, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val change: ((SetupForm) -> SetupForm) -> Unit = { onEvent(DietEvent.SetupChanged(it)) }
    SystemWindow(title = lexString(R.string.diet_window_setup), accent = colors.accent, reveal = false) {
        Text(lexString(R.string.diet_setup_intro), style = type.body, color = colors.textSecondary)
        Question(R.string.diet_setup_goal)
        WeightGoal.entries.forEach { goal ->
            ChoiceChip(text = lexString(goal.nameRes()), selected = form.goal == goal, onClick = { change { it.copy(goal = goal) } }, modifier = Modifier.fillMaxWidth())
        }
        Question(R.string.diet_setup_sex)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Sex.entries.forEach { sex ->
                ChoiceChip(text = lexString(sex.nameRes()), selected = form.sex == sex, onClick = { change { it.copy(sex = sex) } }, modifier = Modifier.weight(1f))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LabeledNumber(R.string.diet_setup_birth_year) {
                NumberField(
                    value = form.birthYear.toDouble(),
                    onValueChange = { change { f -> f.copy(birthYear = it.toInt()) } },
                    description = lexString(R.string.diet_setup_birth_year),
                    decimals = false,
                )
            }
            LabeledNumber(R.string.diet_setup_height) {
                NumberField(
                    value = form.heightCm,
                    onValueChange = { change { f -> f.copy(heightCm = it) } },
                    description = lexString(R.string.diet_setup_height),
                    suffix = lexString(R.string.diet_unit_cm),
                )
            }
            LabeledNumber(R.string.diet_setup_weight) {
                NumberField(
                    value = form.weightKg,
                    onValueChange = { change { f -> f.copy(weightKg = it) } },
                    description = lexString(R.string.diet_setup_weight),
                    suffix = lexString(R.string.diet_unit_kg),
                )
            }
        }
        Question(R.string.diet_setup_activity)
        ActivityLevel.entries.forEach { level ->
            ChoiceChip(text = lexString(level.nameRes()), selected = form.activity == level, onClick = { change { it.copy(activity = level) } }, modifier = Modifier.fillMaxWidth())
        }
        val preview = form.preview
        if (preview == null) {
            Text(lexString(R.string.diet_setup_incomplete), style = type.caption, color = colors.textSecondary)
        } else {
            Text(lexString(R.string.diet_setup_budget, formatCount(preview.kcal)), style = type.numeric, color = colors.textPrimary)
            Text(lexString(R.string.diet_setup_preview, formatCount(preview.bmr), formatCount(preview.tdee)), style = type.caption, color = colors.textSecondary)
            Text(
                lexString(
                    R.string.diet_setup_macros,
                    formatAmount(preview.targets[Nutrient.PROTEIN]?.amount ?: 0.0),
                    formatAmount(preview.targets[Nutrient.CARBS]?.amount ?: 0.0),
                    formatAmount(preview.targets[Nutrient.FAT]?.amount ?: 0.0),
                ),
                style = type.caption,
                color = colors.textSecondary,
            )
        }
        PrimaryButton(
            text = lexString(R.string.diet_setup_save),
            onClick = { onEvent(DietEvent.SaveProfile) },
            enabled = preview != null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (canCancel) QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(DietEvent.CancelProfile) })
    }
}

@Composable
private fun Question(@StringRes text: Int) {
    Text(lexString(text), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary)
}

@Composable
private fun LabeledNumber(@StringRes label: Int, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(lexString(label), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
        field()
    }
}

// Diary

@Composable
private fun Diary(state: DietUiState, plan: DietPlan, onEvent: (DietEvent) -> Unit) {
    val day = state.day ?: DiaryDay(state.date, emptyMap(), Nutrients())
    DateStepper(state.date, state.isToday, onEvent)
    BudgetWindow(plan, day.totals, Modifier.staggeredEntrance(0))
    Meal.entries.forEachIndexed { i, meal ->
        MealWindow(meal, day.meals[meal].orEmpty(), onEvent, Modifier.staggeredEntrance(1 + i))
    }
    NutrientsWindow(plan, day.totals)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuietButton(text = lexString(R.string.diet_edit_targets), onClick = { onEvent(DietEvent.OpenTargets) })
        QuietButton(text = lexString(R.string.diet_edit_profile), onClick = { onEvent(DietEvent.EditProfile) })
    }
}

@Composable
private fun DateStepper(date: LocalDate, isToday: Boolean, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onEvent(DietEvent.PreviousDay) }) {
            Icon(AwakenGlyphs.ChevronLeft, contentDescription = lexString(R.string.cd_diet_previous_day), tint = colors.textPrimary)
        }
        Text(
            if (isToday) lexString(R.string.diet_today) else formatDate(date),
            style = AwakenTheme.type.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !isToday) { onEvent(DietEvent.Today) },
        )
        IconButton(onClick = { onEvent(DietEvent.NextDay) }, enabled = !isToday) {
            Icon(AwakenGlyphs.ChevronRight, contentDescription = lexString(R.string.cd_diet_next_day), tint = if (isToday) colors.textMuted else colors.textPrimary)
        }
    }
}

private val MACROS = listOf(Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT, Nutrient.FIBER)

@Composable
private fun BudgetWindow(plan: DietPlan, totals: Nutrients, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val goal = plan.plan.kcal
    val eaten = totals[Nutrient.KCAL].roundToInt()
    val remaining = goal - eaten
    SystemWindow(title = lexString(R.string.diet_window_budget), accent = colors.accent, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Chart(
                ChartSpec.Ring(
                    progress = if (goal > 0) eaten.toFloat() / goal else 0f,
                    description = lexString(R.string.cd_diet_ring, formatCount(eaten), formatCount(goal)),
                    color = if (remaining < 0) colors.critical else colors.positive,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatCount(abs(remaining)), style = type.numeric, color = if (remaining < 0) colors.critical else colors.textPrimary)
                    Text(
                        lexString(if (remaining < 0) R.string.diet_kcal_over else R.string.diet_kcal_remaining),
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Figure(lexString(R.string.diet_eaten), formatCount(eaten))
                Figure(lexString(R.string.diet_goal), formatCount(goal))
            }
        }
        MACROS.forEach { nutrient -> NutrientLine(nutrient, totals[nutrient], plan.plan.targets[nutrient]?.amount, TargetKind.GOAL) }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(value, style = AwakenTheme.type.numericSmall, color = AwakenTheme.colors.textPrimary)
        Text(label, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

/** "Protein   72 / 128 g" with a bar; a limit past its target turns ember. */
@Composable
private fun NutrientLine(nutrient: Nutrient, amount: Double, target: Double?, kind: TargetKind) {
    val colors = AwakenTheme.colors
    val unit = lexString(nutrient.unit.symbolRes())
    val over = kind == TargetKind.LIMIT && target != null && amount > target
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lexString(nutrient.nameRes()), style = AwakenTheme.type.label, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (over) Text(lexString(R.string.diet_limit_over) + "  ", style = AwakenTheme.type.caption, color = colors.critical)
            Text(
                if (target != null) {
                    lexString(R.string.diet_amount_of_target, formatAmount(amount), formatAmount(target), unit)
                } else {
                    lexString(R.string.diet_amount_only, formatAmount(amount), unit)
                },
                style = AwakenTheme.type.caption,
                color = if (over) colors.critical else colors.textSecondary,
            )
        }
        if (target != null && target > 0.0) {
            Chart(ChartSpec.Progress(progress = (amount / target).toFloat(), color = if (over) colors.critical else colors.accent))
        }
    }
}

@Composable
private fun MealWindow(meal: Meal, entries: List<DiaryEntry>, onEvent: (DietEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val kcal = entries.sumOf { it.amounts[Nutrient.KCAL] }.roundToInt()
    SystemWindow(
        title = lexString(meal.nameRes()),
        accent = colors.accent,
        modifier = modifier,
        reveal = false,
        headerTrailing = { Text(lexString(R.string.diet_meal_kcal, formatCount(kcal)), style = AwakenTheme.type.label, color = colors.textSecondary) },
    ) {
        if (entries.isEmpty()) Text(lexString(R.string.diet_meal_empty), style = AwakenTheme.type.caption, color = colors.textMuted)
        entries.forEachIndexed { i, entry ->
            if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
            EntryRow(entry.row, entry.amounts, onEvent)
        }
        QuietButton(text = lexString(R.string.diet_add_food), onClick = { onEvent(DietEvent.OpenSearch(meal)) })
    }
}

@Composable
private fun EntryRow(row: FoodLogEntity, amounts: Nutrients, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val amount = row.portionLabel ?: lexString(R.string.diet_entry_grams, formatAmount(row.grams))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = lexString(R.string.diet_entry_edit, row.name)) { onEvent(DietEvent.EditEntry(row)) }
            .padding(vertical = Spacing.xs),
    ) {
        Text(row.name, style = AwakenTheme.type.label, color = colors.textPrimary, maxLines = 2)
        Text(
            lexString(R.string.diet_entry_meta, amount, formatCount(amounts[Nutrient.KCAL].roundToInt())),
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
        )
    }
}

/** Nutrients that have a target, plus any without one that the day actually contains. */
@Composable
private fun NutrientsWindow(plan: DietPlan, totals: Nutrients) {
    val colors = AwakenTheme.colors
    val shown = Nutrient.entries.filter { it != Nutrient.KCAL && it !in MACROS && (it in plan.plan.targets || totals[it] > 0.0) }
    SystemWindow(title = lexString(R.string.diet_window_nutrients), accent = colors.accent, reveal = false) {
        shown.forEach { nutrient ->
            val target = plan.plan.targets[nutrient]
            NutrientLine(nutrient, totals[nutrient], target?.amount, target?.kind ?: NutritionMath.kindOf(nutrient))
        }
    }
}

// Labels and formatting

@StringRes
internal fun Meal.nameRes(): Int = when (this) {
    Meal.BREAKFAST -> R.string.diet_meal_breakfast
    Meal.LUNCH -> R.string.diet_meal_lunch
    Meal.DINNER -> R.string.diet_meal_dinner
    Meal.SNACK -> R.string.diet_meal_snack
}

@StringRes
private fun WeightGoal.nameRes(): Int = when (this) {
    WeightGoal.LOSE -> R.string.diet_goal_lose
    WeightGoal.MAINTAIN -> R.string.diet_goal_maintain
    WeightGoal.GAIN -> R.string.diet_goal_gain
}

@StringRes
private fun Sex.nameRes(): Int = when (this) {
    Sex.MALE -> R.string.diet_sex_male
    Sex.FEMALE -> R.string.diet_sex_female
}

@StringRes
private fun ActivityLevel.nameRes(): Int = when (this) {
    ActivityLevel.SEDENTARY -> R.string.diet_activity_sedentary
    ActivityLevel.LIGHT -> R.string.diet_activity_light
    ActivityLevel.MODERATE -> R.string.diet_activity_moderate
    ActivityLevel.VERY -> R.string.diet_activity_very
    ActivityLevel.EXTRA -> R.string.diet_activity_extra
}

/** Whole numbers from 10 up, one decimal below (2.4 g iron, 128 g protein). */
internal fun formatAmount(value: Double): String =
    if (abs(value) >= AMOUNT_DECIMALS_BELOW) NumberFormat.getIntegerInstance(Locale.getDefault()).format(value.roundToInt()) else String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(date)

private const val AMOUNT_DECIMALS_BELOW = 10.0

// Previews

internal fun previewDietState(setup: Boolean = false): DietUiState {
    val dietSetup = DietSetup(Sex.MALE, 1998, 175.0, 80.0, ActivityLevel.MODERATE, WeightGoal.MAINTAIN)
    val date = LocalDate.of(2026, 10, 27)
    val body = BodyProfile(dietSetup.sex, age = 28, dietSetup.heightCm, dietSetup.weightKg, dietSetup.activity, dietSetup.goal)
    val plan = DietPlan(dietSetup, emptyMap(), NutritionMath.plan(body))
    fun row(id: String, meal: Meal, name: String, grams: Double, label: String?, per100: Nutrients) = DiaryEntry(
        FoodLogEntity(id, date, meal, "usda:$id", name, grams, label, "{}", Instant.EPOCH),
        per100.forGrams(grams),
    )
    val paneer = Nutrients(mapOf(Nutrient.KCAL to 299.0, Nutrient.PROTEIN to 15.9, Nutrient.CARBS to 22.5, Nutrient.FAT to 15.5, Nutrient.SODIUM to 185.0, Nutrient.CALCIUM to 597.0))
    val roti = Nutrients(mapOf(Nutrient.KCAL to 297.0, Nutrient.PROTEIN to 11.3, Nutrient.CARBS to 46.4, Nutrient.FAT to 7.5, Nutrient.FIBER to 4.9, Nutrient.SODIUM to 409.0))
    val idli = Nutrients(mapOf(Nutrient.KCAL to 128.0, Nutrient.PROTEIN to 6.4, Nutrient.CARBS to 25.0, Nutrient.FIBER to 5.8, Nutrient.SODIUM to 252.0))
    val entries = listOf(
        row("1", Meal.BREAKFAST, "Idli", 76.0, "2 × 1 item", idli),
        row("2", Meal.LUNCH, "Cheese, paneer", 150.0, null, paneer),
        row("3", Meal.LUNCH, "Bread, chapati or roti, plain, commercially prepared", 136.0, "2 × 1 piece", roti),
    )
    return DietUiState(
        loading = false,
        date = date,
        isToday = true,
        plan = plan,
        day = DiaryDay(date, entries.groupBy { it.row.meal }, Nutrients.sum(entries.map { it.amounts })),
        setup = if (setup) SetupForm(goal = WeightGoal.LOSE, sex = Sex.FEMALE, birthYear = 1999, heightCm = 162.0, weightKg = 64.0, activity = ActivityLevel.LIGHT) else null,
    )
}

@PhonePreviews
@Composable
private fun DietDiaryPreview() {
    AwakenTheme(reduceMotion = true) { DietScreen(previewDietState(), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun DietSetupPreview() {
    AwakenTheme(reduceMotion = true) { DietScreen(previewDietState(setup = true), onEvent = {}) }
}

