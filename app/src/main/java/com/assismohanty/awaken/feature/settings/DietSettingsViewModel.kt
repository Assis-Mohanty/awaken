package com.assismohanty.awaken.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.repo.MealReminderStore
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.domain.DietReminderScheduling
import com.assismohanty.awaken.core.domain.FoodLibrary
import com.assismohanty.awaken.core.domain.MealSlot
import com.assismohanty.awaken.core.work.NotificationAccess
import com.assismohanty.awaken.engine.body.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DietSettingsUiState(
    val slots: List<MealSlot> = emptyList(),
    /** Meal id to reminder minutes after midnight. */
    val reminders: Map<String, Int> = emptyMap(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** A reminder is set but the system blocks notifications, so it can not reach the hunter. */
    val notificationsBlocked: Boolean = false,
)

sealed interface DietSettingsEvent {
    data class SetReminder(val mealId: String, val minutes: Int?) : DietSettingsEvent
    data class SetUnit(val unit: WeightUnit) : DietSettingsEvent
    data object Resumed : DietSettingsEvent
}

/** Settings, Diet: meal reminders, the weight unit and where the food data comes from (GD 20.4). */
@HiltViewModel
class DietSettingsViewModel @Inject constructor(
    foodLibrary: FoodLibrary,
    private val store: MealReminderStore,
    private val uiPrefs: UiPrefsRepository,
    private val scheduler: DietReminderScheduling,
    private val access: NotificationAccess,
) : ViewModel() {

    private val blocked = MutableStateFlow(!access.isGranted())

    val uiState: StateFlow<DietSettingsUiState> = combine(
        foodLibrary.observeMealSlots(),
        store.reminders,
        uiPrefs.prefs.map { it.weightUnit },
        blocked,
    ) { slots, reminders, unit, isBlocked ->
        DietSettingsUiState(slots, reminders, unit, notificationsBlocked = isBlocked && reminders.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DietSettingsUiState())

    fun onEvent(event: DietSettingsEvent) {
        when (event) {
            is DietSettingsEvent.SetReminder -> viewModelScope.launch {
                if (store.set(event.mealId, event.minutes)) scheduler.rearmMeals()
            }
            is DietSettingsEvent.SetUnit -> viewModelScope.launch { uiPrefs.setWeightUnit(event.unit) }
            DietSettingsEvent.Resumed -> blocked.update { !access.isGranted() }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
