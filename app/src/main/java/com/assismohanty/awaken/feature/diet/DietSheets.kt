package com.assismohanty.awaken.feature.diet

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.db.entity.MeasureKind
import com.assismohanty.awaken.core.data.db.entity.WeightLogEntity
import com.assismohanty.awaken.core.domain.CustomFoodDraft
import com.assismohanty.awaken.core.data.db.entity.MealPlanEntryEntity
import com.assismohanty.awaken.core.data.db.entity.RecipeEntity
import com.assismohanty.awaken.core.domain.IngredientDraft
import com.assismohanty.awaken.core.domain.RecipeSaveResult
import androidx.compose.material3.Switch
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.engine.body.MealReminderRules
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.engine.body.FastState
import com.assismohanty.awaken.engine.body.FastingRules
import com.assismohanty.awaken.engine.body.NutrientCoverage
import com.assismohanty.awaken.engine.body.TargetKind
import com.assismohanty.awaken.engine.body.WeekReport
import com.assismohanty.awaken.core.domain.CustomFoodResult
import com.assismohanty.awaken.core.domain.MealSlot
import com.assismohanty.awaken.core.domain.SavedMealSummary
import androidx.compose.ui.text.input.KeyboardType
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.awakenTextFieldColors
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.components.symbolRes
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.DietPlan
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DietSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.surfaceOverlay,
        shape = AwakenTheme.shapes.window,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { content() }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = AwakenTheme.type.windowTitle, color = AwakenTheme.colors.accent, modifier = Modifier.semantics { heading() })
}

// Search

@Composable
fun FoodSearchSheet(search: FoodSearch, slots: List<MealSlot>, savedMeals: List<SavedMealSummary>, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.CloseSearch) }) { FoodSearchContent(search, slots, savedMeals, onEvent) }
}

@Composable
fun FoodSearchContent(search: FoodSearch, slots: List<MealSlot>, savedMeals: List<SavedMealSummary>, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SheetTitle(lexString(R.string.diet_search_title, mealLabel(search.meal, slots)))
    if (search.showCustomForm) {
        CustomFoodForm(search.customError, onEvent)
        return
    }
    OutlinedTextField(
        value = search.query,
        onValueChange = { onEvent(DietEvent.Query(it)) },
        label = { Text(lexString(R.string.diet_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    val blank = search.query.isBlank()
    if (blank) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ChoiceChip(text = lexString(R.string.diet_search_recent), selected = search.tab == SearchTab.RECENT, onClick = { onEvent(DietEvent.PickTab(SearchTab.RECENT)) })
            ChoiceChip(text = lexString(R.string.diet_search_favourites), selected = search.tab == SearchTab.FAVOURITES, onClick = { onEvent(DietEvent.PickTab(SearchTab.FAVOURITES)) })
            if (search.mode == SearchMode.DIARY) ChoiceChip(text = lexString(R.string.diet_search_meals), selected = search.tab == SearchTab.MEALS, onClick = { onEvent(DietEvent.PickTab(SearchTab.MEALS)) })
            ChoiceChip(text = lexString(R.string.diet_search_recipes), selected = search.tab == SearchTab.RECIPES, onClick = { onEvent(DietEvent.PickTab(SearchTab.RECIPES)) })
        }
    }
    if (blank && search.tab == SearchTab.MEALS) {
        SavedMealList(savedMeals, onEvent)
    } else if (search.results.isEmpty()) {
        val empty = when {
            !blank -> R.string.diet_search_empty
            search.tab == SearchTab.FAVOURITES -> R.string.diet_search_favourites_empty
            search.tab == SearchTab.RECIPES -> R.string.diet_search_recipes_empty
            else -> R.string.diet_search_recent_empty
        }
        Text(
            lexString(empty),
            style = AwakenTheme.type.body,
            color = colors.textSecondary,
        )
    }
    search.results.forEachIndexed { i, food ->
        if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
        FoodRow(food, onClick = { onEvent(DietEvent.PickFood(food)) })
    }
    if (!blank && search.results.isEmpty()) Text(lexString(R.string.diet_search_none_title), style = AwakenTheme.type.label, color = colors.textPrimary)
    SecondaryButton(text = lexString(R.string.diet_custom_create), onClick = { onEvent(DietEvent.OpenCustomFood) }, modifier = Modifier.fillMaxWidth())
    if (search.mode == SearchMode.DIARY) SecondaryButton(text = lexString(R.string.diet_quick_add), onClick = { onEvent(DietEvent.OpenPrompt(Prompt.QuickAdd(search.meal))) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SavedMealList(savedMeals: List<SavedMealSummary>, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    if (savedMeals.isEmpty()) Text(lexString(R.string.diet_search_meals_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
    savedMeals.forEachIndexed { i, meal ->
        if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).clickable { onEvent(DietEvent.AddSavedMeal(meal.id)) }.padding(vertical = Spacing.xs)) {
                Text(meal.name, style = AwakenTheme.type.label, color = colors.textPrimary)
                Text(lexString(R.string.diet_saved_meal_line, meal.items, formatAmount(meal.kcal)), style = AwakenTheme.type.caption, color = colors.textSecondary)
            }
            QuietButton(text = lexString(R.string.cd_diet_delete_saved_meal, meal.name), onClick = { onEvent(DietEvent.DeleteSavedMeal(meal.id)) })
        }
    }
}

/** The small input sheets over the diary: a new meal, a meal to save, the day's note, a quick add. */
@Composable
fun PromptSheet(prompt: Prompt, note: String?, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePrompt) }) { PromptContent(prompt, note, onEvent) }
}

@Composable
fun PromptContent(prompt: Prompt, note: String?, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var text by rememberSaveable(prompt) { mutableStateOf(if (prompt is Prompt.Note) note.orEmpty() else "") }
    var values by remember(prompt) { mutableStateOf(emptyMap<Nutrient, Double>()) }
    val title = when (prompt) {
        Prompt.NewMeal -> R.string.diet_new_meal_title
        is Prompt.SaveMeal -> R.string.diet_save_meal_title
        Prompt.Note -> R.string.diet_note_title
        is Prompt.QuickAdd -> R.string.diet_quick_title
    }
    val hint = when (prompt) {
        Prompt.NewMeal -> R.string.diet_new_meal_name
        is Prompt.SaveMeal -> R.string.diet_new_meal_name
        Prompt.Note -> R.string.diet_note_hint
        is Prompt.QuickAdd -> R.string.diet_quick_name
    }
    SheetTitle(lexString(title))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(if (prompt is Prompt.Note) MAX_NOTE else MAX_NAME) },
        label = { Text(lexString(hint)) },
        singleLine = prompt !is Prompt.Note,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (prompt is Prompt.QuickAdd) {
        QUICK_FIELDS.forEach { nutrient ->
            val unit = lexString(nutrient.unit.symbolRes())
            LabeledRow(nutrient.nameRes()) {
                NumberField(
                    value = values[nutrient] ?: 0.0,
                    onValueChange = { v -> values = if (v > 0.0) values + (nutrient to v) else values - nutrient },
                    description = lexString(R.string.cd_diet_custom_value, lexString(nutrient.nameRes()), unit),
                    key = nutrient,
                    suffix = unit,
                )
            }
        }
    }
    val (saveText, enabled, onSave) = when (prompt) {
        Prompt.NewMeal -> Triple<Int, Boolean, () -> Unit>(R.string.diet_new_meal_save, text.isNotBlank(), { onEvent(DietEvent.AddMeal(text)) })
        is Prompt.SaveMeal -> Triple<Int, Boolean, () -> Unit>(R.string.diet_new_meal_save, text.isNotBlank(), { onEvent(DietEvent.SaveMeal(prompt.slotId, text)) })
        Prompt.Note -> Triple<Int, Boolean, () -> Unit>(R.string.diet_note_save, true, { onEvent(DietEvent.SaveNote(text)) })
        is Prompt.QuickAdd -> Triple(R.string.diet_quick_save, (values[Nutrient.KCAL] ?: 0.0) > 0.0) { onEvent(DietEvent.QuickAdd(prompt.slotId, text, Nutrients(values))) }
    }
    PrimaryButton(text = lexString(saveText), onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth())
    QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(DietEvent.ClosePrompt) })
}

