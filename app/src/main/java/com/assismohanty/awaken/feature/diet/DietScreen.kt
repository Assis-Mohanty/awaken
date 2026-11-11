package com.assismohanty.awaken.feature.diet

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import com.assismohanty.awaken.core.domain.FoodLibrary
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.HeaderIconButton
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.Meal
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
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
import com.assismohanty.awaken.core.domain.MealSlot
import com.assismohanty.awaken.engine.body.ActivityLevel
import com.assismohanty.awaken.engine.body.BodyProfile
import com.assismohanty.awaken.engine.body.Hydration
import com.assismohanty.awaken.engine.body.LossPace
import com.assismohanty.awaken.core.domain.TrendOffer
import com.assismohanty.awaken.engine.body.MacroPreset
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
    LargeTitleScaffold(
        title = lexString(R.string.diet_title),
        modifier = modifier,
        actions = {
            if (state.setup == null && state.plan?.targetsVisible == true) {
                HeaderIconButton(AwakenGlyphs.Settings, lexString(R.string.cd_diet_targets), { onEvent(DietEvent.OpenTargets) })
            }
        },
    ) {
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
    state.search?.let { FoodSearchSheet(it, state.slots, state.savedMeals, onEvent) }
    state.prompt?.let { PromptSheet(it, state.note, onEvent) }
    state.amount?.let { AmountSheet(it, state.slots, onEvent) }
    if (state.targets) state.plan?.let { TargetsSheet(it, onEvent) }
    when (state.panel) {
        Panel.WEIGHT -> WeightSheet(state.weights, state.trendKg, onEvent)
        Panel.NUTRIENTS -> state.plan?.let { NutrientsSheet(it, state.day?.totals ?: Nutrients(), state.coverage, onEvent) }
        Panel.REPORT -> ReportSheet(state.report, state.plan, onEvent)
        Panel.FASTING -> FastingSheet(state.fast, onEvent)
        Panel.WATER -> state.plan?.let { WaterTargetSheet(it.waterTargetMl, onEvent) }
        null -> Unit
    }
}

// Setup

@Composable
private fun SetupWindow(form: SetupForm, canCancel: Boolean, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val change: ((SetupForm) -> SetupForm) -> Unit = { onEvent(DietEvent.SetupChanged(it)) }
    SystemWindow(title = lexString(R.string.diet_window_setup), accent = colors.accent, reveal = false) {
        Text(lexString(R.string.diet_setup_step, form.step), style = type.caption, color = colors.textSecondary)
        when (form.step) {
            1 -> AboutYouStep(form, change)
            2 -> GoalStep(form, change)
            else -> NumbersStep(form, change)
        }
        Text(lexString(R.string.diet_estimate_disclaimer), style = type.caption, color = colors.textMuted)
        val ready = when (form.step) {
            1 -> form.sex != null && form.activity != null && form.birthYear > 0 && form.heightCm > 0.0 && form.weightKg > 0.0
            2 -> form.goal != null
            else -> form.preview != null
        }
        if (form.step < LAST_SETUP_STEP) {
            PrimaryButton(text = lexString(R.string.diet_setup_next), onClick = { onEvent(DietEvent.SetupNext) }, enabled = ready, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(text = lexString(R.string.diet_setup_save), onClick = { onEvent(DietEvent.SaveProfile) }, enabled = ready, modifier = Modifier.fillMaxWidth())
        }
        if (form.step > 1) QuietButton(text = lexString(R.string.diet_setup_back), onClick = { onEvent(DietEvent.SetupBack) })
        if (canCancel) QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(DietEvent.CancelProfile) })
    }
}

private const val LAST_SETUP_STEP = 3

