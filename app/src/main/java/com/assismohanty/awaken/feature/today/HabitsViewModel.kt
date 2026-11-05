package com.assismohanty.awaken.feature.today

import com.assismohanty.awaken.engine.model.TimeOfDay
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.domain.HabitDraft
import com.assismohanty.awaken.core.domain.HabitRemoval
import com.assismohanty.awaken.core.domain.ObserveHabitsUseCase
import com.assismohanty.awaken.core.domain.RemoveHabitResult
import com.assismohanty.awaken.core.domain.RemoveHabitUseCase
import com.assismohanty.awaken.core.domain.RestoreHabitUseCase
import com.assismohanty.awaken.core.domain.SaveHabitResult
import com.assismohanty.awaken.core.domain.SaveHabitUseCase
import com.assismohanty.awaken.core.domain.SkipHabitUseCase
import com.assismohanty.awaken.core.domain.SkipState
import com.assismohanty.awaken.core.domain.toDays
import com.assismohanty.awaken.engine.HabitProblem
import com.assismohanty.awaken.engine.HabitRules
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class HabitItem(
    val id: String,
    val title: String,
    val stat: Stat,
    val difficulty: Difficulty,
    val days: Set<DayOfWeek>,
    val target: Int,
    val unit: String,
    val timeOfDay: TimeOfDay? = null,
    /** Planned by a program: read-only, ends with the program (GD §16.15). */
    val locked: Boolean = false,
    val type: HabitType = HabitType.CHECK,
    val frequency: HabitFrequency = HabitFrequency.DAYS_OF_WEEK,
    val timesPerWeek: Int? = null,
    val reminderMinutes: Int? = null,
)

/** What the hunter can do to a habit from its options sheet. */
enum class HabitRemoveAction { ARCHIVE, DELETE }

/** A just-finished archive or delete the snackbar offers to undo; [token] tells snackbars apart. */
data class HabitUndo(val token: Long, val title: String, val action: HabitRemoveAction)

data class HabitEditorState(
    /** null = new habit. */
    val habitId: String? = null,
    val title: String = "",
    val stat: Stat = Stat.WIL,
    val difficulty: Difficulty = Difficulty.EASY,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** Raw text so the field can be empty while typing; validated on save. */
    val target: String = "1",
    val unit: String = "",
    /** GD §14.5: where this habit shows on Today's timeline; null = Anytime. */
    val timeOfDay: TimeOfDay? = null,
    /** GD §16.1: what the habit asks. For Timed the [target] field is minutes. */
    val type: HabitType = HabitType.CHECK,
    val frequency: HabitFrequency = HabitFrequency.DAYS_OF_WEEK,
    /** The times-per-week stepper's value (1..6); only used with [HabitFrequency.TIMES_PER_WEEK]. */
    val timesPerWeek: Int = DEFAULT_TIMES_PER_WEEK,
    /** GD §16.2: minutes after local midnight; null = no reminder. */
    val reminderMinutes: Int? = null,
    val reminderPickerOpen: Boolean = false,
    @StringRes val error: Int? = null,
    val saving: Boolean = false,
) {
    /** GD §4.3: self-reported habits go up to Normal. */
    val difficulties: List<Difficulty>
        get() = Difficulty.entries.filter { it <= QuestRules.maxCustomDifficulty(VerificationSource.SELF) }

    /** A Timed habit's difficulty comes from its minutes (GD §16.1), shown read-only. */
    val timedDifficulty: Difficulty? get() = target.toIntOrNull()?.takeIf { type == HabitType.TIMED && it > 0 }?.let(HabitRules::timedDifficulty)

    companion object {
        const val DEFAULT_TIMES_PER_WEEK = 3
        const val DEFAULT_TIMED_MINUTES = 25
        const val DEFAULT_COUNT_TARGET = 8
        const val DEFAULT_REMINDER_MINUTES = 9 * 60
    }
}

sealed interface HabitSheet {
    data object None : HabitSheet
    data object Overview : HabitSheet
    data class Editor(val editor: HabitEditorState) : HabitSheet

    /** Long-press on Today or in the list: Archive or Delete. [fromList] returns to the list afterwards. */
    data class Options(val habit: HabitItem, val fromList: Boolean, val skip: SkipState = SkipState.NONE) : HabitSheet

