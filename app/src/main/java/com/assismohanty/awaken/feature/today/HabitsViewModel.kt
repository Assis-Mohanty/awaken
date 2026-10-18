package com.assismohanty.awaken.feature.today

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.domain.ArchiveHabitUseCase
import com.assismohanty.awaken.core.domain.HabitDraft
import com.assismohanty.awaken.core.domain.ObserveHabitsUseCase
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
)

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
}

data class HabitsUiState(
    val habits: List<HabitItem> = emptyList(),
    val sheet: HabitSheet = HabitSheet.None,
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
    data object Save : HabitsEvent
    data object Archive : HabitsEvent
}

/** Custom habits (PRD F4, GD §6.1): add, edit, archive; max 8 active. */
@HiltViewModel
class HabitsViewModel @Inject constructor(
    observeHabits: ObserveHabitsUseCase,
    private val saveHabit: SaveHabitUseCase,
    private val archiveHabit: ArchiveHabitUseCase,
) : ViewModel() {

    private val sheet = MutableStateFlow<HabitSheet>(HabitSheet.None)

    val uiState: StateFlow<HabitsUiState> = combine(observeHabits(), sheet) { habits, sheet ->
        HabitsUiState(habits = habits.map { it.toItem() }, sheet = sheet)
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
            HabitsEvent.Save -> save()
            HabitsEvent.Archive -> archive()
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
                null -> R.string.msg_error
            }
            if (error == null) sheet.value = HabitSheet.Overview else edit { it.copy(saving = false, error = error) }
        }
    }

    private fun archive() {
        val habitId = (sheet.value as? HabitSheet.Editor)?.editor?.habitId ?: return
        viewModelScope.launch {
            try {
                archiveHabit(habitId)
                sheet.value = HabitSheet.Overview
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Archiving habit failed")
                edit { it.copy(error = R.string.msg_error) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TARGET_MAX_DIGITS = 5
        const val UNIT_MAX_LENGTH = 16
    }
}

private fun HabitEntity.toItem() = HabitItem(id, title, stat, difficulty, daysOfWeekMask.toDays(), target, unit)

private fun HabitItem.toEditor() = HabitEditorState(
    habitId = id,
    title = title,
    stat = stat,
    difficulty = difficulty,
    days = days,
    target = target.toString(),
    unit = unit,
)
