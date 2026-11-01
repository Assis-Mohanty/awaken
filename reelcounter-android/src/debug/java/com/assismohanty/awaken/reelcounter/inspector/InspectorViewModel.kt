package com.assismohanty.awaken.reelcounter.inspector

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.reelcounter.core.detect.ParseResult
import com.assismohanty.awaken.reelcounter.data.DetectorRepository
import com.assismohanty.awaken.reelcounter.data.DetectorSource
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.assismohanty.awaken.reelcounter.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class InspectorUiState(
    val enabled: Boolean = false,
    val files: List<String> = emptyList(),
    val openName: String? = null,
    val openText: String = "",
    val rulesText: String = "",
    val rulesMessage: String? = null,
    val rulesSource: DetectorSource = DetectorSource.BUILT_IN,
)

sealed interface InspectorEvent {
    data class Toggle(val enabled: Boolean) : InspectorEvent
    data object Refresh : InspectorEvent
    data class Open(val name: String) : InspectorEvent
    data object Close : InspectorEvent
    data class Delete(val name: String) : InspectorEvent
    data class RulesChanged(val value: String) : InspectorEvent
    data object SaveRules : InspectorEvent
    data object ResetRules : InspectorEvent
}

@HiltViewModel
class InspectorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val detectors: DetectorRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(InspectorUiState(rulesText = currentRulesText()))
    val state: StateFlow<InspectorUiState> = _state

    init {
        settings.inspectorEnabled.onEach { on -> _state.update { it.copy(enabled = on) } }.launchIn(viewModelScope)
        detectors.config.onEach { load -> _state.update { it.copy(rulesSource = load.source) } }.launchIn(viewModelScope)
        refresh()
    }

    fun onEvent(event: InspectorEvent) {
        when (event) {
            is InspectorEvent.Toggle -> viewModelScope.launch { settings.setInspectorEnabled(event.enabled) }
            InspectorEvent.Refresh -> refresh()
            is InspectorEvent.Open -> viewModelScope.launch {
                val text = withContext(io) { File(dir(), event.name).takeIf { it.exists() }?.readText().orEmpty() }
                _state.update { it.copy(openName = event.name, openText = text) }
            }
            InspectorEvent.Close -> _state.update { it.copy(openName = null, openText = "") }
            is InspectorEvent.Delete -> viewModelScope.launch {
                withContext(io) { File(dir(), event.name).delete() }
                _state.update { it.copy(openName = null, openText = "") }
                refresh()
            }
            is InspectorEvent.RulesChanged -> _state.update { it.copy(rulesText = event.value, rulesMessage = null) }
            InspectorEvent.SaveRules -> {
                val message = when (val r = detectors.saveOverride(_state.value.rulesText)) {
                    is ParseResult.Ok -> "Saved. These rules are active now."
                    is ParseResult.Error -> "Not saved: ${r.message}"
                }
                _state.update { it.copy(rulesMessage = message) }
            }
            InspectorEvent.ResetRules -> {
                detectors.resetOverride()
                _state.update { it.copy(rulesText = currentRulesText(), rulesMessage = "Back to the built-in rules.") }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val names = withContext(io) {
                dir().listFiles()?.sortedByDescending { it.lastModified() }?.map { it.name }.orEmpty()
            }
            _state.update { it.copy(files = names) }
        }
    }

    private fun dir() = File(context.filesDir, DebugInspectorHooks.DIR_NAME).also { it.mkdirs() }

    private fun currentRulesText(): String = detectors.overrideText() ?: detectors.builtInText()
}
