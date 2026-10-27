package com.assismohanty.awaken.feature.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
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
import com.assismohanty.awaken.core.data.catalog.Exercise
import com.assismohanty.awaken.core.designsystem.components.BodyMap
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.awakenTextFieldColors
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle

/** Secondary muscles light at this share of the accent; primaries at full. */
private const val SECONDARY_INTENSITY = 0.4f

/** The exercise library (DESIGN_SYSTEM §4.11): search, equipment chips, tap-a-muscle body map, results, custom form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(picker: ExercisePicker, onEvent: (GymEvent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(GymEvent.ClosePicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.surfaceOverlay,
        shape = AwakenTheme.shapes.window,
    ) {
        ExercisePickerContent(picker, onEvent, Modifier.navigationBarsPadding())
    }
}

@Composable
fun ExercisePickerContent(picker: ExercisePicker, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(stringResource(R.string.gym_picker_title), style = type.windowTitle, color = colors.accent, modifier = Modifier.semantics { heading() })
        if (picker.showCustomForm) {
            CustomExerciseForm(onEvent)
            return@Column
        }
        OutlinedTextField(
            value = picker.query,
            onValueChange = { onEvent(GymEvent.Query(it)) },
            label = { Text(stringResource(R.string.gym_picker_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = awakenTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.gym_picker_equipment), style = type.label, color = colors.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items(Equipment.entries) { gear ->
                ChoiceChip(
                    text = stringResource(gear.nameRes()),
                    selected = gear in picker.equipment,
                    onClick = { onEvent(GymEvent.ToggleEquipment(gear)) },
                    multiSelect = true,
                )
            }
        }
        val selected = picker.muscle
        val selectedName = selected?.let { stringResource(it.nameRes()) } ?: stringResource(R.string.gym_picker_any_muscle)
        BodyMap(
            intensity = selected?.let { mapOf(it to 1f) }.orEmpty(),
            description = stringResource(R.string.cd_gym_picker_map, selectedName),
            onMuscleClick = { onEvent(GymEvent.PickMuscle(it)) },
        )
        Text(stringResource(R.string.gym_picker_muscle_hint), style = type.caption, color = colors.textSecondary)
        // The same choice as chips: the body map is one TalkBack node.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            item {
                ChoiceChip(text = stringResource(R.string.gym_picker_any_muscle), selected = selected == null, onClick = { onEvent(GymEvent.PickMuscle(null)) })
            }
            items(Muscle.entries) { muscle ->
                ChoiceChip(text = stringResource(muscle.nameRes()), selected = muscle == selected, onClick = { onEvent(GymEvent.PickMuscle(muscle)) })
            }
        }
        if (picker.results.isEmpty()) Text(stringResource(R.string.gym_picker_empty), style = type.body, color = colors.textSecondary)
        picker.results.forEachIndexed { i, exercise ->
            if (i > 0) HorizontalDivider(color = colors.divider, thickness = AwakenTheme.dimens.hairline)
            ExerciseResultRow(exercise, onEvent)
        }
        SecondaryButton(text = stringResource(R.string.gym_picker_custom), onClick = { onEvent(GymEvent.OpenCustomForm) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ExerciseResultRow(exercise: Exercise, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.gym_exercise_info, exercise.name)) { onEvent(GymEvent.ShowExercise(exercise)) }
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(exercise.name, style = AwakenTheme.type.label, color = colors.textPrimary)
            Text(
                (listOf(stringResource(exercise.equipment.nameRes())) + exercise.primary.map { stringResource(it.nameRes()) }).joinToString(" · "),
                style = AwakenTheme.type.caption,
                color = colors.textSecondary,
            )
            if (exercise.custom) Pill(text = stringResource(R.string.gym_custom_badge), color = colors.accent)
        }
        QuietButton(text = stringResource(R.string.gym_exercise_add), onClick = { onEvent(GymEvent.AddExercise(exercise.id)) })
    }
}

/** One exercise: muscles on the body map, equipment and level, and the how-to steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(exercise: Exercise, canAdd: Boolean, onEvent: (GymEvent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(GymEvent.CloseExercise) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AwakenTheme.colors.surfaceOverlay,
        shape = AwakenTheme.shapes.window,
    ) {
        ExerciseDetailContent(exercise, canAdd, onEvent, Modifier.navigationBarsPadding())
    }
}

@Composable
fun ExerciseDetailContent(exercise: Exercise, canAdd: Boolean, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val primary = exercise.primary.sortedBy { it.ordinal }.map { stringResource(it.nameRes()) }
    val secondary = exercise.secondary.sortedBy { it.ordinal }.map { stringResource(it.nameRes()) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(exercise.name, style = type.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Pill(text = stringResource(exercise.equipment.nameRes()), color = colors.accent)
            listOfNotNull(exercise.level, exercise.mechanic, exercise.force).forEach { Pill(text = it.replaceFirstChar(Char::titlecase), color = colors.textSecondary) }
            if (exercise.custom) Pill(text = stringResource(R.string.gym_custom_badge), color = colors.accent)
        }
        BodyMap(
            intensity = exercise.secondary.associateWith { SECONDARY_INTENSITY } + exercise.primary.associateWith { 1f },
            description = stringResource(R.string.cd_gym_exercise_map, (primary + secondary).joinToString()),
        )
        Text(stringResource(R.string.gym_exercise_primary, primary.joinToString()), style = type.label, color = colors.textPrimary)
        if (secondary.isNotEmpty()) Text(stringResource(R.string.gym_exercise_secondary, secondary.joinToString()), style = type.label, color = colors.textSecondary)
        if (exercise.instructions.isNotEmpty()) {
            Text(stringResource(R.string.gym_exercise_instructions), style = type.label, color = colors.accent)
            exercise.instructions.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("${i + 1}.", style = type.body, color = colors.textSecondary)
                    Text(step, style = type.body, color = colors.textPrimary)
                }
            }
        }
        if (canAdd) {
            PrimaryButton(text = stringResource(R.string.gym_exercise_add), onClick = { onEvent(GymEvent.AddExercise(exercise.id)) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CustomExerciseForm(onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    var name by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf(Equipment.BODY_ONLY) }
    var primary by rememberSaveable { mutableStateOf(emptySet<Muscle>()) }
    var secondary by rememberSaveable { mutableStateOf(emptySet<Muscle>()) }
    Text(stringResource(R.string.gym_custom_title), style = type.title, color = colors.textPrimary)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(MAX_NAME) },
        label = { Text(stringResource(R.string.gym_custom_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        colors = awakenTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.gym_picker_equipment), style = type.label, color = colors.textSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Equipment.entries.forEach { gear ->
            ChoiceChip(text = stringResource(gear.nameRes()), selected = gear == equipment, onClick = { equipment = gear })
        }
    }
    MuscleChoices(stringResource(R.string.gym_custom_primary), primary) { m -> primary = if (m in primary) primary - m else primary + m }
    MuscleChoices(stringResource(R.string.gym_custom_secondary), secondary - primary) { m -> secondary = if (m in secondary) secondary - m else secondary + m }
    BodyMap(
        intensity = secondary.associateWith { SECONDARY_INTENSITY } + primary.associateWith { 1f },
        description = stringResource(R.string.cd_gym_exercise_map, (primary + secondary).map { stringResource(it.nameRes()) }.joinToString()),
    )
    PrimaryButton(
        text = stringResource(R.string.gym_custom_create),
        onClick = { onEvent(GymEvent.CreateCustom(name, equipment, primary, secondary)) },
        enabled = name.isNotBlank() && primary.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    )
    QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(GymEvent.CloseCustomForm) })
}

@Composable
private fun MuscleChoices(title: String, selected: Set<Muscle>, onToggle: (Muscle) -> Unit) {
    Text(title, style = AwakenTheme.type.label, color = AwakenTheme.colors.textSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Muscle.entries.forEach { muscle ->
            ChoiceChip(text = stringResource(muscle.nameRes()), selected = muscle in selected, onClick = { onToggle(muscle) }, multiSelect = true)
        }
    }
}

private const val MAX_NAME = 60

private val previewBench = Exercise(
    id = "fedb:bench",
    name = "Barbell Bench Press - Medium Grip",
    equipment = Equipment.BARBELL,
    primary = setOf(Muscle.CHEST),
    secondary = setOf(Muscle.SHOULDERS, Muscle.TRICEPS),
    level = "beginner",
    mechanic = "compound",
    force = "push",
    instructions = listOf("Lie back on a flat bench.", "Lower the bar to mid-chest.", "Press back up."),
)

@PhonePreviews
@Composable
private fun ExercisePickerPreview() {
    AwakenTheme(reduceMotion = true) {
        ExercisePickerContent(
            ExercisePicker(query = "press", equipment = setOf(Equipment.BARBELL), muscle = Muscle.CHEST, results = listOf(previewBench)),
            onEvent = {},
        )
    }
}

@PhonePreviews
@Composable
private fun ExerciseDetailPreview() {
    AwakenTheme(reduceMotion = true) { ExerciseDetailContent(previewBench, canAdd = true, onEvent = {}) }
}
