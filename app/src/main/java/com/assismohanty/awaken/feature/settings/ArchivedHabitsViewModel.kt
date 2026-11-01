package com.assismohanty.awaken.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.ObserveHabitsUseCase
import com.assismohanty.awaken.core.domain.RestoreHabitResult
import com.assismohanty.awaken.core.domain.RestoreHabitUseCase
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** One archived habit in Settings. */
data class ArchivedHabit(val id: String, val title: String, val stat: Stat)

/** Settings → Archived habits: what the hunter archived, each restorable (max 8 active, GD §6.1). */
@HiltViewModel
class ArchivedHabitsViewModel @Inject constructor(
    observeHabits: ObserveHabitsUseCase,
    private val restoreHabit: RestoreHabitUseCase,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    val archived: StateFlow<List<ArchivedHabit>> = observeHabits.archived()
        .map { habits -> habits.map { ArchivedHabit(it.id, it.title, it.stat) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun restore(habitId: String) {
        val title = archived.value.firstOrNull { it.id == habitId }?.title ?: return
        viewModelScope.launch {
            val message = try {
                when (restoreHabit(habitId)) {
                    RestoreHabitResult.Restored -> UserMessage.HabitRestored(title)
                    RestoreHabitResult.LimitReached -> UserMessage.HabitLimit
                    RestoreHabitResult.NotFound -> UserMessage.Error
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Restoring habit failed")
                UserMessage.Error
            }
            completionCenter.message(message)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
