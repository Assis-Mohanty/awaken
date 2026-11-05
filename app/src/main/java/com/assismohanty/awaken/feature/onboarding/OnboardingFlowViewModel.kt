package com.assismohanty.awaken.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.core.data.repo.OnboardingDraft
import com.assismohanty.awaken.core.data.repo.OnboardingDraftStore
import com.assismohanty.awaken.core.domain.CompleteOnboardingUseCase
import com.assismohanty.awaken.core.domain.OnboardingPlanPreview
import com.assismohanty.awaken.core.domain.OnboardingQuestLine
import com.assismohanty.awaken.core.domain.OnboardingResult
import com.assismohanty.awaken.core.domain.PreviewOnboardingPlanUseCase
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
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.OnboardingGrants
import com.assismohanty.awaken.engine.onboarding.OnboardingPlan
import com.assismohanty.awaken.engine.onboarding.OnboardingPlanner
import com.assismohanty.awaken.engine.onboarding.OnboardingRules
import com.assismohanty.awaken.engine.onboarding.OnboardingStep
import com.assismohanty.awaken.engine.onboarding.Pace
import com.assismohanty.awaken.engine.onboarding.ScreenTimeBand
import com.assismohanty.awaken.engine.onboarding.WeightTargetRules
import com.assismohanty.awaken.feature.settings.HealthAction
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** The first plan's quests, which come from the generator and so take a moment. */
sealed interface PlanBuild {
    data object Idle : PlanBuild
    data object Running : PlanBuild
    data class Ready(val quests: List<OnboardingQuestLine>) : PlanBuild
    data object Failed : PlanBuild
}

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
    val todayDate: LocalDate = LocalDate.of(2000, 1, 3),
    val healthAction: HealthAction? = null,
    val healthPermissions: Set<String> = emptySet(),
    val openUsageSettings: Boolean = false,
    val requestNotifications: Boolean = false,
    /** The notification prompt was answered on its screen, so the later prompt after a first quest must not repeat it. */
    val notificationsAsked: Boolean = false,
    /** The first plan's quests, worked out in the background; the plan's numbers do not wait for it. */
    val build: PlanBuild = PlanBuild.Idle,
    /** Start was pressed and the profile is being saved. */
    val committing: Boolean = false,
    val commitFailed: Boolean = false,
) {
    val today: DayOfWeek get() = todayDate.dayOfWeek

    /** What Android allows now: the plan depends on it (limits and phone-down nights need usage access). */
    val grants: OnboardingGrants get() = OnboardingGrants(usageAccess = permissions.usage, healthConnected = answers.healthConnected)

    /** The plan for the answers so far. Pure and cheap, so edits show at once. */
    val plan: OnboardingPlan get() = OnboardingPlanner.plan(answers, grants, todayDate)

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

    // Building and the plan
    /** The building ring reached 100% and the plan is ready: show it. */
    data object BuildFinished : OnboardingFlowEvent
    data object RetryBuild : OnboardingFlowEvent
    data class PlanFocusStatTapped(val stat: Stat) : OnboardingFlowEvent
    data class PlanStepGoalChanged(val goal: Int) : OnboardingFlowEvent
    data class PlanFocusGoalChanged(val minutes: Int) : OnboardingFlowEvent
    data class PlanAppLimitChanged(val minutes: Int) : OnboardingFlowEvent
    data class PlanTrainingDayToggled(val isoDay: Int) : OnboardingFlowEvent
    data class TargetChosen(val weightKg: Double, val weeks: Int) : OnboardingFlowEvent
    data object TargetCleared : OnboardingFlowEvent

    /** Start: saves everything and enters the app. */
    data object Start : OnboardingFlowEvent
}