@Composable
private fun AboutYouStep(form: SetupForm, change: ((SetupForm) -> SetupForm) -> Unit) {
    Text(lexString(R.string.diet_setup_intro), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
    Question(R.string.diet_setup_sex)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Sex.entries.forEach { sex ->
            ChoiceChip(text = lexString(sex.nameRes()), selected = form.sex == sex, onClick = { change { it.copy(sex = sex) } }, modifier = Modifier.weight(1f))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LabeledNumber(R.string.diet_setup_birth_year) {
            NumberField(value = form.birthYear.toDouble(), onValueChange = { change { f -> f.copy(birthYear = it.toInt()) } }, description = lexString(R.string.diet_setup_birth_year), decimals = false)
        }
        LabeledNumber(R.string.diet_setup_height) {
            NumberField(value = form.heightCm, onValueChange = { change { f -> f.copy(heightCm = it) } }, description = lexString(R.string.diet_setup_height), suffix = lexString(R.string.diet_unit_cm))
        }
        LabeledNumber(R.string.diet_setup_weight) {
            NumberField(value = form.weightKg, onValueChange = { change { f -> f.copy(weightKg = it) } }, description = lexString(R.string.diet_setup_weight), suffix = lexString(R.string.diet_unit_kg))
        }
    }
    Question(R.string.diet_setup_activity)
    ActivityLevel.entries.forEach { level ->
        ChoiceChip(text = lexString(level.nameRes()), selected = form.activity == level, onClick = { change { it.copy(activity = level) } }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GoalStep(form: SetupForm, change: ((SetupForm) -> SetupForm) -> Unit) {
    Question(R.string.diet_setup_goal)
    WeightGoal.entries.forEach { goal ->
        ChoiceChip(text = lexString(goal.nameRes()), selected = form.goal == goal, onClick = { change { it.copy(goal = goal) } }, modifier = Modifier.fillMaxWidth())
    }
    if (form.goal == WeightGoal.LOSE) {
        Question(R.string.diet_setup_pace)
        LossPace.entries.forEach { pace ->
            ChoiceChip(text = lexString(pace.nameRes()), selected = form.lossPace == pace, onClick = { change { it.copy(lossPace = pace) } }, modifier = Modifier.fillMaxWidth())
        }
        Text(lexString(R.string.diet_pace_note), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

@Composable
private fun NumbersStep(form: SetupForm, change: ((SetupForm) -> SetupForm) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val preview = form.preview
    if (preview == null) {
        Text(lexString(if (form.preset == MacroPreset.CUSTOM && form.customSplit == null) R.string.diet_split_not_100 else R.string.diet_setup_incomplete), style = type.caption, color = colors.textSecondary)
    } else {
        val plan = preview.plan
        Text(lexString(R.string.diet_setup_budget, formatCount(plan.kcal)), style = type.numeric, color = colors.textPrimary)
        Text(lexString(R.string.diet_setup_preview, formatCount(plan.bmr), formatCount(plan.tdee)), style = type.caption, color = colors.textSecondary)
        Text(
            lexString(
                R.string.diet_setup_macros,
                formatAmount(plan.targets[Nutrient.PROTEIN]?.amount ?: 0.0),
                formatAmount(plan.targets[Nutrient.CARBS]?.amount ?: 0.0),
                formatAmount(plan.targets[Nutrient.FAT]?.amount ?: 0.0),
            ),
            style = type.caption,
            color = colors.textSecondary,
        )
        if (preview.atFloor) Text(lexString(R.string.diet_floor_note), style = type.caption, color = colors.textPrimary)
        if (!preview.splitFits) Text(lexString(R.string.diet_split_no_fit), style = type.caption, color = colors.textPrimary)
    }
    Question(R.string.diet_split_title)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MacroPreset.entries.forEach { preset ->
            ChoiceChip(text = lexString(preset.nameRes()), selected = form.preset == preset, onClick = { change { it.copy(preset = preset) } }, modifier = Modifier.weight(1f))
        }
    }
    if (form.preset == MacroPreset.CUSTOM) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LabeledNumber(R.string.diet_split_protein_pct) {
                NumberField(value = form.proteinPct.toDouble(), onValueChange = { change { f -> f.copy(proteinPct = it.toInt()) } }, description = lexString(R.string.diet_split_protein_pct), decimals = false)
            }
            LabeledNumber(R.string.diet_split_carbs_pct) {
                NumberField(value = form.carbsPct.toDouble(), onValueChange = { change { f -> f.copy(carbsPct = it.toInt()) } }, description = lexString(R.string.diet_split_carbs_pct), decimals = false)
            }
            LabeledNumber(R.string.diet_split_fat_pct) {
                NumberField(value = form.fatPct.toDouble(), onValueChange = { change { f -> f.copy(fatPct = it.toInt()) } }, description = lexString(R.string.diet_split_fat_pct), decimals = false)
            }
        }
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
    Text(
        state.note ?: lexString(R.string.diet_note_add),
        style = AwakenTheme.type.caption,
        color = if (state.note == null) AwakenTheme.colors.textMuted else AwakenTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
            .clickable { onEvent(DietEvent.OpenPrompt(Prompt.Note)) },
    )
    if (plan.targetsVisible) state.offer?.let { TrendCard(it, onEvent) }
    BudgetHero(plan, day.totals, Modifier.staggeredEntrance(0))
    state.slots.forEachIndexed { i, slot ->
        MealWindow(slot, state.slots, day.meals[slot.id].orEmpty(), state.isToday, onEvent, Modifier.staggeredEntrance(1 + i))
    }
    if (!state.isToday && day.meals.isNotEmpty()) {
        SecondaryButton(text = lexString(R.string.diet_copy_day), onClick = { onEvent(DietEvent.CopyToToday(null)) }, modifier = Modifier.fillMaxWidth())
    }
    if (state.slots.size < FoodLibrary.MAX_MEALS) {
        SecondaryButton(text = lexString(R.string.diet_new_meal_add), onClick = { onEvent(DietEvent.OpenPrompt(Prompt.NewMeal)) }, modifier = Modifier.fillMaxWidth())
    }
    WaterRow(state.waterMl, plan.waterTargetMl, onEvent)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SecondaryButton(text = lexString(R.string.diet_weight_button), onClick = { onEvent(DietEvent.OpenPanel(Panel.WEIGHT)) })
        SecondaryButton(text = lexString(R.string.diet_nutrients_button), onClick = { onEvent(DietEvent.OpenPanel(Panel.NUTRIENTS)) })
        SecondaryButton(text = lexString(R.string.diet_report_button), onClick = { onEvent(DietEvent.OpenPanel(Panel.REPORT)) })
        if (plan.targetsVisible) SecondaryButton(text = lexString(R.string.diet_fast_button), onClick = { onEvent(DietEvent.OpenPanel(Panel.FASTING)) })
    }
}

@Composable
private fun TrendCard(offer: TrendOffer, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.diet_offer_title), accent = colors.accent, reveal = false) {
        Text(lexString(R.string.diet_offer_body, formatAmount(offer.trendKg), formatAmount(offer.planWeightKg)), style = AwakenTheme.type.body, color = colors.textSecondary)
        PrimaryButton(text = lexString(R.string.diet_offer_accept), onClick = { onEvent(DietEvent.AcceptTrend) }, modifier = Modifier.fillMaxWidth())
        QuietButton(text = lexString(R.string.diet_offer_dismiss), onClick = { onEvent(DietEvent.DismissTrend) })
    }
}

@Composable
private fun WaterRow(ml: Int, targetMl: Int, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.diet_water_title), accent = colors.accent, reveal = false) {
        Text(lexString(R.string.diet_water_line, formatCount(ml), formatCount(targetMl)), style = AwakenTheme.type.label, color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            WATER_STEPS_ML.forEach { step ->
                SecondaryButton(
                    text = lexString(R.string.diet_water_add, step),
                    onClick = { onEvent(DietEvent.AddWater(step)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row {
            QuietButton(text = lexString(R.string.diet_water_undo), onClick = { onEvent(DietEvent.UndoWater) }, enabled = ml > 0)
            QuietButton(text = lexString(R.string.diet_water_target_button), onClick = { onEvent(DietEvent.OpenPanel(Panel.WATER)) })
        }
    }
}

private val WATER_STEPS_ML = listOf(Hydration.GLASS_ML, Hydration.GLASS_ML * 2)

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

/** The day's number: calories left (or over), the bar to the goal, eaten against goal, then the four macros in a grid. */
@Composable
private fun BudgetHero(plan: DietPlan, totals: Nutrients, modifier: Modifier = Modifier) {
    if (!plan.targetsVisible) {
        HiddenTargetsHero(totals, modifier)
        return
    }
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val goal = plan.plan.kcal
    val eaten = totals[Nutrient.KCAL].roundToInt()
    val remaining = goal - eaten
    val over = remaining < 0
    val ringDescription = lexString(R.string.cd_diet_ring, formatCount(eaten), formatCount(goal))
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Over target stays neutral: the words say it, the colour never scolds (plan 7.3).
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Chart(ChartSpec.Ring(progress = if (goal > 0) (eaten.toFloat() / goal).coerceAtMost(1f) else 0f, description = ringDescription, color = colors.accent)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatCount(eaten), style = type.numeric, color = colors.textPrimary)
                    Text(lexString(R.string.diet_goal_line, formatCount(goal)), style = type.caption, color = colors.textSecondary)
                }
            }
            BigValue(formatCount(abs(remaining)), lexString(if (over) R.string.diet_kcal_over_unit else R.string.diet_kcal_left))
        }
        MACROS.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                pair.forEach { nutrient ->
                    Column(modifier = Modifier.weight(1f)) {
                        NutrientLine(nutrient, totals[nutrient], plan.plan.targets[nutrient]?.amount, TargetKind.GOAL)
                    }
                }
            }
        }
    }
}

