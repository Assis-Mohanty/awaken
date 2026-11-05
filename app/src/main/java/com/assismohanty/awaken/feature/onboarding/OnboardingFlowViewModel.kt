package com.assismohanty.awaken.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.core.data.repo.OnboardingDraft
import com.assismohanty.awaken.core.data.repo.OnboardingDraftStore
import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.onboarding.BodyMath
import com.assismohanty.awaken.engine.onboarding.EquipmentChoice
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.IllustrationSet
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import com.assismohanty.awaken.engine.onboarding.LengthUnit
import com.assismohanty.awaken.engine.onboarding.MassUnit
import com.assismohanty.awaken.engine.onboarding.Obstacle
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.OnboardingRules
import com.assismohanty.awaken.engine.onboarding.OnboardingStep
import com.assismohanty.awaken.engine.onboarding.Pace
import com.assismohanty.awaken.engine.onboarding.ScreenTimeBand
import com.assismohanty.awaken.engine.onboarding.SleepWheels
import com.assismohanty.awaken.feature.settings.HealthAction
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
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

/** What the phone currently allows. Re-read whenever the app comes back from a system screen. */
data class OnboardingPermissionState(
    val health: Boolean = false,
    /** Health Connect is missing or outdated: its Play Store page was opened. */
    val healthNotInstalled: Boolean = false,
    /** The permission sheet was answered without the core permissions. */
    val healthDeclined: Boolean = false,
    val usage: Boolean = false,
    val notifications: Boolean = false,
    /** False below Android 13 and once granted: the screen is then left out. */
    val notificationsAskable: Boolean = true,
)

/**
 * The quiz flow (docs/onboarding/PLAN.md). [loaded] turns true once a saved draft has been read or a fresh one made, so the
 * first frame never shows step 1 for someone who was further along. The `*Action` / `open*` / `request*` flags are one-shot
 * requests for the screen to launch a system dialog; the screen clears each one.
 */
data class OnboardingFlowState(
    val loaded: Boolean = false,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** Direction of the last move, for the slide. */
    val forward: Boolean = true,
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val permissions: OnboardingPermissionState = OnboardingPermissionState(),
    val today: DayOfWeek = DayOfWeek.MONDAY,
    val healthAction: HealthAction? = null,
    val healthPermissions: Set<String> = emptySet(),
    val openUsageSettings: Boolean = false,
    val requestNotifications: Boolean = false,
) {
    val canContinue: Boolean get() = OnboardingRules.canContinue(step, answers)

    /** The screens this person will see: Notifications is left out when there is nothing to ask. */
    val visibleSteps: List<OnboardingStep>
        get() = OnboardingStep.entries.filterNot { it.isHidden(permissions) }

    /** True when Android already allows what [step] would ask for, so the screen offers Continue instead. */
    val stepPermissionGranted: Boolean
        get() = when (step) {
            OnboardingStep.HEALTH -> permissions.health
            OnboardingStep.USAGE -> permissions.usage
            OnboardingStep.NOTIFICATIONS -> permissions.notifications
            else -> false
        }
}

private fun OnboardingStep.isHidden(permissions: OnboardingPermissionState): Boolean =
    this == OnboardingStep.NOTIFICATIONS && !permissions.notificationsAskable

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
    data class ScreenTimeSelected(val band: ScreenTimeBand) : OnboardingFlowEvent
    data class BedtimeChanged(val minute: Int) : OnboardingFlowEvent
    data class WakeChanged(val minute: Int) : OnboardingFlowEvent
    data class FitnessSelected(val level: FitnessLevel) : OnboardingFlowEvent
    data class EquipmentSelected(val choice: EquipmentChoice) : OnboardingFlowEvent
    data class InjuryToggled(val region: InjuryRegion) : OnboardingFlowEvent
    data class TrainingDayToggled(val isoDay: Int) : OnboardingFlowEvent
    data class TrainingReminderChanged(val on: Boolean) : OnboardingFlowEvent
    data class PaceSelected(val pace: Pace) : OnboardingFlowEvent

    // Permission screens
    data object ConnectHealth : OnboardingFlowEvent
    data object HealthActionHandled : OnboardingFlowEvent
    data class HealthPermissionsResult(val granted: Set<String>) : OnboardingFlowEvent
    data object OpenUsageSettings : OnboardingFlowEvent
    data object UsageSettingsHandled : OnboardingFlowEvent
    data object RequestNotifications : OnboardingFlowEvent
    data object NotificationsHandled : OnboardingFlowEvent
    data class NotificationsResult(val granted: Boolean) : OnboardingFlowEvent

    /** The app came back to the foreground, possibly from a system screen: read the permissions again. */
    data object RecheckPermissions : OnboardingFlowEvent
}

