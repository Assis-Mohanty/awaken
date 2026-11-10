package com.assismohanty.awaken.feature.gym

import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.components.heatAlpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import kotlin.math.roundToInt
import com.assismohanty.awaken.core.designsystem.components.SectionTitle
import com.assismohanty.awaken.core.designsystem.components.DetailRows
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.SectionHeader
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.HeaderIconButton
import com.assismohanty.awaken.core.designsystem.components.BodyMap
import com.assismohanty.awaken.core.designsystem.components.NumberField
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.RoundCheck
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.awakenTextFieldColors
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.formatNumber
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.ValueRow
import com.assismohanty.awaken.core.designsystem.body.BodyGeometry
import com.assismohanty.awaken.core.designsystem.components.nameRes
import com.assismohanty.awaken.core.designsystem.components.staggeredEntrance
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.ActiveWorkout
import com.assismohanty.awaken.core.domain.ExerciseRecords
import com.assismohanty.awaken.core.designsystem.components.PrChip
import com.assismohanty.awaken.core.designsystem.components.ProgressionHint
import com.assismohanty.awaken.engine.body.ProgressionHint as EngineHint
import com.assismohanty.awaken.engine.body.RecordEvent
import com.assismohanty.awaken.engine.body.RecordType
import com.assismohanty.awaken.core.domain.GymAnswers
import com.assismohanty.awaken.core.domain.GymOverview
import com.assismohanty.awaken.core.domain.PersonalRecord
import com.assismohanty.awaken.core.domain.RoutineSummary
import com.assismohanty.awaken.core.domain.WorkoutExercise
import com.assismohanty.awaken.core.domain.WorkoutSummary
import com.assismohanty.awaken.core.domain.elapsedSince
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.body.WorkingSets
import com.assismohanty.awaken.engine.body.RestRules
import com.assismohanty.awaken.engine.body.TrainingMath
import com.assismohanty.awaken.core.domain.GymXpResult
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToLong

