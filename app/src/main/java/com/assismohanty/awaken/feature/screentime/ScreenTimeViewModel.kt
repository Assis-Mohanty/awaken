package com.assismohanty.awaken.feature.screentime

import com.assismohanty.awaken.core.reelcounter.ReelWidgetData
import com.assismohanty.awaken.feature.reelcounter.ReelAppRowUi
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.stats.StatsCalculator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.AppUsageRow
import com.assismohanty.awaken.core.domain.LoadScreenTimeOverviewUseCase
import com.assismohanty.awaken.core.domain.ScreenTimeOverview
import com.assismohanty.awaken.core.domain.SetAppRuleResult
import com.assismohanty.awaken.core.domain.SetAppRuleUseCase
import com.assismohanty.awaken.core.domain.SetScreenTimeEnabledUseCase
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.ScreenTimeRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** The category/limit sheet for one app. Saving writes tomorrow's rule (GD §6.7). */
data class AppRuleEditor(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    /** Null = no limit. Only meaningful for DISTRACTING. */
    val limitMinutes: Int?,
)

data class ScreenTimeUiState(
    val loading: Boolean = true,
    val overview: ScreenTimeOverview? = null,
    /** Null shows every category. */
    val filter: AppCategory? = null,
    /** Apps with no use today are folded away until asked for. */
    val showUnused: Boolean = false,
    val editor: AppRuleEditor? = null,
    val failed: Boolean = false,
    /** Reel counter's totals for today (every app, even at zero); null while that feature is switched off. */
    val reels: List<ReelAppRowUi>? = null,
) {
    val visibleApps: List<AppUsageRow>
        get() = overview?.apps.orEmpty()
            .filter { filter == null || it.category == filter }
            .filter { showUnused || it.usedMinutes > 0 || it.limitMinutes != null }
    val hiddenCount: Int
        get() = overview?.apps.orEmpty().count { (filter == null || it.category == filter) } - visibleApps.size
}

sealed interface ScreenTimeEvent {
    data object Refresh : ScreenTimeEvent
    data class SetDiscipline(val enabled: Boolean) : ScreenTimeEvent
    data class Filter(val category: AppCategory?) : ScreenTimeEvent
    data object ToggleUnused : ScreenTimeEvent
    data class OpenApp(val packageName: String) : ScreenTimeEvent
    data class EditCategory(val category: AppCategory) : ScreenTimeEvent
    data class StepLimit(val deltaMinutes: Int) : ScreenTimeEvent
    data object ToggleNoLimit : ScreenTimeEvent
    data object SaveEditor : ScreenTimeEvent
    data object DismissEditor : ScreenTimeEvent
}

@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val loadOverview: LoadScreenTimeOverviewUseCase,
    private val setEnabled: SetScreenTimeEnabledUseCase,
    private val setAppRule: SetAppRuleUseCase,
    reelData: ReelWidgetData,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreenTimeUiState())
    val uiState: StateFlow<ScreenTimeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reelData.observeRows().collect { rows ->
                _uiState.update { state ->
                    state.copy(
                        reels = rows?.let { totals ->
                            ShortVideoApp.entries.map { app ->
                                val row = totals.firstOrNull { it.app == app }
                                ReelAppRowUi(app, row?.count ?: 0, StatsCalculator.minutes(row?.seconds ?: 0))
                            }
                        },
                    )
                }
            }
        }
    }

    fun onEvent(event: ScreenTimeEvent) {
        when (event) {
            ScreenTimeEvent.Refresh -> refresh()
            is ScreenTimeEvent.SetDiscipline -> launchThenRefresh { setEnabled(event.enabled) }
            is ScreenTimeEvent.Filter -> _uiState.update { it.copy(filter = event.category) }
            ScreenTimeEvent.ToggleUnused -> _uiState.update { it.copy(showUnused = !it.showUnused) }
            is ScreenTimeEvent.OpenApp -> openEditor(event.packageName)
            is ScreenTimeEvent.EditCategory -> updateEditor { editor ->
                val limit = when {
                    event.category != AppCategory.DISTRACTING -> null
                    editor.category == AppCategory.DISTRACTING -> editor.limitMinutes
                    else -> ScreenTimeRules.DEFAULT_LIMIT_MINUTES
                }
                editor.copy(category = event.category, limitMinutes = limit)
            }
            is ScreenTimeEvent.StepLimit -> updateEditor { editor ->
                val current = editor.limitMinutes ?: ScreenTimeRules.DEFAULT_LIMIT_MINUTES
                editor.copy(
                    limitMinutes = (current + event.deltaMinutes)
                        .coerceIn(ScreenTimeRules.MIN_LIMIT_MINUTES, ScreenTimeRules.MAX_LIMIT_MINUTES),
                )
            }
            ScreenTimeEvent.ToggleNoLimit -> updateEditor { editor ->
                editor.copy(limitMinutes = if (editor.limitMinutes == null) ScreenTimeRules.DEFAULT_LIMIT_MINUTES else null)
            }
            ScreenTimeEvent.SaveEditor -> saveEditor()
            ScreenTimeEvent.DismissEditor -> _uiState.update { it.copy(editor = null) }
        }
    }

    private fun openEditor(packageName: String) {
        val row = _uiState.value.overview?.apps?.firstOrNull { it.packageName == packageName } ?: return
        // Edit from tomorrow's rule when one is pending, so a second edit starts where the first left off.
        val category = row.nextCategory ?: row.category
        val limit = if (row.hasPendingChange) row.nextLimitMinutes else row.limitMinutes
        _uiState.update {
            it.copy(
                editor = AppRuleEditor(
                    packageName = row.packageName,
                    label = row.label,
                    category = category,
                    limitMinutes = if (category == AppCategory.DISTRACTING) limit else null,
                ),
            )
        }
    }

    private fun updateEditor(change: (AppRuleEditor) -> AppRuleEditor) {
        _uiState.update { state -> state.copy(editor = state.editor?.let(change)) }
    }

    private fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        launchThenRefresh {
            if (setAppRule(editor.packageName, editor.category, editor.limitMinutes) == SetAppRuleResult.Saved) {
                _uiState.update { it.copy(editor = null) }
            }
        }
    }

    private fun launchThenRefresh(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Screen Time action failed")
                _uiState.update { it.copy(failed = true) }
            }
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val overview = try {
                loadOverview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Loading Screen Time failed")
                _uiState.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            _uiState.update { it.copy(loading = false, overview = overview, failed = false) }
        }
    }
}
