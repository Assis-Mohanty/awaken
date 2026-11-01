package com.assismohanty.awaken.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.RunRolloverUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One way a debug build is faking data right now. */
sealed interface DebugOverride {
    data class ClockOffset(val days: Long) : DebugOverride
    data object SimulatedSteps : DebugOverride
    data object SimulatedUsage : DebugOverride
}

/** Feeds [DebugEntry.OverrideBanner]: which overrides are on, and one reset back to real data. */
@HiltViewModel
class DebugBannerViewModel @Inject constructor(
    private val settings: DebugSettings,
    private val rollover: RunRolloverUseCase,
) : ViewModel() {

    val overrides: StateFlow<List<DebugOverride>> =
        combine(settings.offsetDays, settings.simulateSteps, settings.simulatedUsageMinutes) { offset, steps, usage ->
            buildList {
                if (offset != 0L) add(DebugOverride.ClockOffset(offset))
                if (steps) add(DebugOverride.SimulatedSteps)
                if (usage > 0) add(DebugOverride.SimulatedUsage)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun reset() {
        viewModelScope.launch {
            settings.resetOverrides()
            rollover()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