@Composable
fun GymRoute(modifier: Modifier = Modifier, viewModel: GymViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GymScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Gym (DESIGN_SYSTEM §4.11): the running session when there is one, otherwise the week, routines, records and history. */
@Composable
fun GymScreen(state: GymUiState, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    if (!state.loading && state.active != null && state.prMoment != null) {
        PrMoment(state.prMoment, onEvent)
        return
    }
    val restEnds = state.active?.workout?.restEndsAt?.takeIf { !state.loading && state.restMinimisedFor != it }
    if (state.active != null && restEnds != null) {
        RestScreen(state, state.active, restEnds, onEvent)
        return
    }
    val subtitle = if (state.active == null && state.overview?.trainingDay == true) lexString(R.string.gym_training_day) else null
    val heading = state.active?.let { coachedHeading(state, it) }
    val summary = if (state.active == null) state.summaryId?.let { id -> state.overview?.history?.firstOrNull { it.workoutId == id } } else null
    LargeTitleScaffold(
        title = if (summary != null) lexString(R.string.gym_summary_title) else heading?.first ?: lexString(R.string.gym_title),
        subtitle = if (summary != null) {
            listOfNotNull(summary.name.ifBlank { null }, formatDate(summary.gameDate)).joinToString(" · ")
        } else {
            heading?.second ?: subtitle
        },
        modifier = modifier,
        actions = {
            if (state.active == null && summary == null) {
                HeaderIconButton(AwakenGlyphs.Settings, lexString(R.string.gym_settings_icon_cd), { onEvent(GymEvent.OpenGymSettings) })
            } else if (heading != null) {
                CoachedActions(state.active, onEvent)
            }
        },
    ) {
        if (state.loading) {
            SkeletonCard()
            return@LargeTitleScaffold
        }
        val active = state.active
        if (active != null) {
            ActiveWorkoutContent(state, active, onEvent)
        } else if (summary != null) {
            SessionSummary(summary, state.summaryRecords, state.summaryXp, onEvent)
        } else {
            state.notice?.let { NoticeLine(it, onEvent) }
            state.home?.let { home ->
                GymWeekSection(home, onEvent)
                PlansSection(home, onEvent, Modifier.staggeredEntrance(0))
                MyRoutinesSection(home, onEvent, Modifier.staggeredEntrance(1))
            }
            state.overview?.let { GymHome(it, onEvent) }
        }
    }
    state.home?.let { home ->
        home.planDetail?.let { PlanDetailSheet(it, onEvent) }
        home.editor?.let { RoutineEditorSheet(it, onEvent) }
        home.menu?.let { menu ->
            val index = home.routines.indexOfFirst { it.id == menu.id }
            RoutineMenuSheet(menu, canMoveUp = index > 0, canMoveDown = index in 0 until home.routines.lastIndex, onEvent = onEvent)
        }
        home.assign?.let { AssignDaysSheet(it, onEvent) }
        home.delete?.let { DeleteRoutineDialog(it, onEvent) }
    }
    state.picker?.let { ExercisePickerSheet(it, onEvent) }
    state.detail?.let { ExerciseDetailSheet(it, canAdd = state.picker?.browse != true, onEvent = onEvent) }
    state.plates?.let { PlatesSheet(it, onEvent) }
    if (state.settingsOpen) GymSettingsSheet(state.prefs, state.home?.answers ?: GymAnswers(), onEvent)
    state.history?.let { HistorySheet(it, onEvent) }
    state.strength?.let { StrengthSheet(it, onEvent) }
    if (state.confirmDiscard) DiscardDialog(onEvent)
    val workoutSets = state.active?.exercises.orEmpty().flatMap { it.sets }
    state.numberPad?.let { pad -> NumberPadSheet(pad, workoutSets.firstOrNull { it.id == pad.setId }, onEvent) }
    state.workoutMenu?.let { position -> state.active?.let { WorkoutMenuSheet(it, position, onEvent) } }
    state.replace?.let { ReplaceSheet(it, onEvent) }
    if (state.lastSetSheet) LastSetSheet(workoutSets.filter { it.done }.maxByOrNull { it.completedAt ?: java.time.Instant.EPOCH }, onEvent)
    if (state.confirmFinish) FinishConfirmDialog(workoutSets.count { !it.done }, onEvent)
    if (state.routinePrompt) RoutineNameDialog(onEvent)
}

@Composable
private fun NoticeLine(notice: GymNotice, onEvent: (GymEvent) -> Unit) {
    val text = when (notice) {
        GymNotice.FINISHED -> R.string.gym_notice_finished
        GymNotice.DISCARDED_EMPTY -> R.string.gym_notice_discarded_empty
        GymNotice.ROUTINE_SAVED -> R.string.gym_notice_routine_saved
        GymNotice.PLAN_ADDED -> R.string.gym_notice_plan_added
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(lexString(text), style = AwakenTheme.type.body, color = AwakenTheme.colors.accent, modifier = Modifier.weight(1f))
        IconButton(onClick = { onEvent(GymEvent.DismissNotice) }) {
            Icon(AwakenGlyphs.Close, contentDescription = lexString(R.string.action_dismiss), tint = AwakenTheme.colors.textSecondary)
        }
    }
}

// Home

@Composable
private fun GymHome(overview: GymOverview, onEvent: (GymEvent) -> Unit) {
    WeekCard(overview, Modifier.staggeredEntrance(2))
    RecordsSection(overview.records, onEvent, Modifier.staggeredEntrance(3))
    HistorySection(overview.history, onEvent, Modifier.staggeredEntrance(4))
    MoreSection(onEvent, Modifier.staggeredEntrance(5))
}

/** This week: the three totals, and a link that opens the muscle map in a sheet. */
@Composable
private fun WeekCard(overview: GymOverview, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    AwakenCard(modifier = modifier.fillMaxWidth(), reveal = false) {
        Text(lexString(R.string.gym_window_week), style = AwakenTheme.type.caption, color = colors.textSecondary)
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat(lexString(R.string.gym_week_workouts), formatCount(overview.weekWorkouts), Modifier.weight(1f))
            Stat(lexString(R.string.gym_week_sets), formatCount(overview.weekSets), Modifier.weight(1f))
            Stat(lexString(R.string.gym_week_volume), lexString(R.string.gym_kg_value, formatCount(overview.weekVolumeKg.roundToLong())), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = AwakenTheme.dimens.touchTarget)
                .clickable { mapOpen = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lexString(R.string.gym_muscle_map_open), style = AwakenTheme.type.label, color = colors.textPrimary)
            Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
        }
    }
    if (mapOpen) MuscleMapSheet(overview, onDismiss = { mapOpen = false })
}

