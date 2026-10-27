package com.assismohanty.awaken.feature.diet

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.db.entity.Meal
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
fun FoodSearchSheet(search: FoodSearch, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.CloseSearch) }) { FoodSearchContent(search, onEvent) }
}

@Composable
fun FoodSearchContent(search: FoodSearch, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SheetTitle(stringResource(R.string.diet_search_title, stringResource(search.meal.nameRes())))
    if (search.showCustomForm) {
        CustomFoodForm(onEvent)
        return
    }
    OutlinedTextField(
        value = search.query,
        onValueChange = { onEvent(DietEvent.Query(it)) },
        label = { Text(stringResource(R.string.diet_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    val blank = search.query.isBlank()
    if (blank) Text(stringResource(R.string.diet_search_recent), style = AwakenTheme.type.label, color = colors.textSecondary)
    if (search.results.isEmpty()) {
        Text(
            stringResource(if (blank) R.string.diet_search_recent_empty else R.string.diet_search_empty),
            style = AwakenTheme.type.body,
            color = colors.textSecondary,
        )
    }
    search.results.forEachIndexed { i, food ->
        if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
        FoodRow(food, onClick = { onEvent(DietEvent.PickFood(food)) })
    }
    SecondaryButton(text = stringResource(R.string.diet_custom_create), onClick = { onEvent(DietEvent.OpenCustomFood) }, modifier = Modifier.fillMaxWidth())
}

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
            if (food.source == FoodSource.CUSTOM) Pill(text = stringResource(R.string.diet_source_custom), color = colors.accent)
        }
        Text(
            stringResource(
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
fun AmountSheet(form: AmountForm, onEvent: (DietEvent) -> Unit) {
    DietSheet(onDismiss = { onEvent(DietEvent.CloseAmount) }) { AmountContent(form, onEvent) }
}

@Composable
fun AmountContent(form: AmountForm, onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val change: ((AmountForm) -> AmountForm) -> Unit = { onEvent(DietEvent.AmountChanged(it)) }
    Text(form.food.name, style = AwakenTheme.type.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ChoiceChip(text = stringResource(R.string.diet_amount_grams), selected = form.portion == null, onClick = { change { it.copy(portion = null) } })
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
                description = stringResource(R.string.diet_amount_grams),
                key = "grams",
                suffix = stringResource(R.string.nutrient_unit_g),
            )
        } else {
            NumberField(
                value = form.count,
                onValueChange = { change { f -> f.copy(count = it) } },
                description = stringResource(R.string.diet_amount_count),
                key = portion,
            )
            Text(stringResource(R.string.diet_amount_total, formatAmount(form.totalGrams)), style = AwakenTheme.type.label, color = colors.textSecondary)
        }
    }
    val amounts = form.amounts
    Text(
        stringResource(
            R.string.diet_amount_preview,
            formatAmount(amounts[Nutrient.KCAL]),
            formatAmount(amounts[Nutrient.PROTEIN]),
            formatAmount(amounts[Nutrient.CARBS]),
            formatAmount(amounts[Nutrient.FAT]),
        ),
        style = AwakenTheme.type.numericSmall,
        color = colors.accent,
    )
    Text(stringResource(R.string.diet_amount_meal), style = AwakenTheme.type.label, color = colors.textSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Meal.entries.forEach { meal ->
            ChoiceChip(text = stringResource(meal.nameRes()), selected = form.meal == meal, onClick = { change { it.copy(meal = meal) } })
        }
    }
    PrimaryButton(
        text = stringResource(if (form.editingRowId != null) R.string.diet_amount_save else R.string.diet_amount_add),
        onClick = { onEvent(DietEvent.ConfirmAmount) },
        enabled = form.valid,
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.editingRowId != null) QuietButton(text = stringResource(R.string.diet_amount_delete), onClick = { onEvent(DietEvent.DeleteEntry) })
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
    SheetTitle(stringResource(R.string.diet_targets_title))
    Text(stringResource(R.string.diet_targets_intro), style = AwakenTheme.type.caption, color = colors.textSecondary)
    TARGET_GROUPS.forEach { (title, nutrients) ->
        Text(stringResource(title), style = AwakenTheme.type.label, color = colors.accent)
        nutrients.forEach { nutrient ->
            val unit = stringResource(nutrient.unit.symbolRes())
            val fallback = defaults[nutrient]?.amount
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(nutrient.nameRes()), style = AwakenTheme.type.label, color = colors.textPrimary)
                    Text(
                        fallback?.let { stringResource(R.string.diet_targets_default, "${formatAmount(it)} $unit") }
                            ?: stringResource(R.string.diet_targets_none),
                        style = AwakenTheme.type.caption,
                        color = colors.textMuted,
                    )
                }
                NumberField(
                    value = draft[nutrient] ?: 0.0,
                    onValueChange = { value -> draft = if (value > 0.0) draft + (nutrient to value) else draft - nutrient },
                    description = stringResource(R.string.cd_diet_target, stringResource(nutrient.nameRes()), unit),
                    key = nutrient to resets,
                    suffix = unit,
                )
            }
        }
    }
    PrimaryButton(text = stringResource(R.string.action_save), onClick = { onEvent(DietEvent.SaveTargets(draft)) }, modifier = Modifier.fillMaxWidth())
    QuietButton(
        text = stringResource(R.string.diet_targets_reset),
        onClick = {
            draft = emptyMap()
            resets++
        },
    )
}