@HiltViewModel
class OnboardingFlowViewModel internal constructor(
    private val drafts: OnboardingDraftStore,
    private val country: String?,
    private val newId: () -> String,
    private val permissions: OnboardingPermissions,
    private val today: () -> DayOfWeek,
) : ViewModel() {

    @Inject
    constructor(drafts: OnboardingDraftStore, permissions: AndroidOnboardingPermissions, clock: Clock) : this(
        drafts = drafts,
        country = Locale.getDefault().country,
        newId = { UUID.randomUUID().toString() },
        permissions = permissions,
        today = { clock.gameDate(GameDay.DEFAULT_DAY_START_HOUR).dayOfWeek },
    )

    private val _uiState = MutableStateFlow(OnboardingFlowState())
    val uiState: StateFlow<OnboardingFlowState> = _uiState.asStateFlow()

    /** The person went to Android's usage-access page from this flow, so coming back with access means "carry on". */
    private var awaitingUsage = false

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
            recheck()
        }
    }

    fun onEvent(event: OnboardingFlowEvent) {
        if (!_uiState.value.loaded) return
        when (event) {
            OnboardingFlowEvent.Next -> next(skip = false)
            OnboardingFlowEvent.Skip -> next(skip = true)
            OnboardingFlowEvent.Back -> change { state ->
                if (!state.step.canGoBack) state else state.copy(step = previousStep(state), forward = false)
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
            is OnboardingFlowEvent.ScreenTimeSelected -> answer { it.copy(screenTime = event.band) }
            is OnboardingFlowEvent.BedtimeChanged -> answer {
                // Moving the bedtime can leave the wake time too close or too far, so the wake time follows it.
                val (bed, wake) = OnboardingRules.sleepShown(event.minute, it.wakeMinute, GameDay.DEFAULT_DAY_START_HOUR)
                it.copy(bedtimeMinute = bed, wakeMinute = wake)
            }
            is OnboardingFlowEvent.WakeChanged -> answer {
                it.copy(bedtimeMinute = it.bedtimeMinute ?: BedtimeRules.DEFAULT_BEDTIME, wakeMinute = event.minute)
            }
            is OnboardingFlowEvent.FitnessSelected -> answer { it.copy(fitness = event.level) }
            is OnboardingFlowEvent.EquipmentSelected -> answer { it.copy(equipment = event.choice) }
            is OnboardingFlowEvent.InjuryToggled -> answer { it.copy(injuries = OnboardingRules.toggleInjury(it.injuries, event.region)) }
            is OnboardingFlowEvent.TrainingDayToggled -> answer {
                val days = OnboardingRules.toggleDay(it.trainingDays, event.isoDay)
                // A reminder with no days has nothing to remind about.
                it.copy(trainingDays = days, trainingReminder = it.trainingReminder && days.isNotEmpty())
            }
            is OnboardingFlowEvent.TrainingReminderChanged -> answer { it.copy(trainingReminder = event.on) }
            is OnboardingFlowEvent.PaceSelected -> answer { it.copy(pace = event.pace) }

            OnboardingFlowEvent.ConnectHealth -> connectHealth()
            OnboardingFlowEvent.HealthActionHandled -> _uiState.update { it.copy(healthAction = null) }
            is OnboardingFlowEvent.HealthPermissionsResult -> onHealthResult(event.granted)
            OnboardingFlowEvent.OpenUsageSettings -> {
                awaitingUsage = true
                _uiState.update { it.copy(openUsageSettings = true) }
            }
            OnboardingFlowEvent.UsageSettingsHandled -> _uiState.update { it.copy(openUsageSettings = false) }
            OnboardingFlowEvent.RequestNotifications -> _uiState.update { it.copy(requestNotifications = true) }
            OnboardingFlowEvent.NotificationsHandled -> _uiState.update { it.copy(requestNotifications = false) }
            is OnboardingFlowEvent.NotificationsResult -> {
                _uiState.update { it.copy(permissions = it.permissions.copy(notifications = event.granted)) }
                // Allowed or not, the question has been asked: move on.
                next(skip = false)
            }
            OnboardingFlowEvent.RecheckPermissions -> viewModelScope.launch { recheck() }
        }
    }

    private fun fromDraft(draft: OnboardingDraft?): OnboardingFlowState {
        val (lengthUnit, massUnit) = BodyMath.defaultUnits(country)
        val saved = draft?.answers ?: OnboardingAnswers(heightUnit = lengthUnit, weightUnit = massUnit)
        val answers = if (saved.hunterId.isBlank()) saved.copy(hunterId = newId()) else saved
        val step = draft?.let { OnboardingStep.entries.getOrNull(it.step) } ?: OnboardingStep.WELCOME
        return OnboardingFlowState(loaded = true, step = step, forward = true, answers = answers, today = today())
    }

    private fun next(skip: Boolean) = change { state ->
        when {
            skip && !state.step.skippable -> state
            !skip && !state.canContinue -> state
            else -> {
                val answers = if (skip) OnboardingRules.skipped(state.step, state.answers) else OnboardingRules.withShownDefaults(state.step, state.answers)
                state.copy(step = nextStep(state), forward = true, answers = answers)
            }
        }
    }

    /** The step after the current one, leaving out screens that have nothing to ask. */
    private fun nextStep(state: OnboardingFlowState): OnboardingStep {
        var candidate = state.step.next
        while (candidate.isHidden(state.permissions) && candidate != candidate.next) candidate = candidate.next
        return candidate
    }

    /** The step before: hidden screens are skipped, and so is the building screen, which is never a place to return to. */
    private fun previousStep(state: OnboardingFlowState): OnboardingStep {
        var candidate = state.step.previous
        while ((candidate.isHidden(state.permissions) || candidate == OnboardingStep.BUILDING) && candidate != candidate.previous) {
            candidate = candidate.previous
        }
        return candidate
    }

    private fun connectHealth() {
        viewModelScope.launch {
            val available = try {
                permissions.healthStatus() == HealthStatus.AVAILABLE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Health Connect status failed")
                false
            }
            _uiState.update {
                if (available) {
                    it.copy(healthAction = HealthAction.REQUEST_PERMISSIONS, healthPermissions = permissions.healthPermissions)
                } else {
                    it.copy(
                        healthAction = HealthAction.OPEN_STORE,
                        permissions = it.permissions.copy(healthNotInstalled = true),
                    )
                }
            }
        }
    }

    private fun onHealthResult(granted: Set<String>) {
        val connected = granted.containsAll(permissions.healthRequired)
        change {
            it.copy(
                answers = it.answers.copy(healthConnected = connected),
                permissions = it.permissions.copy(health = connected, healthDeclined = !connected, healthNotInstalled = false),
            )
        }
        if (connected) next(skip = false)
    }

    /** Reads the phone's permissions into the state; carries on from the usage screen if access was just granted there. */
    private suspend fun recheck() {
        val health = try {
            permissions.healthGranted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Reading Health Connect permissions failed")
            false
        }
        val usage = permissions.usageGranted()
        val notifications = permissions.notificationsGranted()
        val askable = permissions.notificationsAskable()
        change { state ->
            state.copy(
                answers = state.answers.copy(healthConnected = health),
                permissions = state.permissions.copy(
                    health = health,
                    healthDeclined = state.permissions.healthDeclined && !health,
                    usage = usage,
                    notifications = notifications,
                    notificationsAskable = askable,
                ),
            )
        }
        if (awaitingUsage && usage && _uiState.value.step == OnboardingStep.USAGE) {
            awaitingUsage = false
            next(skip = false)
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