private val QUICK_FIELDS = listOf(Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT)
private const val MAX_NOTE = 500

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    val colors = AwakenTheme.colors
    val per100 = food.per100
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(food.name, style = AwakenTheme.type.label, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (food.source == FoodSource.CUSTOM) Pill(text = lexString(R.string.diet_source_custom), color = colors.accent)
        }
        Text(
            lexString(
                R.string.diet_search_per100,
                formatAmount(per100[Nutrient.KCAL]),
                formatAmount(per100[Nutrient.PROTEIN]),
                formatAmount(per100[Nutrient.CARBS]),
                formatAmount(per100[Nutrient.FAT]),
            ),
            style = AwakenTheme.type.caption,
            color = colors.textSecondary,
        )
    }
}

// Amount

@Composable
fun AmountSheet(form: AmountForm, slots: List<MealSlot>, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.CloseAmount) }) { AmountContent(form, slots, onEvent) }
}

@Composable
fun AmountContent(form: AmountForm, slots: List<MealSlot>, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val change: ((AmountForm) -> AmountForm) -> Unit = { onEvent(DietEvent.AmountChanged(it)) }
    Text(form.food.name, style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ChoiceChip(text = lexString(R.string.diet_amount_grams), selected = form.portion == null, onClick = { change { it.copy(portion = null) } })
        form.portions.forEach { portion ->
            ChoiceChip(
                text = "${portion.label} (${formatAmount(portion.grams)} g)",
                selected = form.portion == portion,
                onClick = { change { it.copy(portion = portion, count = 1.0) } },
            )
        }
    }
    val portion = form.portion
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (portion == null) {
            NumberField(
                value = form.grams,
                onValueChange = { change { f -> f.copy(grams = it) } },
                description = lexString(R.string.diet_amount_grams),
                key = "grams",
                suffix = lexString(R.string.nutrient_unit_g),
            )
        } else {
            NumberField(
                value = form.count,
                onValueChange = { change { f -> f.copy(count = it) } },
                description = lexString(R.string.diet_amount_count),
                key = portion,
            )
            Text(lexString(R.string.diet_amount_total, formatAmount(form.totalGrams)), style = AwakenTheme.type.label, color = colors.textSecondary)
        }
    }
    val amounts = form.amounts
    val range = form.kcalRange
    Text(
        lexString(
            if (range != null) R.string.diet_amount_preview_range else R.string.diet_amount_preview,
            if (range != null) lexString(R.string.diet_kcal_range, formatAmount(range.start), formatAmount(range.endInclusive)) else formatAmount(amounts[Nutrient.KCAL]),
            formatAmount(amounts[Nutrient.PROTEIN]),
            formatAmount(amounts[Nutrient.CARBS]),
            formatAmount(amounts[Nutrient.FAT]),
        ),
        style = AwakenTheme.type.numericSmall,
        color = colors.accent,
    )
    if (form.food.estimated) Pill(text = lexString(R.string.diet_estimate_tag), color = colors.textSecondary)
    if (form.mode != SearchMode.RECIPE) SecondaryButton(
        text = lexString(if (form.favourite) R.string.diet_favourite_remove else R.string.diet_favourite_add),
        onClick = { onEvent(DietEvent.ToggleFavourite) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.mode != SearchMode.RECIPE) Text(lexString(R.string.diet_amount_meal), style = AwakenTheme.type.label, color = colors.textSecondary)
    if (form.mode != SearchMode.RECIPE) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        slots.forEach { slot ->
            ChoiceChip(text = mealLabel(slot.id, slots), selected = form.meal == slot.id, onClick = { change { it.copy(meal = slot.id) } })
        }
    }
    PrimaryButton(
        text = lexString(when { form.mode == SearchMode.RECIPE -> R.string.diet_amount_add_ingredient; form.mode == SearchMode.PLAN && form.editingPlanId == null -> R.string.diet_amount_add_plan; form.editingPlanId != null -> R.string.diet_amount_save; form.editingRowId != null -> R.string.diet_amount_save; else -> R.string.diet_amount_add }),
        onClick = { onEvent(DietEvent.ConfirmAmount) },
        enabled = form.valid,
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.editingRowId != null) QuietButton(text = lexString(R.string.diet_amount_delete), onClick = { onEvent(DietEvent.DeleteEntry) })
}