// Custom food

private val CUSTOM_FIELDS = listOf(
    Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT, Nutrient.FIBER, Nutrient.SUGARS,
    Nutrient.SAT_FAT, Nutrient.SODIUM, Nutrient.CHOLESTEROL, Nutrient.POTASSIUM, Nutrient.CALCIUM, Nutrient.IRON,
)

@Composable
private fun CustomFoodForm(onEvent: (DietEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var values by remember { mutableStateOf(emptyMap<Nutrient, Double>()) }
    var portionLabel by rememberSaveable { mutableStateOf("") }
    var portionGrams by remember { mutableStateOf(0.0) }
    Text(stringResource(R.string.diet_custom_title), style = AwakenTheme.type.title, color = colors.textPrimary)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(MAX_NAME) },
        label = { Text(stringResource(R.string.diet_custom_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.diet_custom_per100), style = AwakenTheme.type.label, color = colors.accent)
    CUSTOM_FIELDS.forEach { nutrient ->
        val unit = stringResource(nutrient.unit.symbolRes())
        LabeledRow(nutrient.nameRes()) {
            NumberField(
                value = values[nutrient] ?: 0.0,
                onValueChange = { v -> values = if (v > 0.0) values + (nutrient to v) else values - nutrient },
                description = stringResource(R.string.cd_diet_custom_value, stringResource(nutrient.nameRes()), unit),
                key = nutrient,
                suffix = unit,
            )
        }
    }
    Text(stringResource(R.string.diet_custom_portion), style = AwakenTheme.type.label, color = colors.accent)
    OutlinedTextField(
        value = portionLabel,
        onValueChange = { portionLabel = it.take(MAX_NAME) },
        label = { Text(stringResource(R.string.diet_custom_portion_label)) },
        singleLine = true,
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledRow(R.string.diet_custom_portion_grams) {
        NumberField(
            value = portionGrams,
            onValueChange = { portionGrams = it },
            description = stringResource(R.string.diet_custom_portion_grams),
            suffix = stringResource(R.string.nutrient_unit_g),
        )
    }
    PrimaryButton(
        text = stringResource(R.string.diet_custom_save),
        onClick = {
            val portion = if (portionLabel.isNotBlank() && portionGrams > 0.0) Portion(portionLabel.trim(), portionGrams) else null
            onEvent(DietEvent.CreateCustomFood(name, Nutrients(values + (Nutrient.KCAL to (values[Nutrient.KCAL] ?: 0.0))), portion))
        },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
    QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(DietEvent.CloseCustomFood) })
}

@Composable
private fun LabeledRow(@StringRes label: Int, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = AwakenTheme.type.label, color = AwakenTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        field()
    }
}

private const val MAX_NAME = 80

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
            FoodSearchContent(FoodSearch(Meal.LUNCH, query = "roti", results = listOf(previewRoti)), onEvent = {})
        }
    }
}

@PhonePreviews
@Composable
private fun AmountPreview() {
    AwakenTheme(reduceMotion = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AmountContent(AmountForm(previewRoti, Meal.LUNCH, previewRoti.portions, portion = previewRoti.portions.first(), count = 2.0), onEvent = {})
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