    data class Confirm(val habit: HabitItem, val action: HabitRemoveAction, val fromList: Boolean) : HabitSheet
}

data class HabitsUiState(
    val habits: List<HabitItem> = emptyList(),
    val sheet: HabitSheet = HabitSheet.None,
    val undo: HabitUndo? = null,
) {
    val max: Int get() = QuestRules.MAX_ACTIVE_CUSTOM
    val canAdd: Boolean get() = QuestRules.canAddCustom(habits.size)
}

sealed interface HabitsEvent {
    data object OpenList : HabitsEvent
    data object Add : HabitsEvent
    data class Edit(val habitId: String) : HabitsEvent
    data object Dismiss : HabitsEvent
    data class TitleChanged(val title: String) : HabitsEvent
    data class StatChanged(val stat: Stat) : HabitsEvent
    data class DifficultyChanged(val difficulty: Difficulty) : HabitsEvent
    data class DayToggled(val day: DayOfWeek) : HabitsEvent
    data class TargetChanged(val target: String) : HabitsEvent
    data class UnitChanged(val unit: String) : HabitsEvent
    data class BlockChanged(val timeOfDay: TimeOfDay?) : HabitsEvent
    data class TypeChanged(val type: HabitType) : HabitsEvent
    data class FrequencyChanged(val frequency: HabitFrequency) : HabitsEvent
    data class TimesPerWeekChanged(val times: Int) : HabitsEvent
    data object OpenReminderPicker : HabitsEvent
    data object DismissReminderPicker : HabitsEvent
    data class ReminderChanged(val minutes: Int?) : HabitsEvent
    data object Save : HabitsEvent

    /** Options sheet: skip today / take the skip back (GD §16.2). */
    data object SkipToday : HabitsEvent
    data object UndoSkip : HabitsEvent

    /** The editor's Archive / Delete buttons: confirm first. */
    data object Archive : HabitsEvent
    data object Delete : HabitsEvent

    /** Long-press a habit (on Today by its habit id, or in the list). */
    data class Options(val habitId: String, val fromList: Boolean = false) : HabitsEvent
    data class RequestRemove(val action: HabitRemoveAction) : HabitsEvent
    data object ConfirmRemove : HabitsEvent
    data class Undo(val token: Long) : HabitsEvent
    data class UndoHandled(val token: Long) : HabitsEvent
}