// Targets

private val TARGET_GROUPS: List<Pair<Int, List<Nutrient>>> = listOf(
    R.string.diet_targets_macros to listOf(Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT, Nutrient.FIBER, Nutrient.SUGARS),
    R.string.diet_targets_fats to listOf(Nutrient.SAT_FAT, Nutrient.MONO_FAT, Nutrient.POLY_FAT, Nutrient.TRANS_FAT, Nutrient.CHOLESTEROL),
    R.string.diet_targets_minerals to listOf(
        Nutrient.SODIUM, Nutrient.POTASSIUM, Nutrient.CALCIUM, Nutrient.IRON, Nutrient.MAGNESIUM,
        Nutrient.VITAMIN_A, Nutrient.VITAMIN_C, Nutrient.VITAMIN_D,
    ),
    R.string.diet_targets_other to listOf(Nutrient.CAFFEINE, Nutrient.ALCOHOL),
)

@Composable
fun TargetsSheet(plan: DietPlan, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.CloseTargets) }) { TargetsContent(plan, onEvent) }
}

/** Every nutrient with its default; a typed number overrides it, an empty field goes back to the default. */
@Composable
fun TargetsContent(plan: DietPlan, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var draft by remember(plan) { mutableStateOf(plan.overrides) }
    var resets by remember { mutableIntStateOf(0) }
    val defaults = remember(plan) { plan.plan.targets - plan.overrides.keys }
    SheetTitle(lexString(R.string.diet_targets_title))
    Text(lexString(R.string.diet_targets_intro), style = AwakenTheme.type.caption, color = colors.textSecondary)
    TARGET_GROUPS.forEach { (title, nutrients) ->
        Text(lexString(title), style = AwakenTheme.type.label, color = colors.accent)
        nutrients.forEach { nutrient ->
            val unit = lexString(nutrient.unit.symbolRes())
            val fallback = defaults[nutrient]?.amount
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lexString(nutrient.nameRes()), style = AwakenTheme.type.label, color = colors.textPrimary)
                    Text(
                        fallback?.let { lexString(R.string.diet_targets_default, "${formatAmount(it)} $unit") }
                            ?: lexString(R.string.diet_targets_none),
                        style = AwakenTheme.type.caption,
                        color = colors.textMuted,
                    )
                }
                NumberField(
                    value = draft[nutrient] ?: 0.0,
                    onValueChange = { value -> draft = if (value > 0.0) draft + (nutrient to value) else draft - nutrient },
                    description = lexString(R.string.cd_diet_target, lexString(nutrient.nameRes()), unit),
                    key = nutrient to resets,
                    suffix = unit,
                )
            }
        }
    }
    PrimaryButton(text = lexString(R.string.action_save), onClick = { onEvent(DietEvent.SaveTargets(draft)) }, modifier = Modifier.fillMaxWidth())
    QuietButton(
        text = lexString(R.string.diet_targets_reset),
        onClick = {
            draft = emptyMap()
            resets++
        },
    )
    QuietButton(text = lexString(R.string.diet_targets_redo_setup), onClick = { onEvent(DietEvent.EditProfile) })
}

// Custom food

private val CUSTOM_FIELDS = listOf(
    Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT, Nutrient.FIBER, Nutrient.SUGARS,
    Nutrient.SAT_FAT, Nutrient.SODIUM, Nutrient.CHOLESTEROL, Nutrient.POTASSIUM, Nutrient.CALCIUM, Nutrient.IRON,
)

