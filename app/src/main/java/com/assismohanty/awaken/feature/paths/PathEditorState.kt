package com.assismohanty.awaken.feature.paths

import androidx.compose.runtime.Immutable
import com.assismohanty.awaken.core.domain.PathSummary
import com.assismohanty.awaken.core.domain.toDays
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathInput
import com.assismohanty.awaken.engine.path.PathInputProblem
import com.assismohanty.awaken.engine.path.PathRules
import com.assismohanty.awaken.engine.path.StatSuggester
import java.time.DayOfWeek

enum class EditorFailure { GENERIC, LIMIT, TODAY, REPLAN }

/**
 * The create / edit sheet (DS §4.8). The primary stat is pre-selected by `StatSuggester` (a keyword table in `:engine`,
 * not AI) until the hunter picks one. Everything validated here is validated again by `PathRules` in the use case.
 */
@Immutable
data class PathEditorState(
    val editingPathId: String? = null,
    val goal: String = "",
    val currentLevel: String = "",
    val notes: String = "",
    val chosenPrimary: Stat? = null,
    val secondary: Stat? = null,
    val days: Set<DayOfWeek> = DEFAULT_DAYS,
    val questsPerDay: Int = 1,
    val weeklyMinutes: Int = DEFAULT_MINUTES,
    val replan: Boolean = false,
    /** False once this Path was re-planned this ISO week (GD §6.6.2). */
    val canReplan: Boolean = true,
    val showErrors: Boolean = false,
    val submitting: Boolean = false,
    val failure: EditorFailure? = null,
) {
    val isEdit: Boolean get() = editingPathId != null
    val suggestedStat: Stat? get() = StatSuggester.suggest(goal)
    val primary: Stat? get() = chosenPrimary ?: suggestedStat
    val minutesPerActiveDay: Int get() = weeklyMinutes / days.size.coerceAtLeast(1)

    /** The engine's input for the Path as typed, or null while no primary stat is chosen or suggested. */
    fun input(): PathInput? = primary?.let { primary ->
        PathInput(
            goal = goal.trim(),
            currentLevel = currentLevel.trim(),
            notes = notes.trim(),
            primaryStat = primary,
            secondaryStat = secondary?.takeIf { it != primary },
            activeDays = days,
            questsPerDay = questsPerDay,
            weeklyMinutes = weeklyMinutes,
        )
    }

    val primaryMissing: Boolean get() = primary == null

    val problems: List<PathInputProblem> get() = input()?.let(PathRules::problems).orEmpty()

    val valid: Boolean get() = !primaryMissing && problems.isEmpty()

    companion object {
        val DEFAULT_DAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        const val DEFAULT_MINUTES = 180
        const val MINUTES_STEP = 15

        fun forEdit(summary: PathSummary, canReplan: Boolean): PathEditorState = with(summary.path) {
            PathEditorState(
                editingPathId = id,
                goal = goal,
                currentLevel = currentLevel,
                notes = notes,
                chosenPrimary = primaryStat,
                secondary = secondaryStat,
                days = activeDaysMask.toDays(),
                questsPerDay = questsPerDay,
                weeklyMinutes = weeklyMinutes,
                canReplan = canReplan,
            )
        }
    }
}

sealed interface PathEditorEvent {
    data class GoalChanged(val value: String) : PathEditorEvent
    data class LevelChanged(val value: String) : PathEditorEvent
    data class NotesChanged(val value: String) : PathEditorEvent
    data class PrimaryPicked(val stat: Stat) : PathEditorEvent
    data class SecondaryPicked(val stat: Stat) : PathEditorEvent
    data class DayToggled(val day: DayOfWeek) : PathEditorEvent
    data class QuestsPerDayPicked(val count: Int) : PathEditorEvent
    data class MinutesChanged(val minutes: Int) : PathEditorEvent
    data object ReplanToggled : PathEditorEvent
    data object Save : PathEditorEvent
    data object Dismiss : PathEditorEvent
}

/** Pure reducer for the field events (Save and Dismiss are handled by the owning ViewModel). */
fun PathEditorState.reduce(event: PathEditorEvent): PathEditorState = when (event) {
    is PathEditorEvent.GoalChanged -> copy(goal = event.value.take(PathRules.GOAL_LENGTH.last), failure = null)
    is PathEditorEvent.LevelChanged -> copy(currentLevel = event.value.take(PathRules.LEVEL_LENGTH.last), failure = null)
    is PathEditorEvent.NotesChanged -> copy(notes = event.value.take(PathRules.NOTES_LENGTH.last), failure = null)
    is PathEditorEvent.PrimaryPicked ->
        copy(chosenPrimary = event.stat, secondary = secondary?.takeIf { it != event.stat }, failure = null)
    // Picking the chosen second stat again clears it; it can never equal the primary.
    is PathEditorEvent.SecondaryPicked ->
        copy(secondary = if (secondary == event.stat || event.stat == primary) null else event.stat, failure = null)
    is PathEditorEvent.DayToggled -> copy(days = if (event.day in days) days - event.day else days + event.day, failure = null)
    is PathEditorEvent.QuestsPerDayPicked -> copy(questsPerDay = event.count.coerceIn(PathRules.QUESTS_PER_DAY), failure = null)
    is PathEditorEvent.MinutesChanged ->
        copy(weeklyMinutes = event.minutes.coerceIn(PathRules.WEEKLY_MINUTES.first, PathRules.WEEKLY_MINUTES.last), failure = null)
    PathEditorEvent.ReplanToggled -> if (canReplan) copy(replan = !replan) else this
    PathEditorEvent.Save, PathEditorEvent.Dismiss -> this
}