/** The muscle map in a sheet from the "This week" card, so the routines stay near the top of Gym. */
private const val MAP_TOP_MUSCLES = 5

@Composable
private fun MuscleMapSheet(overview: GymOverview, onDismiss: () -> Unit) {
    AwakenBottomSheet(onDismiss = onDismiss, title = lexString(R.string.gym_muscle_map_title)) {
        MuscleMapContent(overview)
    }
}

/** The map, its caption, and, once a muscle is tapped, that muscle's week underneath. */
@Composable
private fun MuscleMapContent(overview: GymOverview) {
    val trained = overview.weekMuscleLoad.filterValues { it > 0.0 }.keys.sortedBy { it.ordinal }
    val names = trained.map { lexString(it.nameRes()) }
    var opened by rememberSaveable { mutableStateOf<Muscle?>(null) }
    BodyMap(
        intensity = loadIntensity(overview.weekMuscleLoad),
        description = if (names.isEmpty()) {
            lexString(R.string.cd_gym_week_map_empty)
        } else {
            lexString(R.string.cd_gym_week_map, names.joinToString())
        },
        onMuscleClick = { opened = it },
    )
    Text(lexString(R.string.gym_week_map_caption), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    val busiest = overview.weekMuscleLoad.filterValues { it > 0.0 }.entries.sortedByDescending { it.value }.take(MAP_TOP_MUSCLES)
    if (busiest.isNotEmpty()) {
        DetailRows(busiest.map { (muscle, sets) -> lexString(muscle.nameRes()) to lexPlural(R.plurals.gym_sets_count, sets.roundToInt(), sets.roundToInt()) })
    }
    opened?.let { muscle -> MuscleDetail(muscle, overview) }
}

/** A muscle's week (DESIGN_SYSTEM §3, Gym): sets and volume since Monday, for every catalog group its region covers. */
@Composable
private fun MuscleDetail(muscle: Muscle, overview: GymOverview) {
    val muscles = BodyGeometry.regionMuscles(muscle)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        muscles.forEach { m ->
            val sets = overview.weekMuscleLoad[m] ?: 0.0
            AwakenCard(reveal = false, contentPadding = PaddingValues(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                Text(lexString(m.nameRes()), style = AwakenTheme.type.windowTitle, color = AwakenTheme.colors.textPrimary)
                if (sets <= 0.0) {
                    Text(lexString(R.string.gym_muscle_sheet_empty), style = AwakenTheme.type.body, color = AwakenTheme.colors.textSecondary)
                } else {
                    ValueRow(lexString(R.string.gym_muscle_sheet_sets), formatNumber(sets))
                    ValueRow(
                        lexString(R.string.gym_muscle_sheet_volume),
                        lexString(R.string.gym_kg_value, formatCount((overview.weekMuscleVolume[m] ?: 0.0).roundToLong())),
                    )
                }
            }
        }
        Text(lexString(R.string.gym_muscle_sheet_note), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

/** Weekly sets per muscle → the heatmap alpha steps via the engine's bands (GD §13.2). */
internal fun loadIntensity(load: Map<Muscle, Double>): Map<Muscle, Float> =
    load.mapValues { (_, sets) -> TrainingMath.band(sets) }
        .filterValues { it > 0 }
        .mapValues { (_, band) -> heatAlpha(band) }

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = AwakenTheme.type.metricL, color = AwakenTheme.colors.textPrimary)
        Text(label, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

@Composable
private fun RecordsSection(records: List<PersonalRecord>, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(title = lexString(R.string.gym_window_records))
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            if (records.isEmpty()) Text(lexString(R.string.gym_records_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
            records.take(MAX_RECORDS).forEach { record ->
                ListRow(
                    title = record.name,
                    secondary = lexString(R.string.gym_record_line, formatNumber(record.weightKg), record.reps, formatNumber(record.estimatedMaxKg.roundTo1())),
                    onClick = { onEvent(GymEvent.OpenHistory(record.exerciseId, record.name)) },
                )
            }
        }
    }
}

@Composable
private fun HistorySection(history: List<WorkoutSummary>, onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(title = lexString(R.string.gym_window_history))
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            if (history.isEmpty()) Text(lexString(R.string.gym_history_empty), style = AwakenTheme.type.body, color = colors.textSecondary)
            history.take(MAX_HISTORY).forEach { workout ->
                val date = formatDate(workout.gameDate)
                ListRow(
                    title = workout.name.ifBlank { lexString(R.string.gym_workout_default_name) },
                    secondary = lexString(
                        R.string.gym_history_meta,
                        date,
                        workout.duration.toMinutes().toInt(),
                        lexPlural(R.plurals.gym_sets_count, workout.sets, workout.sets),
                        formatCount(workout.volumeKg.roundToLong()),
                    ),
                    trailing = {
                        IconButton(onClick = { onEvent(GymEvent.DeleteWorkout(workout.workoutId)) }) {
                            Icon(AwakenGlyphs.Close, contentDescription = lexString(R.string.gym_history_delete, date), tint = colors.textSecondary)
                        }
                    },
                )
            }
        }
    }
}

/** What used to hide inside a workout or float on the page: the library, the plate calculator and Gym settings. */
@Composable
private fun MoreSection(onEvent: (GymEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(title = lexString(R.string.gym_window_more))
        AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero, modifier = Modifier.fillMaxWidth()) {
            val chevron: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                Icon(AwakenGlyphs.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(AwakenTheme.dimens.iconSmall))
            }
            ListRow(
                title = lexString(R.string.gym_strength_open),
                secondary = lexString(R.string.gym_strength_open_note),
                onClick = { onEvent(GymEvent.OpenStrength) },
                trailing = chevron,
            )
            ListRow(
                title = lexString(R.string.gym_more_exercises),
                secondary = lexString(R.string.gym_more_exercises_note),
                onClick = { onEvent(GymEvent.OpenBrowse) },
                trailing = chevron,
            )
            ListRow(
                title = lexString(R.string.gym_plates_title),
                secondary = lexString(R.string.gym_more_plates_note),
                onClick = { onEvent(GymEvent.OpenPlates(null)) },
                trailing = chevron,
            )
            ListRow(
                title = lexString(R.string.gym_settings_open),
                secondary = lexString(R.string.gym_more_settings_note),
                onClick = { onEvent(GymEvent.OpenGymSettings) },
                trailing = chevron,
            )
        }
    }
}

