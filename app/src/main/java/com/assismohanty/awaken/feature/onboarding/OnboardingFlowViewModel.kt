package com.assismohanty.awaken.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.repo.OnboardingDraft
import com.assismohanty.awaken.core.data.repo.OnboardingDraftStore
import com.assismohanty.awaken.engine.onboarding.BodyMath
import com.assismohanty.awaken.engine.onboarding.IllustrationSet
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.LengthUnit
import com.assismohanty.awaken.engine.onboarding.MassUnit
import com.assismohanty.awaken.engine.onboarding.Obstacle
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.OnboardingRules
import com.assismohanty.awaken.engine.onboarding.OnboardingStep
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The quiz flow (docs/onboarding/PLAN.md). [loaded] turns true once a saved draft has been read or a fresh one made, so the
 * first frame never shows step 1 for someone who was further along.
 */
data class OnboardingFlowState(
    val loaded: Boolean = false,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** Direction of the last move, for the slide. */
    val forward: Boolean = true,
    val answers: OnboardingAnswers = OnboardingAnswers(),
) {
    val canContinue: Boolean get() = OnboardingRules.canContinue(step, answers)
}

sealed interface OnboardingFlowEvent {
    data object Next : OnboardingFlowEvent
    data object Back : OnboardingFlowEvent
    data object Skip : OnboardingFlowEvent
    data class NameChanged(val name: String) : OnboardingFlowEvent
    data class GenderSelected(val set: IllustrationSet) : OnboardingFlowEvent
    data class AgeChanged(val age: Int) : OnboardingFlowEvent
    data class HeightChanged(val cm: Double) : OnboardingFlowEvent
    data class WeightChanged(val kg: Double) : OnboardingFlowEvent
    data class HeightUnitChanged(val unit: LengthUnit) : OnboardingFlowEvent
    data class WeightUnitChanged(val unit: MassUnit) : OnboardingFlowEvent
    data class StageChanged(val stage: Int) : OnboardingFlowEvent
    data class ImproveToggled(val goal: ImproveGoal) : OnboardingFlowEvent
    data class ObstacleToggled(val obstacle: Obstacle) : OnboardingFlowEvent
}

@HiltViewModel
class OnboardingFlowViewModel internal constructor(
    private val drafts: OnboardingDraftStore,
    private val country: String?,
    private val newId: () -> String,
) : ViewModel() {

    @Inject
    constructor(drafts: OnboardingDraftStore) : this(drafts, Locale.getDefault().country, { UUID.randomUUID().toString() })

    private val _uiState = MutableStateFlow(OnboardingFlowState())
    val uiState: StateFlow<OnboardingFlowState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val draft = try {
                drafts.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Reading the onboarding draft failed")
                null
            }
            _uiState.update { fromDraft(draft) }
            save()
        }
    }

    fun onEvent(event: OnboardingFlowEvent) {
        if (!_uiState.value.loaded) return
        when (event) {
            OnboardingFlowEvent.Next -> next(skip = false)
            OnboardingFlowEvent.Skip -> next(skip = true)
            OnboardingFlowEvent.Back -> change { state ->
                if (!state.step.canGoBack) state else state.copy(step = state.step.previous, forward = false)
            }
            is OnboardingFlowEvent.NameChanged -> answer { it.copy(name = event.name.take(NAME_INPUT_LIMIT)) }
            is OnboardingFlowEvent.GenderSelected -> answer { it.copy(illustrationSet = event.set) }
            is OnboardingFlowEvent.AgeChanged -> answer { it.copy(age = event.age.coerceIn(BodyMath.AGE_MIN, BodyMath.AGE_MAX)) }
            is OnboardingFlowEvent.HeightChanged -> answer { it.copy(heightCm = event.cm) }
            is OnboardingFlowEvent.WeightChanged -> answer { it.copy(weightKg = event.kg) }
            is OnboardingFlowEvent.HeightUnitChanged -> answer { it.copy(heightUnit = event.unit) }
            is OnboardingFlowEvent.WeightUnitChanged -> answer { it.copy(weightUnit = event.unit) }
            is OnboardingFlowEvent.StageChanged ->
                answer { it.copy(bodyStage = event.stage.coerceIn(1, OnboardingRules.BODY_STAGES)) }
            is OnboardingFlowEvent.ImproveToggled -> answer { it.copy(improve = OnboardingRules.toggleImprove(it.improve, event.goal)) }
            is OnboardingFlowEvent.ObstacleToggled ->
                answer { it.copy(obstacles = OnboardingRules.toggleObstacle(it.obstacles, event.obstacle)) }
        }
    }

    private fun fromDraft(draft: OnboardingDraft?): OnboardingFlowState {
        val (lengthUnit, massUnit) = BodyMath.defaultUnits(country)
        val saved = draft?.answers ?: OnboardingAnswers(heightUnit = lengthUnit, weightUnit = massUnit)
        val answers = if (saved.hunterId.isBlank()) saved.copy(hunterId = newId()) else saved
        val step = draft?.let { OnboardingStep.entries.getOrNull(it.step) } ?: OnboardingStep.WELCOME
        return OnboardingFlowState(loaded = true, step = step, forward = true, answers = answers)
    }

    private fun next(skip: Boolean) = change { state ->
        when {
            skip && !state.step.skippable -> state
            !skip && !state.canContinue -> state
            else -> {
                val answers = if (skip) OnboardingRules.skipped(state.step, state.answers) else OnboardingRules.withShownDefaults(state.step, state.answers)
                state.copy(step = state.step.next, forward = true, answers = answers)
            }
        }
    }

    private fun answer(block: (OnboardingAnswers) -> OnboardingAnswers) = change { it.copy(answers = block(it.answers)) }

    /** Every change is saved, so a killed app resumes with what was typed and picked, not just the last finished step. */
    private fun change(block: (OnboardingFlowState) -> OnboardingFlowState) {
        _uiState.update(block)
        save()
    }

    private fun save() {
        val state = _uiState.value
        if (!state.loaded) return
        viewModelScope.launch {
            try {
                drafts.save(OnboardingDraft(step = state.step.ordinal, answers = state.answers))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The flow still works; it only would not resume after a kill.
                Timber.w(e, "Saving the onboarding draft failed")
            }
        }
    }

    private companion object {
        /** A little over the 20-character name limit, so a paste is trimmed and a typo can still be fixed. */
        const val NAME_INPUT_LIMIT = 40
    }
}
