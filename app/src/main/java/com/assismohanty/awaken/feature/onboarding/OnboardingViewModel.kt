package com.assismohanty.awaken.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.CompleteOnboardingUseCase
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.OnboardingResult
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** The seven Awakening steps (DESIGN_SYSTEM §4.1), in order. */
enum class OnboardingStep { WELCOME, NAME, FOCUS, CALIBRATION, HEALTH, REMINDER, COMPLETE }

enum class CalibrationQuestion { TRAINING, STUDY, SLEEP }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    val showNameError: Boolean = false,
    val focus: Set<Stat> = emptySet(),
    val answers: Map<CalibrationQuestion, Awakening.Answer> = emptyMap(),
    /** Health Connect is wired in Phase 11; until then Connect only shows a notice. */
    val healthNoticeShown: Boolean = false,
    val reminder: LocalTime = DEFAULT_REMINDER,
    val submitting: Boolean = false,
    val failed: Boolean = false,
) {
    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.FOCUS -> focus.size == FOCUS_COUNT
            OnboardingStep.CALIBRATION -> answers.size == CalibrationQuestion.entries.size
            else -> true
        }

    companion object {
        const val FOCUS_COUNT = 2
        /** DESIGN_SYSTEM §4.1 step 6 default. */
        val DEFAULT_REMINDER: LocalTime = LocalTime.of(7, 0)
    }
}

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
    data class NameChanged(val name: String) : OnboardingEvent
    data class FocusToggled(val stat: Stat) : OnboardingEvent
    data class Answered(val question: CalibrationQuestion, val answer: Awakening.Answer) : OnboardingEvent
    data object ConnectHealth : OnboardingEvent
    data class ReminderChanged(val time: LocalTime) : OnboardingEvent
    /** "Enter" on the final window: creates the hunter and the first directive. */
    data object Finish : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Next -> next()
            OnboardingEvent.Back -> _uiState.update { state ->
                if (state.submitting) state else state.copy(step = OnboardingStep.entries[(state.step.ordinal - 1).coerceAtLeast(0)])
            }
            is OnboardingEvent.NameChanged -> _uiState.update { it.copy(name = event.name, showNameError = false) }
            is OnboardingEvent.FocusToggled -> _uiState.update { state ->
                when {
                    event.stat in state.focus -> state.copy(focus = state.focus - event.stat)
                    state.focus.size < OnboardingUiState.FOCUS_COUNT -> state.copy(focus = state.focus + event.stat)
                    else -> state
                }
            }
            is OnboardingEvent.Answered -> _uiState.update { it.copy(answers = it.answers + (event.question to event.answer)) }
            OnboardingEvent.ConnectHealth -> _uiState.update { it.copy(healthNoticeShown = true) }
            is OnboardingEvent.ReminderChanged -> _uiState.update { it.copy(reminder = event.time) }
            OnboardingEvent.Finish -> finish()
        }
    }

    private fun next() = _uiState.update { state ->
        when {
            state.step == OnboardingStep.NAME && !Awakening.isValidName(state.name) -> state.copy(showNameError = true)
            !state.canContinue || state.step == OnboardingStep.COMPLETE -> state
            else -> state.copy(step = OnboardingStep.entries[state.step.ordinal + 1])
        }
    }

    private fun finish() {
        val state = _uiState.value
        if (state.submitting) return
        val answers = state.answers
        val training = answers[CalibrationQuestion.TRAINING] ?: return
        val study = answers[CalibrationQuestion.STUDY] ?: return
        val sleep = answers[CalibrationQuestion.SLEEP] ?: return
        _uiState.update { it.copy(submitting = true, failed = false) }
        viewModelScope.launch {
            val result = try {
                completeOnboarding(state.name, state.focus, training, study, sleep, state.reminder)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Onboarding failed")
                null
            }
            when (result) {
                // The app switches to Today as soon as the profile exists; the unlocks toast there.
                is OnboardingResult.Done -> completionCenter.achievements(result.unlocked)
                OnboardingResult.InvalidName -> _uiState.update { it.copy(submitting = false, step = OnboardingStep.NAME, showNameError = true) }
                OnboardingResult.InvalidFocus -> _uiState.update { it.copy(submitting = false, step = OnboardingStep.FOCUS) }
                null -> _uiState.update { it.copy(submitting = false, failed = true) }
            }
        }
    }
}
