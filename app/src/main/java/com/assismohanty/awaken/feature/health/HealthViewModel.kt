package com.assismohanty.awaken.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.domain.HealthOverview
import com.assismohanty.awaken.core.domain.HealthOverviewResult
import com.assismohanty.awaken.core.domain.LoadHealthOverviewUseCase
import com.assismohanty.awaken.feature.settings.HealthAction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class HealthScreenStatus { LOADING, READY, DISABLED, UNAVAILABLE, READ_FAILED }

data class HealthUiState(
    val status: HealthScreenStatus = HealthScreenStatus.LOADING,
    val overview: HealthOverview? = null,
    /** A reload is running; the previous data stays on screen meanwhile. */
    val refreshing: Boolean = false,
    val action: HealthAction? = null,
    val permissions: Set<String> = emptySet(),
)

sealed interface HealthEvent {
    data object Refresh : HealthEvent

    /** Open Health Connect's permission sheet again so the hunter can allow the optional metrics. */
    data object AllowMoreMetrics : HealthEvent
    data object ActionHandled : HealthEvent
    data object PermissionsResult : HealthEvent
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val loadOverview: LoadHealthOverviewUseCase,
    private val gateway: HealthGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: HealthEvent) {
        when (event) {
            HealthEvent.Refresh, HealthEvent.PermissionsResult -> refresh()
            HealthEvent.AllowMoreMetrics ->
                _uiState.update { it.copy(action = HealthAction.REQUEST_PERMISSIONS, permissions = gateway.allPermissions) }
            HealthEvent.ActionHandled -> _uiState.update { it.copy(action = null) }
        }
    }

    private fun refresh() {
        if (_uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val next = try {
                when (val result = loadOverview()) {
                    is HealthOverviewResult.Ready -> HealthScreenStatus.READY to result.overview
                    HealthOverviewResult.Disabled -> HealthScreenStatus.DISABLED to null
                    HealthOverviewResult.Unavailable -> HealthScreenStatus.UNAVAILABLE to null
                    HealthOverviewResult.ReadFailed -> HealthScreenStatus.READ_FAILED to _uiState.value.overview
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Loading the Health screen failed")
                HealthScreenStatus.READ_FAILED to _uiState.value.overview
            }
            _uiState.update { it.copy(status = next.first, overview = next.second, refreshing = false) }
        }
    }
}
