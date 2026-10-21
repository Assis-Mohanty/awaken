package com.assismohanty.awaken.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.core.domain.CompleteOnboardingUseCase
import com.assismohanty.awaken.core.domain.CreatePathUseCase
import com.assismohanty.awaken.core.domain.GrantAiConsentUseCase
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.OnboardingResult
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathInput
import com.assismohanty.awaken.engine.path.PathRules
import com.assismohanty.awaken.engine.path.StatSuggester
import com.assismohanty.awaken.feature.paths.PathEditorState
import com.assismohanty.awaken.feature.settings.HealthAction
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The eight Awakening steps (DESIGN_SYSTEM §4.1), in order. */
enum class OnboardingStep { WELCOME, NAME, FOCUS, CALIBRATION, HEALTH, PATH, REMINDER, COMPLETE }

enum class CalibrationQuestion { TRAINING, STUDY, SLEEP }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    val showNameError: Boolean = false,
    val focus: Set<Stat> = emptySet(),
    val answers: Map<CalibrationQuestion, Awakening.Answer> = emptyMap(),
    /** All three Health Connect read permissions were granted in step 5; saved on the profile at Finish. */
    val healthConnected: Boolean = false,
    /** Health Connect is missing or outdated: the Play Store page was opened (DESIGN_SYSTEM §5 copy shown). */
    val healthNotInstalled: Boolean = false,
    /** Some permissions were declined; verified quests stay off and the hunter can carry on. */
    val healthDeclined: Boolean = false,
    val healthAction: HealthAction? = null,
    val healthPermissions: Set<String> = emptySet(),
    /** Step 6 "Your first Path": both stay empty when the hunter skips; nothing is sent before consent. */
    val pathGoal: String = "",
    val pathLevel: String = "",
    val showConsent: Boolean = false,
    /** The hunter pressed [Enable AI Paths] in step 6; the Path is created right after the hunter exists. */
    val pathConsented: Boolean = false,
    val reminder: LocalTime = DEFAULT_REMINDER,
    val submitting: Boolean = false,
    val failed: Boolean = false,
) {
    val goalValid: Boolean get() = pathGoal.trim().length in PathRules.GOAL_LENGTH
    val levelValid: Boolean get() = pathLevel.trim().length in PathRules.LEVEL_LENGTH

    /** The Path to chart once the hunter exists; null when skipped, declined or not valid. */
    fun pathInput(): PathInput? {
        if (!pathConsented || !goalValid || !levelValid) return null
        val primary = StatSuggester.suggest(pathGoal) ?: focus.firstOrNull() ?: return null
        return PathInput(
            goal = pathGoal.trim(),
            currentLevel = pathLevel.trim(),
            notes = "",
            primaryStat = primary,
            secondaryStat = null,
            activeDays = PathEditorState.DEFAULT_DAYS,
            questsPerDay = 1,
            weeklyMinutes = PathEditorState.DEFAULT_MINUTES,
        )
    }

    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.FOCUS -> focus.size == FOCUS_COUNT
            OnboardingStep.CALIBRATION -> answers.size == CalibrationQuestion.entries.size
            OnboardingStep.PATH -> goalValid && levelValid
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
    data class HealthPermissionsResult(val granted: Set<String>) : OnboardingEvent
    data object HealthActionHandled : OnboardingEvent
    data class PathGoalChanged(val goal: String) : OnboardingEvent
    data class PathLevelChanged(val level: String) : OnboardingEvent
    /** [Skip] on step 6: no Path, nothing is asked or sent. */
    data object SkipPath : OnboardingEvent
    data object EnableAiConsent : OnboardingEvent
    data object DeclineAiConsent : OnboardingEvent
    data class ReminderChanged(val time: LocalTime) : OnboardingEvent
    /** "Enter" on the final window: creates the hunter and the first directive. */
    data object Finish : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val completionCenter: CompletionCenter,
    private val healthGateway: HealthGateway,
    private val grantConsent: GrantAiConsentUseCase,
    private val createPath: CreatePathUseCase,
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
            OnboardingEvent.ConnectHealth -> connectHealth()
            is OnboardingEvent.HealthPermissionsResult -> onHealthResult(event.granted)
            OnboardingEvent.HealthActionHandled -> _uiState.update { it.copy(healthAction = null) }
            is OnboardingEvent.PathGoalChanged ->
                _uiState.update { it.copy(pathGoal = event.goal.take(PathRules.GOAL_LENGTH.last)) }
            is OnboardingEvent.PathLevelChanged ->
                _uiState.update { it.copy(pathLevel = event.level.take(PathRules.LEVEL_LENGTH.last)) }
            OnboardingEvent.SkipPath -> _uiState.update { it.skipPath() }
            OnboardingEvent.EnableAiConsent -> enableConsent()
            OnboardingEvent.DeclineAiConsent -> _uiState.update { it.skipPath() }
            is OnboardingEvent.ReminderChanged -> _uiState.update { it.copy(reminder = event.time) }
            OnboardingEvent.Finish -> finish()
        }
    }


    private fun connectHealth() {
        viewModelScope.launch {
            val available = try {
                healthGateway.status() == HealthStatus.AVAILABLE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Health Connect status failed")
                false
            }
            _uiState.update {
                if (available) {
                    it.copy(healthAction = HealthAction.REQUEST_PERMISSIONS, healthPermissions = healthGateway.requiredPermissions)
                } else {
                    it.copy(healthAction = HealthAction.OPEN_STORE, healthNotInstalled = true)
                }
            }
        }
    }

    private fun onHealthResult(granted: Set<String>) {
        val connected = granted.containsAll(healthGateway.requiredPermissions)
        _uiState.update { it.copy(healthConnected = connected, healthDeclined = !connected, healthNotInstalled = false) }
        if (connected) next()
    }

    private fun enableConsent() {
        viewModelScope.launch {
            try {
                grantConsent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Saving AI consent failed")
                // Consent was not recorded, so no Path is created: treat it as a skip.
                _uiState.update { it.skipPath() }
                return@launch
            }
            _uiState.update { it.copy(showConsent = false, pathConsented = true, step = OnboardingStep.REMINDER) }
        }
    }

    private fun OnboardingUiState.skipPath() =
        copy(pathGoal = "", pathLevel = "", pathConsented = false, showConsent = false, step = OnboardingStep.REMINDER)

    private fun next() = _uiState.update { state ->
        when {
            state.step == OnboardingStep.NAME && !Awakening.isValidName(state.name) -> state.copy(showNameError = true)
            !state.canContinue || state.step == OnboardingStep.COMPLETE -> state
            // The consent sheet comes first; nothing leaves the phone until the hunter enables AI Paths.
            state.step == OnboardingStep.PATH -> if (state.pathConsented) {
                state.copy(step = OnboardingStep.REMINDER)
            } else {
                state.copy(showConsent = true)
            }
            else -> state.copy(step = OnboardingStep.entries[state.step.ordinal + 1])
        }
    }

    /**
     * Creates the Path from step 6 once the hunter exists. The first directive is already written and never waits on the
     * Oracle; a failure here only means no Path yet, so it is logged and the hunter can create one from the Paths tab.
     * Runs non-cancellable because the app leaves onboarding (and clears this ViewModel) as soon as the profile exists.
     */
    private suspend fun chartFirstPath(state: OnboardingUiState) {
        val input = state.pathInput() ?: return
        try {
            withContext(NonCancellable) { createPath(input) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Creating the first Path failed")
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
                completeOnboarding(state.name, state.focus, training, study, sleep, state.reminder, state.healthConnected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Onboarding failed")
                null
            }
            when (result) {
                // The app switches to Today as soon as the profile exists; the unlocks toast there.
                is OnboardingResult.Done -> {
                    completionCenter.achievements(result.unlocked)
                    chartFirstPath(state)
                }
                OnboardingResult.InvalidName -> _uiState.update { it.copy(submitting = false, step = OnboardingStep.NAME, showNameError = true) }
                OnboardingResult.InvalidFocus -> _uiState.update { it.copy(submitting = false, step = OnboardingStep.FOCUS) }
                null -> _uiState.update { it.copy(submitting = false, failed = true) }
            }
        }
    }
}
