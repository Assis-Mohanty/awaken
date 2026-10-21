package com.assismohanty.awaken.feature.paths

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.AdvanceMilestoneUseCase
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.CreateBlock
import com.assismohanty.awaken.core.domain.CreatePathResult
import com.assismohanty.awaken.core.domain.CreatePathUseCase
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.GrantAiConsentUseCase
import com.assismohanty.awaken.core.domain.ObservePathsUseCase
import com.assismohanty.awaken.core.domain.RetryPathResult
import com.assismohanty.awaken.core.domain.RetryPathUseCase
import com.assismohanty.awaken.core.domain.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
data class PathsUiState(
    val loading: Boolean = true,
    val paths: List<PathCardUi> = emptyList(),
    /** Why [PathsEvent.Create] is unavailable right now; null when it is allowed. */
    val createBlock: CreateBlock? = null,
    val consentGiven: Boolean = false,
    val editor: PathEditorState? = null,
    val showConsent: Boolean = false,
)

sealed interface PathsEvent {
    data object Create : PathsEvent
    data object EnableConsent : PathsEvent
    data object DeclineConsent : PathsEvent
    data class Retry(val pathId: String) : PathsEvent
    data class Advance(val pathId: String) : PathsEvent
    data class Stay(val pathId: String) : PathsEvent
    data class Editor(val event: PathEditorEvent) : PathsEvent
}

@HiltViewModel
class PathsViewModel @Inject constructor(
    observePaths: ObservePathsUseCase,
    dayRefresher: DayRefresher,
    private val createPath: CreatePathUseCase,
    private val grantConsent: GrantAiConsentUseCase,
    private val retryPath: RetryPathUseCase,
    private val advanceMilestone: AdvanceMilestoneUseCase,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    private data class Overlay(val showConsent: Boolean = false, val createAfterConsent: Boolean = false)

    private val overlay = MutableStateFlow(Overlay())

    private val editor = PathEditorController(
        scope = viewModelScope,
        submit = { form -> submitNewPath(form) },
        onConsentNeeded = { overlay.update { it.copy(showConsent = true) } },
    )

    val uiState: StateFlow<PathsUiState> = combine(
        observePaths(dayRefresher.ticks).map { it },
        editor.state,
        overlay,
    ) { model, editorState, overlayState ->
        if (model == null) return@combine PathsUiState(loading = false, editor = editorState, showConsent = overlayState.showConsent)
        PathsUiState(
            loading = false,
            paths = model.paths.map { it.toCard(model.consentGiven) },
            createBlock = model.createBlock,
            consentGiven = model.consentGiven,
            editor = editorState,
            showConsent = overlayState.showConsent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PathsUiState())

    fun onEvent(event: PathsEvent) {
        when (event) {
            PathsEvent.Create -> {
                val state = uiState.value
                if (state.createBlock != null) return
                if (state.consentGiven) editor.open(PathEditorState()) else overlay.update { it.copy(showConsent = true, createAfterConsent = true) }
            }
            PathsEvent.EnableConsent -> launchSafely {
                grantConsent()
                val openEditor = overlay.value.createAfterConsent
                overlay.value = Overlay()
                if (openEditor && editor.state.value == null) editor.open(PathEditorState())
            }
            PathsEvent.DeclineConsent -> overlay.value = Overlay()
            is PathsEvent.Retry -> launchSafely {
                when (retryPath(event.pathId)) {
                    RetryPathResult.Started -> Unit
                    RetryPathResult.ConsentRequired -> overlay.update { it.copy(showConsent = true) }
                    RetryPathResult.LimitReached, RetryPathResult.NothingToRetry -> completionCenter.message(UserMessage.Error)
                }
            }
            is PathsEvent.Advance -> launchSafely { advanceMilestone.advance(event.pathId) }
            is PathsEvent.Stay -> launchSafely { advanceMilestone.stay(event.pathId) }
            is PathsEvent.Editor -> editor.onEvent(event.event)
        }
    }

    private suspend fun submitNewPath(form: PathEditorState): EditorSubmit {
        val input = form.input() ?: return EditorSubmit.Invalid
        return when (createPath(input)) {
            is CreatePathResult.Created -> EditorSubmit.Done
            CreatePathResult.ConsentRequired -> EditorSubmit.ConsentNeeded
            is CreatePathResult.Invalid -> EditorSubmit.Invalid
            CreatePathResult.LimitReached -> EditorSubmit.Failed(EditorFailure.LIMIT)
            CreatePathResult.CreatedTodayAlready -> EditorSubmit.Failed(EditorFailure.TODAY)
            CreatePathResult.NoHunter -> EditorSubmit.Failed(EditorFailure.GENERIC)
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Paths action failed")
                completionCenter.message(UserMessage.Error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