/** What the day shows when the guard hides targets: only what was eaten, and one neutral line (GD 20.2). */
@Composable
private fun HiddenTargetsHero(totals: Nutrients, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        BigValue(formatCount(totals[Nutrient.KCAL].roundToInt()), lexString(R.string.diet_eaten_unit), large = true)
        Text(lexString(R.string.diet_targets_off), style = AwakenTheme.type.body, color = colors.textSecondary)
        MACROS.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                pair.forEach { nutrient -> Column(modifier = Modifier.weight(1f)) { NutrientLine(nutrient, totals[nutrient], null, TargetKind.GOAL) } }
            }
        }
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
private fun MealWindow(slot: MealSlot, slots: List<MealSlot>, entries: List<DiaryEntry>, isToday: Boolean, onEvent: (DietEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val kcal = entries.sumOf { it.amounts[Nutrient.KCAL] }.roundToInt()
    SystemWindow(
        title = mealLabel(slot.id, slots),
        accent = colors.accent,
        modifier = modifier,
        reveal = false,
        headerTrailing = {
            Text(lexString(R.string.diet_meal_kcal, formatCount(kcal)), style = AwakenTheme.type.label, color = colors.textSecondary)
            HeaderIconButton(AwakenGlyphs.Plus, lexString(R.string.cd_diet_add_to_meal, mealLabel(slot.id, slots)), { onEvent(DietEvent.OpenSearch(slot.id)) })
        },
    ) {
        if (entries.isEmpty()) Text(lexString(R.string.diet_meal_empty), style = AwakenTheme.type.caption, color = colors.textMuted)
        entries.forEach { entry ->
            EntryRow(entry.row, entry.amounts, onEvent)
        }
        if (entries.isNotEmpty()) {
            QuietButton(text = lexString(R.string.diet_save_meal_action), onClick = { onEvent(DietEvent.OpenPrompt(Prompt.SaveMeal(slot.id))) })
            if (!isToday) QuietButton(text = lexString(R.string.diet_copy_meal), onClick = { onEvent(DietEvent.CopyToToday(slot.id)) })
        }
        if (!slot.builtIn) QuietButton(text = lexString(R.string.diet_remove_meal), onClick = { onEvent(DietEvent.DeleteMeal(slot.id)) })
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
            .padding(vertical = Spacing.sm),
    ) {
        Text(row.name, style = AwakenTheme.type.label, color = colors.textPrimary, maxLines = 2)
        Text(
            lexString(R.string.diet_entry_meta, amount, formatCount(amounts[Nutrient.KCAL].roundToInt())),
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
        )
    }
}

// Labels and formatting

/** A meal's own name, or the app's word for a built-in one. */
@Composable
internal fun mealLabel(id: String, slots: List<MealSlot>): String {
    slots.firstOrNull { it.id == id }?.name?.let { return it }
    return Meal.entries.firstOrNull { it.name == id }?.let { lexString(it.nameRes()) } ?: id
}

@StringRes
internal fun Meal.nameRes(): Int = when (this) {
    Meal.BREAKFAST -> R.string.diet_meal_breakfast
    Meal.LUNCH -> R.string.diet_meal_lunch
    Meal.DINNER -> R.string.diet_meal_dinner
    Meal.SNACK -> R.string.diet_meal_snack
}

@StringRes
private fun LossPace.nameRes(): Int = when (this) {
    LossPace.GENTLE -> R.string.diet_pace_gentle
    LossPace.STEADY -> R.string.diet_pace_steady
    LossPace.FASTER -> R.string.diet_pace_faster
}

@StringRes
private fun MacroPreset.nameRes(): Int = when (this) {
    MacroPreset.BALANCED -> R.string.diet_split_balanced
    MacroPreset.HIGH_PROTEIN -> R.string.diet_split_high_protein
    MacroPreset.CUSTOM -> R.string.diet_split_custom
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

internal fun previewDietState(setup: Boolean = false, hidden: Boolean = false, offer: Boolean = false): DietUiState {
    val dietSetup = DietSetup(Sex.MALE, 1998, 175.0, 80.0, ActivityLevel.MODERATE, WeightGoal.MAINTAIN)
    val date = LocalDate.of(2026, 10, 27)
    val body = BodyProfile(dietSetup.sex, age = 28, dietSetup.heightCm, dietSetup.weightKg, dietSetup.activity, dietSetup.goal)
    val plan = DietPlan(dietSetup, emptyMap(), NutritionMath.plan(body), targetsVisible = !hidden)
    fun row(id: String, meal: Meal, name: String, grams: Double, label: String?, per100: Nutrients) = DiaryEntry(
        FoodLogEntity(id, date, meal.name, "usda:$id", name, grams, label, "{}", Instant.EPOCH),
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
        offer = if (offer) TrendOffer(78.4, 80.0) else null,
        waterMl = 750,
        date = date,
        isToday = true,
        plan = plan,
        day = DiaryDay(date, entries.groupBy { it.row.meal }, Nutrients.sum(entries.map { it.amounts })),
        slots = listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.SNACK, Meal.DINNER).mapIndexed { i, m -> MealSlot(m.name, null, i, builtIn = true) },
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

@PhonePreviews
@Composable
private fun DietHiddenTargetsPreview() {
    AwakenTheme(reduceMotion = true) { DietScreen(previewDietState(hidden = true), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun DietOfferPreview() {
    AwakenTheme(reduceMotion = true) { DietScreen(previewDietState(offer = true), onEvent = {}) }
}