@HiltViewModel
class OnboardingFlowViewModel internal constructor(
    private val drafts: OnboardingDraftStore,
    private val country: String?,
    private val newId: () -> String,
    private val permissions: OnboardingPermissions,
    private val today: () -> LocalDate,
    private val previewPlan: suspend (OnboardingAnswers, OnboardingGrants) -> OnboardingPlanPreview,
    private val completeOnboarding: suspend (OnboardingAnswers, OnboardingGrants, Boolean) -> OnboardingResult,
) : ViewModel() {

    @Inject
    constructor(
        drafts: OnboardingDraftStore,
        permissions: AndroidOnboardingPermissions,
        clock: Clock,
        preview: PreviewOnboardingPlanUseCase,
        complete: CompleteOnboardingUseCase,
    ) : this(
        drafts = drafts,
        country = Locale.getDefault().country,
        newId = { UUID.randomUUID().toString() },
        permissions = permissions,
        today = { clock.gameDate(GameDay.DEFAULT_DAY_START_HOUR) },
        previewPlan = { answers, grants -> preview(answers, grants) },
        completeOnboarding = { answers, grants, asked -> complete(answers, grants, asked) },
    )

    private var buildJob: Job? = null

    /** Debug preview: fresh answers, nothing saved, so someone who already has a profile can look at the flow. */
    private var previewOnly = false

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
            if (previewOnly) return@launch
            _uiState.update { fromDraft(draft) }
            save()
            recheck()
            // A person who was building or looking at the plan when the app closed needs the plan again.
            if (_uiState.value.step == OnboardingStep.BUILDING || _uiState.value.step == OnboardingStep.PLAN) startBuild()
        }
    }

    /** Starts the flow from the first screen on fresh answers and stops saving the draft. Debug preview only. */
    fun startPreview() {
        previewOnly = true
        buildJob?.cancel()
        _uiState.value = fromDraft(null)
        viewModelScope.launch { recheck() }
    }

    fun onEvent(event: OnboardingFlowEvent) {
        if (!_uiState.value.loaded) return
        when (event) {
            OnboardingFlowEvent.Next -> if (_uiState.value.step == OnboardingStep.PLAN) start() else next(skip = false)
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
                _uiState.update { it.copy(permissions = it.permissions.copy(notifications = event.granted), notificationsAsked = true) }
                // Allowed or not, the question has been asked: move on.
                next(skip = false)
            }
            OnboardingFlowEvent.RecheckPermissions -> viewModelScope.launch { recheck() }

            OnboardingFlowEvent.BuildFinished -> change { state ->
                if (state.step == OnboardingStep.BUILDING && state.build is PlanBuild.Ready) {
                    state.copy(step = OnboardingStep.PLAN, forward = true)
                } else {
                    state
                }
            }
            OnboardingFlowEvent.RetryBuild -> startBuild()
            is OnboardingFlowEvent.PlanFocusStatTapped -> {
                answer { it.copy(edits = it.edits.copy(focusStats = OnboardingRules.swapFocusStat(_uiState.value.plan.focusStats, event.stat))) }
                refreshQuests()
            }
            is OnboardingFlowEvent.PlanStepGoalChanged -> answer { it.copy(edits = it.edits.copy(stepGoal = event.goal)) }
            is OnboardingFlowEvent.PlanFocusGoalChanged -> answer { it.copy(edits = it.edits.copy(focusGoalMinutes = event.minutes)) }
            is OnboardingFlowEvent.PlanAppLimitChanged -> answer { it.copy(edits = it.edits.copy(appLimitMinutes = event.minutes)) }
            is OnboardingFlowEvent.PlanTrainingDayToggled -> answer {
                val days = OnboardingRules.toggleDay(_uiState.value.plan.trainingDays, event.isoDay)
                it.copy(edits = it.edits.copy(trainingDays = days))
            }
            is OnboardingFlowEvent.TargetChosen -> answer {
                val target = WeightTargetRules.target(event.weightKg, event.weeks, _uiState.value.todayDate)
                // A target that breaks the rules is not kept (the screen only offers valid ones; this is the guard).
                if (WeightTargetRules.problems(target, it, _uiState.value.todayDate).isEmpty()) it.copy(target = target) else it
            }
            OnboardingFlowEvent.TargetCleared -> answer { it.copy(target = null) }
            OnboardingFlowEvent.Start -> start()
        }
    }

    /** Works out the first plan's quests. The ring on the building screen waits for this, so what it says is real. */
    private fun startBuild() {
        buildJob?.cancel()
        _uiState.update { it.copy(build = PlanBuild.Running) }
        buildJob = viewModelScope.launch { _uiState.update { it.copy(build = computeQuests(it)) } }
    }

    /** After an edit that changes the quests: the old list stays up until the new one is ready. */
    private fun refreshQuests() {
        buildJob?.cancel()
        buildJob = viewModelScope.launch { _uiState.update { it.copy(build = computeQuests(it)) } }
    }

    private suspend fun computeQuests(state: OnboardingFlowState): PlanBuild = try {
        PlanBuild.Ready(previewPlan(state.answers, state.grants).quests)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Working out the first plan failed")
        PlanBuild.Failed
    }

    private fun start() {
        val state = _uiState.value
        if (state.step != OnboardingStep.PLAN || state.committing || state.build == PlanBuild.Running) return
        _uiState.update { it.copy(committing = true, commitFailed = false) }
        viewModelScope.launch {
            val result = try {
                // The profile row closes this screen, so the rest of the save must not be cancelled with the ViewModel.
                withContext(NonCancellable) { completeOnboarding(state.answers, state.grants, state.notificationsAsked) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Saving the plan failed")
                null
            }
            when (result) {
                // The app switches to Today as soon as the profile exists; the screen stays disabled until it does.
                OnboardingResult.Done -> Unit
                OnboardingResult.InvalidName -> change { it.copy(committing = false, step = OnboardingStep.NAME, forward = false) }
                OnboardingResult.InvalidHunterId, null -> _uiState.update { it.copy(committing = false, commitFailed = true) }
            }
        }
    }

    private fun fromDraft(draft: OnboardingDraft?): OnboardingFlowState {
        val (lengthUnit, massUnit) = BodyMath.defaultUnits(country)
        val saved = draft?.answers ?: OnboardingAnswers(heightUnit = lengthUnit, weightUnit = massUnit)
        val answers = if (saved.hunterId.isBlank()) saved.copy(hunterId = newId()) else saved
        val step = draft?.let { OnboardingStep.entries.getOrNull(it.step) } ?: OnboardingStep.WELCOME
        return OnboardingFlowState(loaded = true, step = step, forward = true, answers = answers, todayDate = today())
    }

    private fun next(skip: Boolean) {
        val before = _uiState.value.step
        change { state ->
            when {
                skip && !state.step.skippable -> state
                !skip && !state.canContinue -> state
                else -> {
                    val answers = if (skip) OnboardingRules.skipped(state.step, state.answers) else OnboardingRules.withShownDefaults(state.step, state.answers)
                    state.copy(step = nextStep(state), forward = true, answers = answers)
                }
            }
        }
        // Arriving on the building screen is what starts the work it shows.
        if (before != OnboardingStep.BUILDING && _uiState.value.step == OnboardingStep.BUILDING) startBuild()
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
        if (!state.loaded || previewOnly) return
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
