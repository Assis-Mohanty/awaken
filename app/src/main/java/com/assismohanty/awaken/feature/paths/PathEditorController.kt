package com.assismohanty.awaken.feature.paths

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** What saving the editor came to. */
sealed interface EditorSubmit {
    data object Done : EditorSubmit
    data class Failed(val failure: EditorFailure) : EditorSubmit
    /** AI consent was revoked while the sheet was open: ask again before sending anything. */
    data object ConsentNeeded : EditorSubmit
    /** The engine's own rules rejected the form; the sheet shows the inline messages. */
    data object Invalid : EditorSubmit
}

/**
 * The editor sheet's behaviour, shared by the Paths tab (create) and Path detail (edit): field events go through the pure
 * reducer, Save validates and hands the form to [submit], and failures stay on the sheet instead of being lost.
 */
class PathEditorController(
    private val scope: CoroutineScope,
    private val submit: suspend (PathEditorState) -> EditorSubmit,
    private val onConsentNeeded: () -> Unit,
) {
    private val _state = MutableStateFlow<PathEditorState?>(null)
    val state: StateFlow<PathEditorState?> = _state.asStateFlow()

    fun open(initial: PathEditorState) {
        _state.value = initial
    }

    fun onEvent(event: PathEditorEvent) {
        when (event) {
            PathEditorEvent.Dismiss -> _state.update { if (it?.submitting == true) it else null }
            PathEditorEvent.Save -> save()
            else -> _state.update { it?.reduce(event) }
        }
    }

    private fun save() {
        val current = _state.value ?: return
        if (current.submitting) return
        if (!current.valid) {
            _state.value = current.copy(showErrors = true)
            return
        }
        _state.value = current.copy(submitting = true, failure = null)
        scope.launch {
            val result = try {
                submit(current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Saving the path failed")
                EditorSubmit.Failed(EditorFailure.GENERIC)
            }
            when (result) {
                EditorSubmit.Done -> _state.value = null
                EditorSubmit.ConsentNeeded -> {
                    _state.update { it?.copy(submitting = false) }
                    onConsentNeeded()
                }
                EditorSubmit.Invalid -> _state.update { it?.copy(submitting = false, showErrors = true) }
                is EditorSubmit.Failed -> _state.update { it?.copy(submitting = false, failure = result.failure) }
            }
        }
    }
}
