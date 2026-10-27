package com.assismohanty.awaken.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.LoadWeeklyReviewUseCase
import com.assismohanty.awaken.core.domain.ReviewResult
import com.assismohanty.awaken.core.domain.SubmitWeeklyReviewUseCase
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.core.domain.WeeklyReviewInput
import com.assismohanty.awaken.core.domain.WeeklyReviewModel
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** The five review steps (GD §14.4), in order. */
enum class ReviewStep { NUMBERS, WENT_WELL, SLIPPED, FOCUS, INTENTION }

data class ReviewUiState(
    val loading: Boolean = true,
    /** Null once loaded means no review is open (or this week's is done). */
    val model: WeeklyReviewModel? = null,
    val step: ReviewStep = ReviewStep.NUMBERS,
    val wentWell: String = "",
    val slipped: String = "",
    val focus: List<Stat> = emptyList(),
    val intention: String = "",
    val submitting: Boolean = false,
    /** Saved (or already saved elsewhere): the screen closes. */
    val done: Boolean = false,
) {
    val canSave: Boolean get() = focus.size == FOCUS_COUNT && !submitting

    companion object {
        const val FOCUS_COUNT = 2
    }
}

sealed interface ReviewEvent {
    data object Next : ReviewEvent
    data object Back : ReviewEvent
    /** [Skip] on an optional step: clears that step's answer and moves on. */
    data object Skip : ReviewEvent
    data class WentWellChanged(val text: String) : ReviewEvent
    data class SlippedChanged(val text: String) : ReviewEvent
    data class IntentionChanged(val text: String) : ReviewEvent
    data class ToggleFocus(val stat: Stat) : ReviewEvent
    data object Save : ReviewEvent
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val loadReview: LoadWeeklyReviewUseCase,
    private val submitReview: SubmitWeeklyReviewUseCase,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val model = try {
                loadReview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Loading the weekly review failed")
                completionCenter.message(UserMessage.Error)
                null
            }
            _uiState.update { it.copy(loading = false, model = model, focus = model?.focus.orEmpty()) }
        }
    }

    fun onEvent(event: ReviewEvent) {
        when (event) {
            ReviewEvent.Next -> _uiState.update {
                it.copy(step = ReviewStep.entries[(it.step.ordinal + 1).coerceAtMost(ReviewStep.entries.lastIndex)])
            }
            ReviewEvent.Back -> _uiState.update { it.copy(step = ReviewStep.entries[(it.step.ordinal - 1).coerceAtLeast(0)]) }
            ReviewEvent.Skip -> skip()
            // The fields cap input at the GD §14.4 lengths; the engine cleans it again on save.
            is ReviewEvent.WentWellChanged -> _uiState.update { it.copy(wentWell = event.text) }
            is ReviewEvent.SlippedChanged -> _uiState.update { it.copy(slipped = event.text) }
            is ReviewEvent.IntentionChanged -> _uiState.update { it.copy(intention = event.text) }
            is ReviewEvent.ToggleFocus -> _uiState.update { state ->
                val now = state.focus
                // Exactly two (GD §2): a third tap replaces the older pick.
                state.copy(focus = if (event.stat in now) now - event.stat else (now + event.stat).takeLast(ReviewUiState.FOCUS_COUNT))
            }
            ReviewEvent.Save -> save()
        }
    }

    /** Clears the optional answer and moves on; skipping the last step saves the review. */
    private fun skip() {
        val step = _uiState.value.step
        _uiState.update { state ->
            when (step) {
                ReviewStep.WENT_WELL -> state.copy(wentWell = "", step = ReviewStep.SLIPPED)
                ReviewStep.SLIPPED -> state.copy(slipped = "", step = ReviewStep.FOCUS)
                ReviewStep.INTENTION -> state.copy(intention = "")
                ReviewStep.NUMBERS, ReviewStep.FOCUS -> state
            }
        }
        if (step == ReviewStep.INTENTION) save()
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = try {
                submitReview(WeeklyReviewInput(state.wentWell, state.slipped, state.focus, state.intention))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Saving the weekly review failed")
                null
            }
            when (result) {
                is ReviewResult.Saved -> {
                    completionCenter.message(UserMessage.ReviewSaved)
                    completionCenter.achievements(result.unlocked)
                    _uiState.update { it.copy(submitting = false, done = true) }
                }
                ReviewResult.AlreadySubmitted, ReviewResult.Closed -> _uiState.update { it.copy(submitting = false, done = true) }
                ReviewResult.InvalidFocus -> _uiState.update { it.copy(submitting = false, step = ReviewStep.FOCUS) }
                null -> {
                    completionCenter.message(UserMessage.Error)
                    _uiState.update { it.copy(submitting = false) }
                }
            }
        }
    }
}