@Composable
private fun CustomFoodForm(error: CustomFoodResult.Reason?, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var values by remember { mutableStateOf(emptyMap<Nutrient, Double>()) }
    var portionLabel by rememberSaveable { mutableStateOf("") }
    var portionGrams by remember { mutableStateOf(0.0) }
    var brand by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf("") }
    var perServing by rememberSaveable { mutableStateOf(false) }
    var servingGrams by remember { mutableStateOf(0.0) }
    val servingLabel = lexString(R.string.diet_custom_serving_label)
    Text(lexString(R.string.diet_custom_title), style = AwakenTheme.type.title, color = colors.textPrimary)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(MAX_NAME) },
        label = { Text(lexString(R.string.diet_custom_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = brand,
        onValueChange = { brand = it.take(MAX_NAME) },
        label = { Text(lexString(R.string.diet_custom_brand)) },
        singleLine = true,
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = barcode,
        onValueChange = { barcode = it.filter(Char::isDigit).take(MAX_BARCODE) },
        label = { Text(lexString(R.string.diet_custom_barcode)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ChoiceChip(text = lexString(R.string.diet_custom_mode_100), selected = !perServing, onClick = { perServing = false })
        ChoiceChip(text = lexString(R.string.diet_custom_mode_serving), selected = perServing, onClick = { perServing = true })
    }
    if (perServing) {
        LabeledRow(R.string.diet_custom_serving_grams) {
            NumberField(value = servingGrams, onValueChange = { servingGrams = it }, description = lexString(R.string.diet_custom_serving_grams), suffix = lexString(R.string.nutrient_unit_g))
        }
    }
    Text(lexString(if (perServing) R.string.diet_custom_mode_serving else R.string.diet_custom_per100), style = AwakenTheme.type.label, color = colors.accent)
    CUSTOM_FIELDS.forEach { nutrient ->
        val unit = lexString(nutrient.unit.symbolRes())
        LabeledRow(nutrient.nameRes()) {
            NumberField(
                value = values[nutrient] ?: 0.0,
                onValueChange = { v -> values = if (v > 0.0) values + (nutrient to v) else values - nutrient },
                description = lexString(R.string.cd_diet_custom_value, lexString(nutrient.nameRes()), unit),
                key = nutrient,
                suffix = unit,
            )
        }
    }
    Text(lexString(R.string.diet_custom_portion), style = AwakenTheme.type.label, color = colors.accent)
    OutlinedTextField(
        value = portionLabel,
        onValueChange = { portionLabel = it.take(MAX_NAME) },
        label = { Text(lexString(R.string.diet_custom_portion_label)) },
        singleLine = true,
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledRow(R.string.diet_custom_portion_grams) {
        NumberField(
            value = portionGrams,
            onValueChange = { portionGrams = it },
            description = lexString(R.string.diet_custom_portion_grams),
            suffix = lexString(R.string.nutrient_unit_g),
        )
    }
    PrimaryButton(
        text = lexString(R.string.diet_custom_save),
        onClick = {
            val portion = if (portionLabel.isNotBlank() && portionGrams > 0.0) Portion(portionLabel.trim(), portionGrams) else null
            // A label gives numbers per serving; the library stores everything per 100 g.
            val scale = if (perServing && servingGrams > 0.0) 100.0 / servingGrams else 1.0
            val per100 = Nutrients(values.mapValues { it.value * scale } + (Nutrient.KCAL to (values[Nutrient.KCAL] ?: 0.0) * scale))
            val serving = servingGrams.takeIf { perServing && it > 0.0 }
            onEvent(
                DietEvent.CreateCustomFood(
                    CustomFoodDraft(
                        name = name, brand = brand, barcode = barcode, per100 = per100,
                        portions = listOfNotNull(portion, serving?.let { Portion(servingLabel, it) }),
                        servingGrams = serving,
                    ),
                ),
            )
        },
        enabled = name.isNotBlank() && (!perServing || servingGrams > 0.0),
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let {
        Text(
            lexString(
                when (it) {
                    CustomFoodResult.Reason.NAME -> R.string.diet_custom_error_name
                    CustomFoodResult.Reason.ENERGY -> R.string.diet_custom_error_energy
                    CustomFoodResult.Reason.BARCODE -> R.string.diet_custom_error_barcode
                },
            ),
            style = AwakenTheme.type.caption,
            color = colors.textPrimary,
        )
    }
    QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(DietEvent.CloseCustomFood) })
}

@Composable
private fun LabeledRow(@StringRes label: Int, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(lexString(label), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        field()
    }
}

private const val MAX_NAME = 80
private const val MAX_BARCODE = 14

// Previews

private val previewRoti = Food(
    id = "usda:171844",
    name = "Bread, chapati or roti, plain, commercially prepared",
    source = FoodSource.USDA_SR,
    per100 = Nutrients(mapOf(Nutrient.KCAL to 297.0, Nutrient.PROTEIN to 11.25, Nutrient.CARBS to 46.36, Nutrient.FAT to 7.45)),
    portions = listOf(Portion("1 piece", 68.0)),
)

@PhonePreviews
@Composable
private fun FoodSearchPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FoodSearchContent(FoodSearch("LUNCH", query = "roti", results = listOf(previewRoti)), previewDietState().slots, emptyList(), onEvent = {})
        }
    }
}

@PhonePreviews
@Composable
private fun AmountPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AmountContent(AmountForm(previewRoti, "LUNCH", previewRoti.portions, portion = previewRoti.portions.first(), count = 2.0), previewDietState().slots, onEvent = {})
        }
    }
}

@PhonePreviews
@Composable
private fun TargetsPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { TargetsContent(checkNotNull(previewDietState().plan), onEvent = {}) }
    }
}

// Weight and measurements

@Composable
fun WeightSheet(weights: List<WeightLogEntity>, trendKg: Double?, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePanel) }) { WeightContent(weights, trendKg, onEvent) }
}

@Composable
fun WeightContent(weights: List<WeightLogEntity>, trendKg: Double?, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var kg by remember { mutableStateOf(0.0) }
    var kind by rememberSaveable { mutableStateOf(MeasureKind.WAIST) }
    var cm by remember { mutableStateOf(0.0) }
    SheetTitle(lexString(R.string.diet_weight_title))
    LabeledRow(R.string.diet_weight_hint) {
        NumberField(value = kg, onValueChange = { kg = it }, description = lexString(R.string.diet_weight_hint), suffix = lexString(R.string.diet_unit_kg))
    }
    PrimaryButton(text = lexString(R.string.diet_weight_save), onClick = { onEvent(DietEvent.LogWeight(kg)) }, enabled = kg > 0.0, modifier = Modifier.fillMaxWidth())
    trendKg?.let { Text(lexString(R.string.diet_weight_trend, formatAmount(it)), style = AwakenTheme.type.label, color = colors.textPrimary) }
    if (weights.isEmpty()) Text(lexString(R.string.diet_weight_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
    weights.take(WEIGHT_ROWS).forEachIndexed { i, w ->
        if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.gameDate.toString(), style = AwakenTheme.type.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(lexString(R.string.diet_weight_row, formatAmount(w.weightKg)), style = AwakenTheme.type.label, color = colors.textPrimary)
            QuietButton(text = lexString(R.string.cd_diet_weight_delete, w.gameDate.toString()), onClick = { onEvent(DietEvent.DeleteWeight(w.id)) })
        }
    }
    Text(lexString(R.string.diet_measure_title), style = AwakenTheme.type.label, color = colors.accent)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MeasureKind.entries.forEach { k -> ChoiceChip(text = lexString(k.nameRes()), selected = kind == k, onClick = { kind = k }) }
    }
    LabeledRow(kind.nameRes()) {
        NumberField(value = cm, onValueChange = { cm = it }, description = lexString(kind.nameRes()), key = kind, suffix = lexString(R.string.diet_unit_cm))
    }
    SecondaryButton(text = lexString(R.string.diet_measure_save), onClick = { onEvent(DietEvent.LogMeasure(kind, cm)) }, enabled = cm > 0.0, modifier = Modifier.fillMaxWidth())
}

