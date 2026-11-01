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
import com.assismohanty.awaken.core.domain.toDays
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.model.Difficulty
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
    /** Planned by a campaign: read-only, ends with the campaign (GD §6.8). */
    val campaign: Boolean = false,
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
    @StringRes val error: Int? = null,
    val saving: Boolean = false,
) {
    /** GD §4.3: self-reported habits go up to Normal. */
    val difficulties: List<Difficulty>
        get() = Difficulty.entries.filter { it <= QuestRules.maxCustomDifficulty(VerificationSource.SELF) }
}

sealed interface HabitSheet {
    data object None : HabitSheet
    data object Overview : HabitSheet
    data class Editor(val editor: HabitEditorState) : HabitSheet

    /** Long-press on Today or in the list: Archive or Delete. [fromList] returns to the list afterwards. */
    data class Options(val habit: HabitItem, val fromList: Boolean) : HabitSheet

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
    data object Save : HabitsEvent

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
            HabitsEvent.Save -> save()
            HabitsEvent.Archive -> confirmFromEditor(HabitRemoveAction.ARCHIVE)
            HabitsEvent.Delete -> confirmFromEditor(HabitRemoveAction.DELETE)
            is HabitsEvent.Options -> uiState.value.habits.firstOrNull { it.id == event.habitId }?.let {
                sheet.value = HabitSheet.Options(it, event.fromList)
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
                SaveHabitResult.CampaignHabit -> R.string.habit_campaign_locked
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

internal fun HabitEntity.toItem() = HabitItem(id, title, stat, difficulty, daysOfWeekMask.toDays(), target, unit, timeOfDay, campaign = campaignId != null)

private fun HabitItem.toEditor() = HabitEditorState(
    habitId = id,
    title = title,
    stat = stat,
    difficulty = difficulty,
    days = days,
    target = target.toString(),
    unit = unit,
    timeOfDay = timeOfDay,
)
