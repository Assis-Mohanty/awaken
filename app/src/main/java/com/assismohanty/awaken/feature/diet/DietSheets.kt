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
            ChoiceChip(text = lexString(R.string.diet_search_meals), selected = search.tab == SearchTab.MEALS, onClick = { onEvent(DietEvent.PickTab(SearchTab.MEALS)) })
        }
    }
    if (blank && search.tab == SearchTab.MEALS) {
        SavedMealList(savedMeals, onEvent)
    } else if (search.results.isEmpty()) {
        val empty = when {
            !blank -> R.string.diet_search_empty
            search.tab == SearchTab.FAVOURITES -> R.string.diet_search_favourites_empty
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
    SecondaryButton(text = lexString(R.string.diet_quick_add), onClick = { onEvent(DietEvent.OpenPrompt(Prompt.QuickAdd(search.meal))) }, modifier = Modifier.fillMaxWidth())
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
    SecondaryButton(
        text = lexString(if (form.favourite) R.string.diet_favourite_remove else R.string.diet_favourite_add),
        onClick = { onEvent(DietEvent.ToggleFavourite) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(lexString(R.string.diet_amount_meal), style = AwakenTheme.type.label, color = colors.textSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        slots.forEach { slot ->
            ChoiceChip(text = mealLabel(slot.id, slots), selected = form.meal == slot.id, onClick = { change { it.copy(meal = slot.id) } })
        }
    }
    PrimaryButton(
        text = lexString(if (form.editingRowId != null) R.string.diet_amount_save else R.string.diet_amount_add),
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
fun WeightSheet(weights: List<WeightLogEntity>, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.CloseWeight) }) { WeightContent(weights, onEvent) }
}

@Composable
fun WeightContent(weights: List<WeightLogEntity>, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var kg by remember { mutableStateOf(0.0) }
    var kind by rememberSaveable { mutableStateOf(MeasureKind.WAIST) }
    var cm by remember { mutableStateOf(0.0) }
    SheetTitle(lexString(R.string.diet_weight_title))
    LabeledRow(R.string.diet_weight_hint) {
        NumberField(value = kg, onValueChange = { kg = it }, description = lexString(R.string.diet_weight_hint), suffix = lexString(R.string.diet_unit_kg))
    }
    PrimaryButton(text = lexString(R.string.diet_weight_save), onClick = { onEvent(DietEvent.LogWeight(kg)) }, enabled = kg > 0.0, modifier = Modifier.fillMaxWidth())
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
                listOf(
                    WeightLogEntity("1", java.time.LocalDate.of(2026, 10, 27), 80.4, java.time.Instant.EPOCH, null),
                    WeightLogEntity("2", java.time.LocalDate.of(2026, 10, 20), 81.0, java.time.Instant.EPOCH, null),
                ),
                onEvent = {},
            )
        }
    }
}
