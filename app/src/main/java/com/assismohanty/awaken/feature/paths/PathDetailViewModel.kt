package com.assismohanty.awaken.feature.paths

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.assismohanty.awaken.core.domain.AdvanceMilestoneUseCase
import com.assismohanty.awaken.core.domain.ChangePathStatusUseCase
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.GrantAiConsentUseCase
import com.assismohanty.awaken.core.domain.ObservePathsUseCase
import com.assismohanty.awaken.core.domain.PathSummary
import com.assismohanty.awaken.core.domain.RetryPathResult
import com.assismohanty.awaken.core.domain.RetryPathUseCase
import com.assismohanty.awaken.core.domain.UpdatePathResult
import com.assismohanty.awaken.core.domain.UpdatePathUseCase
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.navigation.PathDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
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

@Immutable
data class PathDetailUiState(
    val loading: Boolean = true,
    /** Null once loaded means the Path is gone (archived or never existed): the screen leaves. */
    val path: PathCardUi? = null,
    /** The re-plan used up this ISO week. */
    val replanUsed: Boolean = false,
    val editor: PathEditorState? = null,
    val showConsent: Boolean = false,
    val confirmArchive: Boolean = false,
)

sealed interface PathDetailEvent {
    data object Edit : PathDetailEvent
    data object Pause : PathDetailEvent
    data object Resume : PathDetailEvent
    data object AskArchive : PathDetailEvent
    data object ConfirmArchive : PathDetailEvent
    data object CancelArchive : PathDetailEvent
    data object Retry : PathDetailEvent
    data object Advance : PathDetailEvent
    data object Stay : PathDetailEvent
    data object EnableConsent : PathDetailEvent
    data object DeclineConsent : PathDetailEvent
    data class Editor(val event: PathEditorEvent) : PathDetailEvent
}

@HiltViewModel
class PathDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePaths: ObservePathsUseCase,
    dayRefresher: DayRefresher,
    private val updatePath: UpdatePathUseCase,
    private val changeStatus: ChangePathStatusUseCase,
    private val grantConsent: GrantAiConsentUseCase,
    private val retryPath: RetryPathUseCase,
    private val advanceMilestone: AdvanceMilestoneUseCase,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    private val pathId = savedStateHandle.toRoute<PathDetailRoute>().pathId

    private data class Overlay(val showConsent: Boolean = false, val confirmArchive: Boolean = false)

    private val overlay = MutableStateFlow(Overlay())

    /** The last summary seen, so Edit can open the sheet from it without a second read. */
    private var latest: PathSummary? = null
    private var latestReplanUsed = false

    private val editor = PathEditorController(
        scope = viewModelScope,
        submit = { form -> submitEdit(form) },
        onConsentNeeded = { overlay.update { it.copy(showConsent = true) } },
    )

    val uiState: StateFlow<PathDetailUiState> = combine(observePaths(dayRefresher.ticks), editor.state, overlay) { model, editorState, overlayState ->
        val summary = model?.paths?.firstOrNull { it.path.id == pathId }
        latest = summary
        latestReplanUsed = summary != null && model != null &&
            summary.path.lastReplanIsoWeek == PathScheduler.isoWeek(model.gameDate)
        PathDetailUiState(
            loading = false,
            path = summary?.toCard(model?.consentGiven == true),
            replanUsed = latestReplanUsed,
            editor = editorState,
            showConsent = overlayState.showConsent,
            confirmArchive = overlayState.confirmArchive,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PathDetailUiState())

    fun onEvent(event: PathDetailEvent) {
        when (event) {
            PathDetailEvent.Edit -> latest?.let { editor.open(PathEditorState.forEdit(it, canReplan = !latestReplanUsed)) }
            PathDetailEvent.Pause -> launchSafely { changeStatus.pause(pathId) }
            PathDetailEvent.Resume -> launchSafely { changeStatus.resume(pathId) }
            PathDetailEvent.AskArchive -> overlay.update { it.copy(confirmArchive = true) }
            PathDetailEvent.CancelArchive -> overlay.update { it.copy(confirmArchive = false) }
            PathDetailEvent.ConfirmArchive -> launchSafely {
                overlay.update { it.copy(confirmArchive = false) }
                changeStatus.archive(pathId)
            }
            PathDetailEvent.Retry -> launchSafely {
                when (retryPath(pathId)) {
                    RetryPathResult.Started -> Unit
                    RetryPathResult.ConsentRequired -> overlay.update { it.copy(showConsent = true) }
                    RetryPathResult.LimitReached, RetryPathResult.NothingToRetry -> completionCenter.message(UserMessage.Error)
                }
            }
            PathDetailEvent.Advance -> launchSafely { advanceMilestone.advance(pathId) }
            PathDetailEvent.Stay -> launchSafely { advanceMilestone.stay(pathId) }
            PathDetailEvent.EnableConsent -> launchSafely {
                grantConsent()
                overlay.update { it.copy(showConsent = false) }
            }
            PathDetailEvent.DeclineConsent -> overlay.update { it.copy(showConsent = false) }
            is PathDetailEvent.Editor -> editor.onEvent(event.event)
        }
    }

    private suspend fun submitEdit(form: PathEditorState): EditorSubmit {
        val input = form.input() ?: return EditorSubmit.Invalid
        return when (updatePath(pathId, input, replan = form.replan)) {
            UpdatePathResult.Updated -> EditorSubmit.Done
            UpdatePathResult.ConsentRequired -> EditorSubmit.ConsentNeeded
            is UpdatePathResult.Invalid -> EditorSubmit.Invalid
            UpdatePathResult.ReplanLimit -> EditorSubmit.Failed(EditorFailure.REPLAN)
            UpdatePathResult.NotFound, UpdatePathResult.Archived -> EditorSubmit.Failed(EditorFailure.GENERIC)
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Path action failed")
                completionCenter.message(UserMessage.Error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Which lifecycle actions a Path in [status] offers. */
internal fun PathStatus.canPause(): Boolean = this == PathStatus.ACTIVE
internal fun PathStatus.canResume(): Boolean = this == PathStatus.PAUSED
