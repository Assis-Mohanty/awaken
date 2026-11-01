package com.assismohanty.awaken.feature.focuslock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.data.usage.InstalledApp
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.lock.AlwaysAllowed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AllowedAppRow(val packageName: String, val label: String, val allowed: Boolean, val locked: Boolean)

data class AllowedAppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val rows: List<AllowedAppRow> = emptyList(),
)

sealed interface AllowedAppsEvent {
    data class Query(val text: String) : AllowedAppsEvent
    data class Toggle(val packageName: String) : AllowedAppsEvent
}

/** Focus Lock → Allowed apps (GD 8.2): launcher apps with a tick; apps Focus Lock never covers show locked on. */
@HiltViewModel
class AllowedAppsViewModel @Inject constructor(
    private val prefs: UiPrefsRepository,
    private val usage: UsageGateway,
    private val alwaysAllowed: AlwaysAllowed,
) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<AllowedAppsUiState> = combine(apps, prefs.prefs, query) { installed, p, q ->
        if (installed == null) return@combine AllowedAppsUiState(query = q)
        val fixed = alwaysAllowed.current()
        val rows = installed
            .filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }
            .map { AllowedAppRow(it.packageName, it.label, it.packageName in p.allowedApps || it.packageName in fixed, it.packageName in fixed) }
        AllowedAppsUiState(loading = false, query = q, rows = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AllowedAppsUiState())

    init {
        viewModelScope.launch { apps.value = usage.launcherApps() }
    }

    fun onEvent(event: AllowedAppsEvent) {
        when (event) {
            is AllowedAppsEvent.Query -> query.update { event.text }
            is AllowedAppsEvent.Toggle -> viewModelScope.launch {
                if (event.packageName in alwaysAllowed.current()) return@launch
                val current = prefs.prefs.first().allowedApps
                prefs.setAllowedApps(if (event.packageName in current) current - event.packageName else current + event.packageName)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