/** Custom habits (PRD F4, GD §6.1): add, edit, archive or delete with undo; max 8 active. */
@HiltViewModel
class HabitsViewModel @Inject constructor(
    observeHabits: ObserveHabitsUseCase,
    private val saveHabit: SaveHabitUseCase,
    private val removeHabit: RemoveHabitUseCase,
    private val restoreHabit: RestoreHabitUseCase,
    private val skipHabit: SkipHabitUseCase,
) : ViewModel() {

    private val sheet = MutableStateFlow<HabitSheet>(HabitSheet.None)
    private val undo = MutableStateFlow<HabitUndo?>(null)

    /** What each offered undo would put back; kept here, never in UI state. */
    private val removals = mutableMapOf<Long, HabitRemoval>()
    private var nextToken = 0L

    val uiState: StateFlow<HabitsUiState> = combine(observeHabits(), sheet, undo) { habits, sheet, undo ->
        HabitsUiState(habits = habits.map { it.toItem() }, sheet = sheet, undo = undo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HabitsUiState())

    fun onEvent(event: HabitsEvent) {
        when (event) {
            HabitsEvent.OpenList -> sheet.value = HabitSheet.Overview
            HabitsEvent.Add -> if (uiState.value.canAdd) sheet.value = HabitSheet.Editor(HabitEditorState())
            is HabitsEvent.Edit -> uiState.value.habits.firstOrNull { it.id == event.habitId }?.let { sheet.value = HabitSheet.Editor(it.toEditor()) }
            HabitsEvent.Dismiss -> sheet.value = HabitSheet.None
            is HabitsEvent.TitleChanged -> edit { it.copy(title = event.title, error = null) }
            is HabitsEvent.StatChanged -> edit { it.copy(stat = event.stat) }
            is HabitsEvent.DifficultyChanged -> edit { it.copy(difficulty = event.difficulty) }
            is HabitsEvent.DayToggled -> edit { it.copy(days = if (event.day in it.days) it.days - event.day else it.days + event.day, error = null) }
            is HabitsEvent.TargetChanged -> edit { it.copy(target = event.target.filter(Char::isDigit).take(TARGET_MAX_DIGITS), error = null) }
            is HabitsEvent.UnitChanged -> edit { it.copy(unit = event.unit.take(UNIT_MAX_LENGTH)) }
            is HabitsEvent.BlockChanged -> edit { it.copy(timeOfDay = event.timeOfDay) }
            is HabitsEvent.TypeChanged -> edit { it.withType(event.type) }
            is HabitsEvent.FrequencyChanged -> edit { it.copy(frequency = event.frequency, error = null) }
            is HabitsEvent.TimesPerWeekChanged -> edit { it.copy(timesPerWeek = event.times.coerceIn(HabitRules.WEEKLY_MIN, HabitRules.WEEKLY_MAX), error = null) }
            HabitsEvent.OpenReminderPicker -> edit { it.copy(reminderPickerOpen = true) }
            HabitsEvent.DismissReminderPicker -> edit { it.copy(reminderPickerOpen = false) }
            is HabitsEvent.ReminderChanged -> edit { it.copy(reminderMinutes = event.minutes, reminderPickerOpen = false, error = null) }
            HabitsEvent.SkipToday -> skip(undo = false)
            HabitsEvent.UndoSkip -> skip(undo = true)
            HabitsEvent.Save -> save()
            HabitsEvent.Archive -> confirmFromEditor(HabitRemoveAction.ARCHIVE)
            HabitsEvent.Delete -> confirmFromEditor(HabitRemoveAction.DELETE)
            is HabitsEvent.Options -> uiState.value.habits.firstOrNull { it.id == event.habitId }?.let { habit ->
                viewModelScope.launch {
                    // The skip offer is only about today's instance, so it is not asked from the list.
                    val skip = if (event.fromList) SkipState.NONE else runCatching { skipHabit.state(habit.id) }.getOrDefault(SkipState.NONE)
                    sheet.value = HabitSheet.Options(habit, event.fromList, skip)
                }
            }
            is HabitsEvent.RequestRemove -> sheet.update { current ->
                if (current is HabitSheet.Options) HabitSheet.Confirm(current.habit, event.action, current.fromList) else current
            }
            HabitsEvent.ConfirmRemove -> remove()
            is HabitsEvent.Undo -> undo(event.token)
            is HabitsEvent.UndoHandled -> {
                removals.remove(event.token)
                undo.update { if (it?.token == event.token) null else it }
            }
        }
    }

    private fun skip(undo: Boolean) {
        val options = sheet.value as? HabitSheet.Options ?: return
        viewModelScope.launch {
            try {
                if (undo) skipHabit.undoSkip(options.habit.id) else skipHabit.skip(options.habit.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Skipping habit failed")
            }
            sheet.value = HabitSheet.None
        }
    }

    private fun confirmFromEditor(action: HabitRemoveAction) {
        val habitId = (sheet.value as? HabitSheet.Editor)?.editor?.habitId ?: return
        val habit = uiState.value.habits.firstOrNull { it.id == habitId } ?: return
        sheet.value = HabitSheet.Confirm(habit, action, fromList = true)
    }

    private fun remove() {
        val confirm = sheet.value as? HabitSheet.Confirm ?: return
        viewModelScope.launch {
            val result = try {
                when (confirm.action) {
                    HabitRemoveAction.ARCHIVE -> removeHabit.archive(confirm.habit.id)
                    HabitRemoveAction.DELETE -> removeHabit.delete(confirm.habit.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Removing habit failed")
                null
            }
            sheet.value = if (confirm.fromList) HabitSheet.Overview else HabitSheet.None
            if (result is RemoveHabitResult.Removed) {
                val token = ++nextToken
                removals[token] = result.removal
                undo.value = HabitUndo(token, confirm.habit.title, confirm.action)
            }
        }
    }

    private fun undo(token: Long) {
        val removal = removals.remove(token) ?: return
        undo.update { if (it?.token == token) null else it }
        viewModelScope.launch {
            try {
                restoreHabit.undo(removal)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Undoing habit removal failed")
            }
        }
    }

    private fun edit(transform: (HabitEditorState) -> HabitEditorState) = sheet.update { current ->
        if (current is HabitSheet.Editor) current.copy(editor = transform(current.editor)) else current
    }

    private fun save() {
        val editor = (sheet.value as? HabitSheet.Editor)?.editor ?: return
        if (editor.saving) return
        edit { it.copy(saving = true) }
        viewModelScope.launch {
            val result = try {
                saveHabit(
                    HabitDraft(
                        id = editor.habitId,
                        title = editor.title,
                        stat = editor.stat,
                        difficulty = editor.difficulty,
                        days = editor.days,
                        target = editor.target.toIntOrNull() ?: 0,
                        unit = editor.unit.trim(),
                        timeOfDay = editor.timeOfDay,
                        type = editor.type,
                        frequency = editor.frequency,
                        timesPerWeek = editor.timesPerWeek.takeIf { editor.frequency == HabitFrequency.TIMES_PER_WEEK },
                        reminderMinutes = editor.reminderMinutes,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Saving habit failed")
                null
            }
            val error = when (result) {
                is SaveHabitResult.Saved -> null
                SaveHabitResult.LimitReached -> R.string.habit_limit
                SaveHabitResult.InvalidTitle -> R.string.habit_error_title
                SaveHabitResult.InvalidDays -> R.string.habit_error_days
                SaveHabitResult.InvalidTarget -> R.string.habit_error_target
                SaveHabitResult.DifficultyTooHigh -> R.string.habit_difficulty_note
                SaveHabitResult.ProgramHabit -> R.string.habit_program_locked
                SaveHabitResult.ReminderLimit -> R.string.habit_reminder_limit
                is SaveHabitResult.InvalidHabit -> when (result.problem) {
                    HabitProblem.QUANTITY_TARGET -> R.string.habit_error_quantity
                    HabitProblem.TIMED_MINUTES -> R.string.habit_error_timed
                    HabitProblem.TIMES_PER_WEEK -> R.string.habit_error_weekly
                    HabitProblem.NEGATIVE_NEEDS_DAYS -> R.string.habit_error_avoid_days
                }
                null -> R.string.msg_error
            }
            if (error == null) sheet.value = HabitSheet.Overview else edit { it.copy(saving = false, error = error) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TARGET_MAX_DIGITS = 5
        const val UNIT_MAX_LENGTH = 16
    }
}

internal fun HabitEntity.toItem() = HabitItem(
    id, title, stat, difficulty, daysOfWeekMask.toDays(), target, unit, timeOfDay,
    locked = programId != null, type = type, frequency = frequency, timesPerWeek = timesPerWeek, reminderMinutes = reminderTime,
)

/** A new type resets what does not carry over: a count starts at 8, a timed habit at 25 minutes, an avoid habit at one a day. */
private fun HabitEditorState.withType(newType: HabitType): HabitEditorState = copy(
    type = newType,
    error = null,
    target = when (newType) {
        HabitType.CHECK -> "1"
        HabitType.QUANTITY -> HabitEditorState.DEFAULT_COUNT_TARGET.toString()
        HabitType.TIMED -> HabitEditorState.DEFAULT_TIMED_MINUTES.toString()
        HabitType.NEGATIVE -> "1"
    },
    unit = if (newType == HabitType.TIMED || newType == HabitType.NEGATIVE) "" else unit,
    // An avoid habit runs on fixed weekdays (GD §16.1).
    frequency = if (newType == HabitType.NEGATIVE) HabitFrequency.DAYS_OF_WEEK else frequency,
)

private fun HabitItem.toEditor() = HabitEditorState(
    habitId = id,
    title = title,
    stat = stat,
    difficulty = difficulty,
    days = days,
    target = target.toString(),
    unit = unit,
    timeOfDay = timeOfDay,
    type = type,
    frequency = frequency,
    timesPerWeek = timesPerWeek ?: HabitEditorState.DEFAULT_TIMES_PER_WEEK,
    reminderMinutes = reminderMinutes,
)