// Session in progress

/** "Try 52.5 kg × 8": what to aim for next (GD §16.6). The last session is shown on its own line, so it is not repeated here. */
@Composable
internal fun tryText(ui: HintUi): String = when (val hint = ui.hint) {
    EngineHint.AddWeightOrVariation -> lexString(R.string.gym_hint_add_load)
    is EngineHint.Suggest -> {
        val reps = if (hint.reps.distinct().size == 1) hint.reps.first().toString() else hint.reps.joinToString(", ")
        if (hint.weightKg <= 0.0) lexString(R.string.gym_try_bodyweight, reps) else lexString(R.string.gym_try_weighted, formatNumber(hint.weightKg), reps)
    }
}

/** The finished session: time, sets, volume and the exercises, until dismissed. */
@Composable
private fun SessionSummary(summary: WorkoutSummary, records: List<ExerciseRecords>, xp: GymXpResult, onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.fillMaxWidth()) {
        Stat(lexString(R.string.gym_summary_time), formatElapsed(summary.duration), Modifier.weight(1f))
        Stat(lexString(R.string.gym_summary_sets), summary.sets.toString(), Modifier.weight(1f))
        Stat(lexString(R.string.gym_summary_volume), formatCount(summary.volumeKg.roundToLong()), Modifier.weight(1f))
    }
    Text(summary.exerciseNames.joinToString(" · "), style = AwakenTheme.type.body, color = colors.textSecondary)
    if (records.isNotEmpty()) {
        SectionTitle(lexString(R.string.gym_summary_records_title))
        AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
            records.forEach { exercise ->
                exercise.events.forEach { event -> Text(recordLine(exercise.name, event), style = AwakenTheme.type.body, color = colors.textSecondary) }
            }
        }
    }
    SectionTitle(lexString(R.string.gym_summary_reward))
    DetailRows(
        listOfNotNull(
            if (xp.fullSets > 0) lexString(R.string.gym_summary_full_sets) to xp.fullSets.toString() else null,
            if (xp.lightSets > 0) lexString(R.string.gym_summary_light_sets) to xp.lightSets.toString() else null,
            lexString(R.string.gym_summary_total) to lexString(R.string.gym_summary_total_value, formatCount(xp.xp)),
        ),
    )
    if (xp.xp == 0 && !xp.capped) Text(lexString(R.string.gym_summary_xp_none), style = AwakenTheme.type.caption, color = colors.textSecondary)
    if (xp.capped) Text(lexString(R.string.gym_summary_xp_capped), style = AwakenTheme.type.caption, color = colors.textSecondary)
    if (xp.unconfirmedSets > 0) Text(lexPlural(R.plurals.gym_summary_unconfirmed, xp.unconfirmedSets, xp.unconfirmedSets), style = AwakenTheme.type.caption, color = colors.textSecondary)
    PrimaryButton(text = lexString(R.string.gym_summary_done), onClick = { onEvent(GymEvent.DismissSummary) }, modifier = Modifier.fillMaxWidth())
    QuietButton(text = lexString(R.string.gym_save_routine), onClick = { onEvent(GymEvent.AskSaveRoutine) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun RoutineNameDialog(onEvent: (GymEvent) -> Unit) {
    val colors = AwakenTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onEvent(GymEvent.DismissSaveRoutine) },
        containerColor = colors.surfaceOverlay,
        title = { Text(lexString(R.string.gym_routine_name_title), style = AwakenTheme.type.title, color = colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = { Text(lexString(R.string.gym_routine_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = awakenTextFieldColors(),
            )
        },
        confirmButton = { QuietButton(text = lexString(R.string.action_save), onClick = { onEvent(GymEvent.SaveRoutine(name)) }, enabled = name.isNotBlank()) },
        dismissButton = { QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(GymEvent.DismissSaveRoutine) }) },
    )
}

// Formatting

internal fun formatElapsed(duration: Duration): String {
    val seconds = duration.seconds
    val h = seconds / 3_600
    val m = (seconds % 3_600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun Double.roundTo1(): Double = Math.round(this * 10.0) / 10.0

@Composable
internal fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(date)

private const val MAX_RECORDS = 8
private const val MAX_HISTORY = 15
private const val MAX_NAME = 60

// Previews

internal fun previewGymState(active: Boolean = false, home: GymHomeUi? = previewHome()): GymUiState {
    val start = Instant.parse("2026-10-27T07:00:00Z")
    val workout = WorkoutEntity("w1", "Push day", LocalDate.of(2026, 10, 27), start, mode = WorkoutEntity.MODE_COACHED)
    fun set(i: Int, kg: Double, reps: Int, done: Boolean, pos: Int = 0, name: String = "Barbell Bench Press") = WorkoutSetEntity(
        id = "s$pos$i", workoutId = "w1", exerciseId = "e$pos", exerciseName = name, primaryMuscles = setOf("chest"),
        secondaryMuscles = setOf("triceps"), position = pos, setIndex = i, weightKg = kg, reps = reps, done = done,
        restSec = if (pos == 0) 90 else null,
    )
    return GymUiState(
        loading = false,
        home = home,
        now = start.plusSeconds(1_745),
        active = if (active) {
            ActiveWorkout(
                workout.copy(restEndsAt = start.plusSeconds(1_745 + 52), restTotalSec = 90),
                listOf(
                    WorkoutExercise(0, "e0", "Barbell Bench Press", listOf(set(0, 60.0, 10, true), set(1, 70.0, 8, true), set(2, 70.0, 8, false))),
                    WorkoutExercise(1, "e1", "Dips - Triceps Version", listOf(set(0, 0.0, 12, false, pos = 1, name = "Dips - Triceps Version"))),
                ),
            )
        } else {
            null
        },
        previous = mapOf("e0" to listOf(set(0, 57.5, 10, true), set(1, 67.5, 8, true))),
        overview = GymOverview(
            weekStart = LocalDate.of(2026, 10, 26),
            weekWorkouts = 3,
            weekSets = 41,
            weekVolumeKg = 12_480.0,
            weekMuscleLoad = mapOf(Muscle.CHEST to 12.0, Muscle.TRICEPS to 8.0, Muscle.SHOULDERS to 6.0, Muscle.QUADRICEPS to 16.0, Muscle.LATS to 4.0),
            history = listOf(WorkoutSummary("w0", "Legs", LocalDate.of(2026, 10, 26), Duration.ofMinutes(52), 18, 6_240.0, listOf("Barbell Squat", "Leg Press"))),
            records = listOf(PersonalRecord("e0", "Barbell Bench Press", 80.0, 5, 93.3, LocalDate.of(2026, 10, 24))),
            routines = listOf(RoutineSummary("r1", "Push day", listOf("Barbell Bench Press", "Dips - Triceps Version"), 7)),
            trainingDay = true,
        ),
    )
}

@PhonePreviews
@Composable
private fun GymHomePreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(previewGymState(), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymMuscleMapPreview() {
    val overview = previewGymState().overview
    AwakenTheme(reduceMotion = true) {
        if (overview != null) Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) { MuscleMapContent(overview) }
    }
}

@PhonePreviews
@Composable
private fun GymSummaryPreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(previewGymState().copy(summaryId = "w0"), onEvent = {}) }
}

/** One line of the finish summary for a record the session broke (GD §16.7). */
@Composable
private fun recordLine(exercise: String, event: RecordEvent): String = when (event.type) {
    RecordType.HEAVIEST_WEIGHT -> lexString(R.string.gym_record_heaviest, exercise, formatNumber(event.value))
    RecordType.BEST_E1RM -> lexString(R.string.gym_record_e1rm, exercise, formatNumber(event.value.roundTo1()))
    RecordType.MOST_REPS_AT_WEIGHT -> lexString(R.string.gym_record_reps, exercise, formatNumber(event.weightKg ?: 0.0), event.value.toInt())
    RecordType.BEST_SESSION_VOLUME -> lexString(R.string.gym_record_volume, exercise, formatCount(event.value.roundToLong()))
}

@PhonePreviews
@Composable
private fun GymSummaryXpPreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(previewGymState().copy(summaryId = "w0", summaryXp = GymXpResult(35, 6, 2, 0, false)), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymSummaryXpCappedPreview() {
    AwakenTheme(reduceMotion = true) { GymScreen(previewGymState().copy(summaryId = "w0", summaryXp = GymXpResult(20, 4, 0, 1, true)), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun GymSummaryRecordsPreview() {
    AwakenTheme(reduceMotion = true) {
        GymScreen(
            previewGymState().copy(
                summaryId = "w0",
                summaryRecords = listOf(
                    ExerciseRecords(
                        "e0", "Barbell Bench Press",
                        listOf(RecordEvent(RecordType.HEAVIEST_WEIGHT, 82.5), RecordEvent(RecordType.MOST_REPS_AT_WEIGHT, 9.0, 80.0)),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