@StringRes
private fun MeasureKind.nameRes(): Int = when (this) {
    MeasureKind.WAIST -> R.string.diet_measure_waist
    MeasureKind.HIP -> R.string.diet_measure_hip
    MeasureKind.CHEST -> R.string.diet_measure_chest
    MeasureKind.ARM -> R.string.diet_measure_arm
    MeasureKind.THIGH -> R.string.diet_measure_thigh
}

private const val WEIGHT_ROWS = 10

@PhonePreviews
@Composable
private fun WeightPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            WeightContent(
                weights = listOf(
                    WeightLogEntity("1", java.time.LocalDate.of(2026, 10, 27), 80.4, java.time.Instant.EPOCH, null),
                    WeightLogEntity("2", java.time.LocalDate.of(2026, 10, 20), 81.0, java.time.Instant.EPOCH, null),
                ),
                trendKg = 80.7,
                onEvent = {},
            )
        }
    }
}

// Nutrients, weekly report, fasting, water target

@Composable
fun NutrientsSheet(plan: DietPlan, totals: Nutrients, coverage: Map<Nutrient, Double?>, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePanel) }) { NutrientsContent(plan, totals, coverage) }
}

private val MACRO_SET = setOf(Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT)

/** Every nutrient with a reference value, or that the day has. A nutrient with patchy data shows "Partial data" and no bar. */
@Composable
fun NutrientsContent(plan: DietPlan, totals: Nutrients, coverage: Map<Nutrient, Double?>) {
    val colors = AwakenTheme.colors
    SheetTitle(lexString(R.string.diet_nutrients_title))
    val shown = Nutrient.entries.filter { it !in MACRO_SET && (it in plan.plan.targets || totals[it] > 0.0) }
    if (coverage.values.all { it == null }) Text(lexString(R.string.diet_nutrients_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
    shown.forEach { nutrient ->
        val unit = lexString(nutrient.unit.symbolRes())
        val target = plan.plan.targets[nutrient]
        val partial = NutrientCoverage.isPartial(coverage[nutrient])
        val over = !partial && target?.kind == TargetKind.LIMIT && totals[nutrient] > target.amount
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lexString(nutrient.nameRes()), style = AwakenTheme.type.label, color = colors.textPrimary, modifier = Modifier.weight(1f))
                if (partial) Pill(text = lexString(R.string.diet_nutrients_partial), color = colors.textSecondary)
                Text(
                    if (target != null) lexString(R.string.diet_amount_of_target, formatAmount(totals[nutrient]), formatAmount(target.amount), unit) else lexString(R.string.diet_amount_only, formatAmount(totals[nutrient]), unit),
                    style = AwakenTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
            if (!partial && target != null && target.amount > 0.0) {
                Chart(ChartSpec.Progress(progress = (totals[nutrient] / target.amount).toFloat(), color = if (over) colors.critical else colors.accent))
            }
        }
    }
    if (shown.any { NutrientCoverage.isPartial(coverage[it]) }) Text(lexString(R.string.diet_nutrients_partial_note), style = AwakenTheme.type.caption, color = colors.textMuted)
}

@Composable
fun ReportSheet(report: WeekReport?, plan: DietPlan?, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePanel) }) { ReportContent(report, plan) }
}

@Composable
fun ReportContent(report: WeekReport?, plan: DietPlan?) {
    val colors = AwakenTheme.colors
    SheetTitle(lexString(R.string.diet_report_title))
    if (report == null || report.daysLogged == 0) {
        Text(lexString(R.string.diet_report_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
    } else {
        Text(lexString(R.string.diet_report_days, report.daysLogged), style = AwakenTheme.type.label, color = colors.textPrimary)
        val avg = formatAmount(report.avgKcal ?: 0.0)
        // With targets off (GD 20.2) the report shows what was eaten and never sets it against a target.
        if (plan?.targetsVisible == true) {
            Text(lexString(R.string.diet_report_avg_target, avg, formatAmount(plan.plan.kcal.toDouble())), style = AwakenTheme.type.body, color = colors.textSecondary)
        } else {
            Text(lexString(R.string.diet_report_avg, avg), style = AwakenTheme.type.body, color = colors.textSecondary)
        }
        Text(
            lexString(R.string.diet_report_macros, formatAmount(report.avgProtein ?: 0.0), formatAmount(report.avgCarbs ?: 0.0), formatAmount(report.avgFat ?: 0.0)),
            style = AwakenTheme.type.body,
            color = colors.textSecondary,
        )
        if (report.loggingStreak > 1) Text(lexString(R.string.diet_report_streak, report.loggingStreak), style = AwakenTheme.type.caption, color = colors.textMuted)
    }
    Text(lexString(R.string.diet_estimate_disclaimer), style = AwakenTheme.type.caption, color = colors.textMuted)
}

@Composable
fun FastingSheet(fast: FastView, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePanel) }) { FastingContent(fast, onEvent) }
}

@Composable
fun FastingContent(fast: FastView, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var hours by rememberSaveable { mutableIntStateOf(FastingRules.PRESET_HOURS.first()) }
    SheetTitle(lexString(R.string.diet_fast_title))
    when (val state = fast.state) {
        FastState.Idle -> {
            Text(lexString(R.string.diet_fast_intro), style = AwakenTheme.type.body, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FastingRules.PRESET_HOURS.forEach { h ->
                    ChoiceChip(text = lexString(R.string.diet_fast_preset, h), selected = hours == h, onClick = { hours = h })
                }
            }
            PrimaryButton(text = lexString(R.string.diet_fast_start), onClick = { onEvent(DietEvent.StartFast(hours * MINUTES_PER_HOUR)) }, modifier = Modifier.fillMaxWidth())
        }
        is FastState.Running -> {
            Text(lexString(R.string.diet_fast_running, durationText(state.elapsed), durationText(state.remaining)), style = AwakenTheme.type.body, color = colors.textPrimary)
            SecondaryButton(text = lexString(R.string.diet_fast_end), onClick = { onEvent(DietEvent.EndFast) }, modifier = Modifier.fillMaxWidth())
        }
        is FastState.Reached -> {
            Text(lexString(R.string.diet_fast_reached, durationText(state.elapsed)), style = AwakenTheme.type.body, color = colors.textPrimary)
            PrimaryButton(text = lexString(R.string.diet_fast_end), onClick = { onEvent(DietEvent.EndFast) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun durationText(duration: java.time.Duration): String =
    lexString(R.string.diet_duration, duration.toHours().toInt(), (duration.toMinutes() % MINUTES_PER_HOUR).toInt())

private const val MINUTES_PER_HOUR = 60

@Composable
fun WaterTargetSheet(currentMl: Int, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePanel) }) {
        var ml by remember { mutableStateOf(currentMl.toDouble()) }
        SheetTitle(lexString(R.string.diet_water_target_title))
        LabeledRow(R.string.diet_water_target_title) {
            NumberField(value = ml, onValueChange = { ml = it }, description = lexString(R.string.diet_water_target_title), decimals = false, suffix = lexString(R.string.nutrient_unit_ml))
        }
        PrimaryButton(text = lexString(R.string.diet_water_target_save), onClick = { onEvent(DietEvent.SetWaterTarget(ml.toInt())) }, enabled = ml > 0.0, modifier = Modifier.fillMaxWidth())
        QuietButton(text = lexString(R.string.diet_water_target_default), onClick = { onEvent(DietEvent.SetWaterTarget(null)) })
    }
}

@PhonePreviews
@Composable
private fun NutrientsPreview() {
    val state = previewDietState()
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            NutrientsContent(checkNotNull(state.plan), state.day?.totals ?: Nutrients(), mapOf(Nutrient.IRON to 0.5, Nutrient.SODIUM to 1.0, Nutrient.CALCIUM to 1.0, Nutrient.FIBER to 1.0))
        }
    }
}

@PhonePreviews
@Composable
private fun ReportPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ReportContent(WeekReport(5, 2_050.0, 98.0, 240.0, 66.0, 3), previewDietState().plan)
        }
    }
}

@PhonePreviews
@Composable
private fun FastingPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FastingContent(FastView(), onEvent = {})
        }
    }
}

// Meal reminders

@Composable
fun RemindersSheet(slots: List<MealSlot>, reminders: Map<String, Int>, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePanel) }) { RemindersContent(slots, reminders, onEvent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersContent(slots: List<MealSlot>, reminders: Map<String, Int>, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var picking by remember { mutableStateOf<String?>(null) }
    SheetTitle(lexString(R.string.diet_reminders_title))
    Text(lexString(R.string.diet_reminders_intro), style = AwakenTheme.type.body, color = colors.textSecondary)
    val full = reminders.size >= MealReminderRules.MAX_REMINDERS
    slots.forEach { slot ->
        val minutes = reminders[slot.id]
        val label = mealLabel(slot.id, slots)
        val switchLabel = lexString(R.string.diet_reminder_on, label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = AwakenTheme.type.label, color = colors.textPrimary)
                if (minutes != null) {
                    QuietButton(text = timeLabel(minutes) + " · " + lexString(R.string.diet_reminder_time), onClick = { picking = slot.id })
                }
            }
            Switch(
                checked = minutes != null,
                enabled = minutes != null || !full,
                onCheckedChange = { on -> onEvent(DietEvent.SetReminder(slot.id, if (on) MealReminderRules.defaultMinutes(slot.id) else null)) },
                modifier = Modifier.semantics { contentDescription = switchLabel },
            )
        }
    }
    if (full) Text(lexString(R.string.diet_reminders_limit), style = AwakenTheme.type.caption, color = colors.textMuted)
    picking?.let { id ->
        val state = rememberTimePickerState(initialHour = (reminders[id] ?: 0) / MINUTES_PER_HOUR, initialMinute = (reminders[id] ?: 0) % MINUTES_PER_HOUR, is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current))
        androidx.compose.ui.window.Dialog(onDismissRequest = { picking = null }) {
            SystemWindow(title = lexString(R.string.diet_reminder_time_title), accent = colors.accent) {
                TimeInput(state = state, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                    QuietButton(text = lexString(R.string.action_cancel), onClick = { picking = null })
                    PrimaryButton(
                        text = lexString(R.string.action_done),
                        onClick = { onEvent(DietEvent.SetReminder(id, state.hour * MINUTES_PER_HOUR + state.minute)); picking = null },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun timeLabel(minutes: Int): String =
    java.time.LocalTime.ofSecondOfDay(minutes * SECONDS_PER_MINUTE).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

private const val SECONDS_PER_MINUTE = 60L

@PhonePreviews
@Composable
private fun RemindersPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RemindersContent(previewDietState().slots, mapOf("LUNCH" to 13 * 60, "DINNER" to 20 * 60), onEvent = {})
        }
    }
}

// Recipes

@Composable
fun RecipesSheet(recipes: List<RecipeEntity>, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePanel) }) { RecipesContent(recipes, onEvent) }
}

@Composable
fun RecipesContent(recipes: List<RecipeEntity>, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SheetTitle(lexString(R.string.diet_recipes_title))
    if (recipes.isEmpty()) Text(lexString(R.string.diet_recipes_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
    recipes.forEachIndexed { i, recipe ->
        if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
        Column(
            modifier = Modifier.fillMaxWidth().clickable { onEvent(DietEvent.EditRecipe(recipe.id)) }.padding(vertical = Spacing.xs),
        ) {
            Text(recipe.name, style = AwakenTheme.type.label, color = colors.textPrimary)
            Text(lexString(R.string.diet_recipe_servings) + ": " + formatAmount(recipe.servings), style = AwakenTheme.type.caption, color = colors.textSecondary)
        }
    }
    PrimaryButton(text = lexString(R.string.diet_recipe_new), onClick = { onEvent(DietEvent.NewRecipe) }, modifier = Modifier.fillMaxWidth())
}

@Composable
fun RecipeEditorSheet(form: RecipeForm, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.CloseRecipe) }) { RecipeEditorContent(form, onEvent) }
}

@Composable
fun RecipeEditorContent(form: RecipeForm, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val change: ((RecipeForm) -> RecipeForm) -> Unit = { onEvent(DietEvent.RecipeChanged(it)) }
    var scaleTo by remember(form.id) { mutableStateOf(form.servings) }
    SheetTitle(lexString(R.string.diet_recipe_new))
    OutlinedTextField(
        value = form.name,
        onValueChange = { v -> change { it.copy(name = v.take(MAX_NAME)) } },
        label = { Text(lexString(R.string.diet_recipe_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledRow(R.string.diet_recipe_servings) {
        NumberField(value = form.servings, onValueChange = { v -> change { it.copy(servings = v) } }, description = lexString(R.string.diet_recipe_servings), key = "servings")
    }
    LabeledRow(R.string.diet_recipe_cooked) {
        NumberField(value = form.cookedGrams, onValueChange = { v -> change { it.copy(cookedGrams = v) } }, description = lexString(R.string.diet_recipe_cooked), key = "cooked", suffix = lexString(R.string.nutrient_unit_g))
    }
    Text(lexString(R.string.diet_recipe_cooked_note), style = AwakenTheme.type.caption, color = colors.textMuted)
    Text(lexString(R.string.diet_recipe_ingredients), style = AwakenTheme.type.label, color = colors.accent)
    if (form.ingredients.isEmpty()) Text(lexString(R.string.diet_recipe_no_ingredients), style = AwakenTheme.type.body, color = colors.textSecondary)
    form.ingredients.forEachIndexed { i, ingredient ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lexString(R.string.diet_recipe_ingredient_row, ingredient.name, formatAmount(ingredient.grams)), style = AwakenTheme.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            QuietButton(text = lexString(R.string.cd_diet_recipe_remove_ingredient, ingredient.name), onClick = { onEvent(DietEvent.RemoveIngredient(i)) })
        }
    }
    SecondaryButton(text = lexString(R.string.diet_recipe_add_ingredient), onClick = { onEvent(DietEvent.AddIngredient) }, modifier = Modifier.fillMaxWidth())
    form.perServing?.let { n ->
        Text(
            lexString(R.string.diet_recipe_per_serving, formatAmount(n[Nutrient.KCAL]), formatAmount(n[Nutrient.PROTEIN]), formatAmount(n[Nutrient.CARBS]), formatAmount(n[Nutrient.FAT])),
            style = AwakenTheme.type.numericSmall,
            color = colors.accent,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            NumberField(value = scaleTo, onValueChange = { scaleTo = it }, description = lexString(R.string.diet_recipe_scale), key = "scale")
            SecondaryButton(text = lexString(R.string.diet_recipe_scale_action), onClick = { onEvent(DietEvent.ScaleRecipe(scaleTo)) }, enabled = scaleTo > 0.0)
        }
        Text(lexString(R.string.diet_recipe_scale_note), style = AwakenTheme.type.caption, color = colors.textMuted)
    }
    form.error?.let {
        Text(
            lexString(
                when (it) {
                    RecipeSaveResult.NeedsName -> R.string.diet_recipe_error_name
                    RecipeSaveResult.NeedsIngredients -> R.string.diet_recipe_error_ingredients
                    else -> R.string.diet_recipe_error_servings
                },
            ),
            style = AwakenTheme.type.caption,
            color = colors.textPrimary,
        )
    }
    PrimaryButton(text = lexString(R.string.diet_recipe_save), onClick = { onEvent(DietEvent.SaveRecipe) }, modifier = Modifier.fillMaxWidth())
    form.id?.let { QuietButton(text = lexString(R.string.diet_recipe_delete), onClick = { onEvent(DietEvent.DeleteRecipe(it)) }) }
}

// Meal plan

@Composable
fun PlanSheet(date: java.time.LocalDate, today: java.time.LocalDate, planned: List<MealPlanEntryEntity>, slots: List<MealSlot>, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.ClosePanel) }) { PlanContent(date, today, planned, slots, onEvent) }
}

@Composable
fun PlanContent(date: java.time.LocalDate, today: java.time.LocalDate, planned: List<MealPlanEntryEntity>, slots: List<MealSlot>, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SheetTitle(lexString(R.string.diet_plan_title))
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { onEvent(DietEvent.PlanDay(date.minusDays(1))) }, enabled = date.isAfter(today)) {
            androidx.compose.material3.Icon(com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs.ChevronLeft, contentDescription = lexString(R.string.cd_diet_previous_day), tint = colors.textPrimary)
        }
        Text(
            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(com.assismohanty.awaken.core.designsystem.components.currentLocale()).format(date),
            style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        androidx.compose.material3.IconButton(onClick = { onEvent(DietEvent.PlanDay(date.plusDays(1))) }, enabled = date.isBefore(today.plusDays(com.assismohanty.awaken.core.domain.BodyLog.MAX_PLAN_DAYS))) {
            androidx.compose.material3.Icon(com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs.ChevronRight, contentDescription = lexString(R.string.cd_diet_next_day), tint = colors.textPrimary)
        }
    }
    if (planned.isEmpty()) Text(lexString(R.string.diet_plan_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
    slots.forEach { slot ->
        val rows = planned.filter { it.meal == slot.id }
        if (rows.isNotEmpty()) {
            Text(mealLabel(slot.id, slots), style = AwakenTheme.type.label, color = colors.accent)
            rows.forEach { row ->
                val kcal = com.assismohanty.awaken.core.data.catalog.FoodCatalog.nutrientsFromJson(row.nutrientsJson).forGrams(row.grams)[Nutrient.KCAL]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).then(if (row.logged) Modifier else Modifier.clickable(onClickLabel = lexString(R.string.diet_entry_edit, row.name)) { onEvent(DietEvent.EditPlanned(row)) })) {
                        Text(row.name, style = AwakenTheme.type.label, color = colors.textPrimary)
                        Text(lexString(R.string.diet_entry_meta, row.portionLabel ?: lexString(R.string.diet_entry_grams, formatAmount(row.grams)), formatAmount(kcal)), style = AwakenTheme.type.caption, color = colors.textSecondary)
                    }
                    if (row.logged) {
                        Text(lexString(R.string.diet_plan_logged), style = AwakenTheme.type.caption, color = colors.textSecondary)
                    } else {
                        if (!date.isAfter(today)) QuietButton(text = lexString(R.string.diet_plan_log), onClick = { onEvent(DietEvent.LogPlanned(row.id, date)) })
                        QuietButton(text = lexString(R.string.cd_diet_plan_remove, row.name), onClick = { onEvent(DietEvent.RemovePlanned(row.id)) })
                    }
                }
            }
        }
    }
    if (planned.isNotEmpty()) {
        val total = planned.sumOf { com.assismohanty.awaken.core.data.catalog.FoodCatalog.nutrientsFromJson(it.nutrientsJson).forGrams(it.grams)[Nutrient.KCAL] }
        Text(lexString(R.string.diet_plan_total, formatAmount(total)), style = AwakenTheme.type.caption, color = colors.textMuted)
    }
    PrimaryButton(text = lexString(R.string.diet_plan_add), onClick = { onEvent(DietEvent.AddToPlan) }, modifier = Modifier.fillMaxWidth())
}

@PhonePreviews
@Composable
private fun RecipesEmptyPreview() {
    AwakenTheme(reduceMotion = true) { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { RecipesContent(emptyList(), onEvent = {}) } }
}

@PhonePreviews
@Composable
private fun RecipeEditorPreview() {
    val dal = IngredientDraft("usda:1", "Lentils, raw", 200.0, Nutrients(mapOf(Nutrient.KCAL to 352.0, Nutrient.PROTEIN to 24.6, Nutrient.CARBS to 63.4, Nutrient.FAT to 1.1)))
    val oil = IngredientDraft("usda:2", "Oil, vegetable", 20.0, Nutrients(mapOf(Nutrient.KCAL to 884.0, Nutrient.PROTEIN to 0.0, Nutrient.CARBS to 0.0, Nutrient.FAT to 100.0)))
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { RecipeEditorContent(RecipeForm(name = "Dal tadka", servings = 4.0, cookedGrams = 900.0, ingredients = listOf(dal, oil)), onEvent = {}) }
    }
}

@PhonePreviews
@Composable
private fun PlanPreview() {
    val today = java.time.LocalDate.of(2026, 10, 27)
    val rows = listOf(
        MealPlanEntryEntity("1", today, "BREAKFAST", "usda:1", "Idli", 120.0, "3 pieces", """{"kcal":128.0}""", logged = true),
        MealPlanEntryEntity("2", today, "LUNCH", "usda:2", "Dal tadka", 150.0, null, """{"kcal":120.0}""", logged = false),
    )
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { PlanContent(today, today, rows, previewDietState().slots, onEvent = {}) }
    }
}

private val previewDal = IngredientDraft("usda:1", "Lentils, raw", 200.0, Nutrients(mapOf(Nutrient.KCAL to 352.0, Nutrient.PROTEIN to 24.6, Nutrient.CARBS to 63.4, Nutrient.FAT to 1.1)))
private val previewOil = IngredientDraft("usda:2", "Oil, vegetable", 20.0, Nutrients(mapOf(Nutrient.KCAL to 884.0, Nutrient.PROTEIN to 0.0, Nutrient.CARBS to 0.0, Nutrient.FAT to 100.0)))

@PhonePreviews
@Composable
private fun RecipesListPreview() {
    val now = java.time.Instant.EPOCH
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RecipesContent(listOf(RecipeEntity("recipe:1", "Dal tadka", 4.0, 900.0, null, now, now), RecipeEntity("recipe:2", "Veg pulao", 3.0, null, null, now, now)), onEvent = {})
        }
    }
}

@PhonePreviews
@Composable
private fun RecipeEditorEmptyPreview() {
    AwakenTheme(reduceMotion = true) { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { RecipeEditorContent(RecipeForm(), onEvent = {}) } }
}

@PhonePreviews
@Composable
private fun RecipeScaledPreview() {
    val scaled = RecipeForm(name = "Dal tadka", servings = 4.0, cookedGrams = 900.0, ingredients = listOf(previewDal, previewOil)).scaledTo(8.0)
    AwakenTheme(reduceMotion = true) { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { RecipeEditorContent(scaled, onEvent = {}) } }
}

@PhonePreviews
@Composable
private fun PlanEmptyPreview() {
    val today = java.time.LocalDate.of(2026, 10, 27)
    AwakenTheme(reduceMotion = true) { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { PlanContent(today.plusDays(1), today, emptyList(), previewDietState().slots, onEvent = {}) } }
}

@PhonePreviews
@Composable
private fun PlanPlannedPreview() {
    val today = java.time.LocalDate.of(2026, 10, 27)
    val rows = listOf(
        MealPlanEntryEntity("1", today.plusDays(1), "BREAKFAST", "usda:1", "Idli", 120.0, "3 pieces", """{"kcal":128.0}""", logged = false),
        MealPlanEntryEntity("2", today.plusDays(1), "DINNER", "usda:2", "Dal tadka", 150.0, null, """{"kcal":120.0}""", logged = false),
    )
    AwakenTheme(reduceMotion = true) { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { PlanContent(today.plusDays(1), today, rows, previewDietState().slots, onEvent = {}) } }
}
