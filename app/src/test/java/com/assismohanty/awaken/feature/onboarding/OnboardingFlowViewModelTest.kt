package com.assismohanty.awaken.feature.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.assismohanty.awaken.core.data.repo.OnboardingDraftStore
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.core.data.repo.OnboardingDraft
import com.assismohanty.awaken.engine.onboarding.EquipmentChoice
import com.assismohanty.awaken.engine.onboarding.FitnessLevel
import com.assismohanty.awaken.engine.onboarding.IllustrationSet
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import com.assismohanty.awaken.engine.onboarding.Pace
import com.assismohanty.awaken.engine.onboarding.ScreenTimeBand
import com.assismohanty.awaken.feature.settings.HealthAction
import com.assismohanty.awaken.testing.FakeOnboardingPermissions
import com.assismohanty.awaken.core.domain.OnboardingPlanPreview
import com.assismohanty.awaken.core.domain.OnboardingQuestLine
import com.assismohanty.awaken.core.domain.OnboardingResult
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.OnboardingGrants
import com.assismohanty.awaken.engine.onboarding.OnboardingPlanner
import com.assismohanty.awaken.engine.onboarding.WeightTargetRules
import java.time.DayOfWeek
import java.time.LocalDate
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.LengthUnit
import com.assismohanty.awaken.engine.onboarding.MassUnit
import com.assismohanty.awaken.engine.onboarding.Obstacle
import com.assismohanty.awaken.engine.onboarding.OnboardingStep
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFlowViewModelTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun newStore() = OnboardingDraftStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(Files.createTempDirectory("onboarding-flow").toFile(), "draft.preferences_pb")
        },
    )
    private val store = newStore()
    private val perms = FakeOnboardingPermissions()
    private var ids = 0

    /**
     * Main is set once for the class and never reset between tests: a finished test's ViewModel can still be resuming on it from
     * a background thread, and changing it underneath those coroutines fails the next test with "used concurrently with setting it".
     */
    @After
    fun tearDown() {
        scope.cancel()
    }

    private companion object {
        @BeforeClass
        @JvmStatic
        fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

        @AfterClass
        @JvmStatic
        fun resetMain() = Dispatchers.resetMain()
    }

    /** The draft is read from disk, so the flow is not usable until `loaded`; wait for it as the screen does. */
    private fun viewModel(
        country: String? = "IN",
        drafts: OnboardingDraftStore = store,
        permissions: FakeOnboardingPermissions = perms,
    ): OnboardingFlowViewModel {
        val vm = OnboardingFlowViewModel(
            drafts = drafts,
            country = country,
            newId = { "hunter-${++ids}" },
            permissions = permissions,
            today = { today },
            previewPlan = { answers, grants ->
                previews += answers
                if (previewFails) error("preview failed")
                OnboardingPlanPreview(OnboardingPlanner.plan(answers, grants, today), previewQuests)
            },
            completeOnboarding = { answers, grants, asked ->
                completed += Triple(answers, grants, asked)
                if (completeThrows) error("save failed")
                completeResult
            },
        )
        runBlocking { withTimeout(30_000) { while (!vm.state().loaded) delay(10) } }
        return vm
    }

    private val today = LocalDate.of(2026, 11, 3)
    private val previewQuests = listOf(OnboardingQuestLine("Walk 6,000 steps", Stat.VIT, Difficulty.EASY))
    private val previews = mutableListOf<OnboardingAnswers>()
    private var previewFails = false
    private val completed = mutableListOf<Triple<OnboardingAnswers, OnboardingGrants, Boolean>>()
    private var completeResult: OnboardingResult = OnboardingResult.Done
    private var completeThrows = false

    private fun OnboardingFlowViewModel.state() = uiState.value

    /** The draft is written from a background coroutine, so wait for it instead of assuming it is there. */
    private fun awaitDraft(check: (com.assismohanty.awaken.core.data.repo.OnboardingDraft) -> Boolean) = runBlocking {
        withTimeout(30_000) {
            while (store.load()?.let(check) != true) delay(10)
        }
    }

    private fun OnboardingFlowViewModel.goTo(step: OnboardingStep) {
        onEvent(OnboardingFlowEvent.NameChanged("Sam"))
        onEvent(OnboardingFlowEvent.GenderSelected(IllustrationSet.NEUTRAL))
        var guard = 0
        while (state().step != step && guard++ < OnboardingStep.entries.size) {
            // Answer whatever the current step needs, then move on.
            when (state().step) {
                OnboardingStep.IMPROVE -> onEvent(OnboardingFlowEvent.ImproveToggled(ImproveGoal.SLEEP))
                OnboardingStep.OBSTACLES -> onEvent(OnboardingFlowEvent.ObstacleToggled(Obstacle.NONE))
                OnboardingStep.SCREEN_TIME -> onEvent(OnboardingFlowEvent.ScreenTimeSelected(ScreenTimeBand.H2_TO_4))
                OnboardingStep.FITNESS -> onEvent(OnboardingFlowEvent.FitnessSelected(FitnessLevel.BEGINNER))
                OnboardingStep.EQUIPMENT -> onEvent(OnboardingFlowEvent.EquipmentSelected(EquipmentChoice.BODYWEIGHT))
                OnboardingStep.INJURIES -> onEvent(OnboardingFlowEvent.InjuryToggled(InjuryRegion.NO_INJURIES))
                OnboardingStep.PACE -> onEvent(OnboardingFlowEvent.PaceSelected(Pace.BALANCED))
                else -> Unit
            }
            onEvent(OnboardingFlowEvent.Next)
        }
        assertThat(state().step).isEqualTo(step)
    }

    @Test
    fun aFreshFlowStartsAtWelcomeWithAnIdAndUnitsFromTheLocale() {
        val india = viewModel("IN").state()
        assertThat(india.loaded).isTrue()
        assertThat(india.step).isEqualTo(OnboardingStep.WELCOME)
        assertThat(india.answers.hunterId).isNotEmpty()
        assertThat(india.answers.heightUnit).isEqualTo(LengthUnit.CM)
        assertThat(india.answers.weightUnit).isEqualTo(MassUnit.KG)
        val us = viewModel("US", newStore()).state()
        assertThat(us.answers.heightUnit).isEqualTo(LengthUnit.FEET_INCHES)
        assertThat(us.answers.weightUnit).isEqualTo(MassUnit.LB)
    }

    @Test
    fun nextMovesForwardAndBackReturnsWithoutLosingAnswers() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.NAME)
        assertThat(vm.state().forward).isTrue()
        vm.onEvent(OnboardingFlowEvent.NameChanged("Sam"))
        vm.onEvent(OnboardingFlowEvent.Next)
        vm.onEvent(OnboardingFlowEvent.Back)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.NAME)
        assertThat(vm.state().forward).isFalse()
        assertThat(vm.state().answers.name).isEqualTo("Sam")
    }

    @Test
    fun theFirstScreenHasNoBack() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.Back)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.WELCOME)
    }

    @Test
    fun theNameGatesContinueAndCannotBeSkipped() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.Next)
        vm.onEvent(OnboardingFlowEvent.NameChanged("Al"))
        assertThat(vm.state().canContinue).isFalse()
        vm.onEvent(OnboardingFlowEvent.Next)
        vm.onEvent(OnboardingFlowEvent.Skip)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.NAME)
        vm.onEvent(OnboardingFlowEvent.NameChanged("Sam"))
        assertThat(vm.state().canContinue).isTrue()
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.GENDER)
    }

    @Test
    fun aLongPastedNameIsCutShort() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.NameChanged("x".repeat(100)))
        assertThat(vm.state().answers.name).hasLength(40)
    }

    @Test
    fun genderNeedsAChoiceOrASkip_andSkipForgetsIt() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.GENDER)
        vm.onEvent(OnboardingFlowEvent.Back)
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.GENDER)
        vm.onEvent(OnboardingFlowEvent.GenderSelected(IllustrationSet.FEMALE))
        assertThat(vm.state().answers.illustrationSet).isEqualTo(IllustrationSet.FEMALE)
        vm.onEvent(OnboardingFlowEvent.Skip)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.AGE)
        assertThat(vm.state().answers.illustrationSet).isNull()
    }

    @Test
    fun continuingAPickerUntouchedAnswersWithTheStartingValue() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.AGE)
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().answers.age).isEqualTo(25)
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().answers.heightCm).isEqualTo(170.0)
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().answers.weightKg).isEqualTo(70.0)
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().answers.bodyStage).isEqualTo(3)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.IMPROVE)
    }

    @Test
    fun skippingAPickerLeavesItUnanswered() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.AGE)
        vm.onEvent(OnboardingFlowEvent.AgeChanged(40))
        vm.onEvent(OnboardingFlowEvent.Skip)
        assertThat(vm.state().answers.age).isNull()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.HEIGHT)
    }

    @Test
    fun theAgeStaysInsideTheSupportedRange() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.AgeChanged(5))
        assertThat(vm.state().answers.age).isEqualTo(13)
        vm.onEvent(OnboardingFlowEvent.AgeChanged(250))
        assertThat(vm.state().answers.age).isEqualTo(100)
    }

    @Test
    fun switchingUnitsNeverChangesTheStoredValues() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.HeightChanged(172.72))
        vm.onEvent(OnboardingFlowEvent.WeightChanged(70.5))
        vm.onEvent(OnboardingFlowEvent.HeightUnitChanged(LengthUnit.FEET_INCHES))
        vm.onEvent(OnboardingFlowEvent.WeightUnitChanged(MassUnit.LB))
        vm.onEvent(OnboardingFlowEvent.HeightUnitChanged(LengthUnit.CM))
        vm.onEvent(OnboardingFlowEvent.WeightUnitChanged(MassUnit.KG))
        assertThat(vm.state().answers.heightCm).isEqualTo(172.72)
        assertThat(vm.state().answers.weightKg).isEqualTo(70.5)
    }

    @Test
    fun theBodyShapeStageStaysBetweenOneAndFive() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.StageChanged(0))
        assertThat(vm.state().answers.bodyStage).isEqualTo(1)
        vm.onEvent(OnboardingFlowEvent.StageChanged(9))
        assertThat(vm.state().answers.bodyStage).isEqualTo(5)
    }

    @Test
    fun improveNeedsOneAndKeepsThePickOrder() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.IMPROVE)
        assertThat(vm.state().canContinue).isFalse()
        vm.onEvent(OnboardingFlowEvent.ImproveToggled(ImproveGoal.SLEEP))
        vm.onEvent(OnboardingFlowEvent.ImproveToggled(ImproveGoal.FOCUS))
        assertThat(vm.state().answers.improve).containsExactly(ImproveGoal.SLEEP, ImproveGoal.FOCUS).inOrder()
        vm.onEvent(OnboardingFlowEvent.ImproveToggled(ImproveGoal.SLEEP))
        assertThat(vm.state().answers.improve).containsExactly(ImproveGoal.FOCUS)
    }

    @Test
    fun noneIsExclusiveInObstacles() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.OBSTACLES)
        vm.onEvent(OnboardingFlowEvent.ObstacleToggled(Obstacle.POOR_SLEEP))
        vm.onEvent(OnboardingFlowEvent.ObstacleToggled(Obstacle.NONE))
        assertThat(vm.state().answers.obstacles).containsExactly(Obstacle.NONE)
        vm.onEvent(OnboardingFlowEvent.ObstacleToggled(Obstacle.PROCRASTINATION))
        assertThat(vm.state().answers.obstacles).containsExactly(Obstacle.PROCRASTINATION)
    }

    @Test
    fun everyChangeIsSavedAndAFreshViewModelResumesFromIt() {
        val first = viewModel()
        first.goTo(OnboardingStep.IMPROVE)
        first.onEvent(OnboardingFlowEvent.ImproveToggled(ImproveGoal.LEARNING))
        val id = first.state().answers.hunterId
        awaitDraft { it.step == OnboardingStep.IMPROVE.ordinal && it.answers.improve == listOf(ImproveGoal.LEARNING) }

        // The app was killed: a new ViewModel reads the same draft.
        val second = viewModel("US")
        assertThat(second.state().step).isEqualTo(OnboardingStep.IMPROVE)
        assertThat(second.state().answers.name).isEqualTo("Sam")
        assertThat(second.state().answers.improve).containsExactly(ImproveGoal.LEARNING)
        assertThat(second.state().answers.hunterId).isEqualTo(id)
        // The saved units are kept, not replaced by the new locale's.
        assertThat(second.state().answers.heightUnit).isEqualTo(LengthUnit.CM)
    }

    @Test
    fun typingTheNameIsSavedBeforeContinue() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.Next)
        vm.onEvent(OnboardingFlowEvent.NameChanged("Sam"))
        awaitDraft { it.step == OnboardingStep.NAME.ordinal && it.answers.name == "Sam" }
    }

    private fun await(check: () -> Boolean) = runBlocking { withTimeout(30_000) { while (!check()) delay(10) } }

    @Test
    fun theLaterQuestionsRecordTheirAnswers() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.SCREEN_TIME)
        vm.onEvent(OnboardingFlowEvent.ScreenTimeSelected(ScreenTimeBand.OVER_6H))
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().answers.screenTime).isEqualTo(ScreenTimeBand.OVER_6H)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.SLEEP)
        vm.onEvent(OnboardingFlowEvent.Next)
        // Sleep passed untouched records the starting window.
        assertThat(vm.state().answers.bedtimeMinute).isEqualTo(23 * 60)
        assertThat(vm.state().answers.wakeMinute).isEqualTo(6 * 60 + 30)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.FITNESS)
        assertThat(vm.state().canContinue).isFalse()
        vm.onEvent(OnboardingFlowEvent.FitnessSelected(FitnessLevel.ADVANCED))
        vm.onEvent(OnboardingFlowEvent.Next)
        vm.onEvent(OnboardingFlowEvent.EquipmentSelected(EquipmentChoice.GYM))
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.INJURIES)
        assertThat(vm.state().canContinue).isFalse()
        vm.onEvent(OnboardingFlowEvent.InjuryToggled(InjuryRegion.KNEE))
        vm.onEvent(OnboardingFlowEvent.InjuryToggled(InjuryRegion.NO_INJURIES))
        assertThat(vm.state().answers.injuries).containsExactly(InjuryRegion.NO_INJURIES)
        vm.onEvent(OnboardingFlowEvent.InjuryToggled(InjuryRegion.HIP))
        assertThat(vm.state().answers.injuries).containsExactly(InjuryRegion.HIP)
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.TRAINING_DAYS)
        // Training days can be left empty: Continue is always on.
        assertThat(vm.state().canContinue).isTrue()
        vm.onEvent(OnboardingFlowEvent.Next)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.PACE)
        assertThat(vm.state().canContinue).isFalse()
        vm.onEvent(OnboardingFlowEvent.PaceSelected(Pace.PUSH_HARD))
        assertThat(vm.state().canContinue).isTrue()
    }

    @Test
    fun movingTheBedtimeMovesAWakeTimeThatNoLongerFits() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.WakeChanged(6 * 60 + 30))
        // WakeChanged alone supplies the default bedtime.
        assertThat(vm.state().answers.bedtimeMinute).isEqualTo(23 * 60)
        vm.onEvent(OnboardingFlowEvent.BedtimeChanged(2 * 60))
        // 02:00 to 06:30 is 4.5 hours, under the 5-hour minimum.
        assertThat(vm.state().answers.bedtimeMinute).isEqualTo(2 * 60)
        assertThat(vm.state().answers.wakeMinute).isEqualTo(7 * 60)
    }

    @Test
    fun skippingSleepForgetsTheWindow() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.SLEEP)
        vm.onEvent(OnboardingFlowEvent.BedtimeChanged(22 * 60))
        vm.onEvent(OnboardingFlowEvent.Skip)
        assertThat(vm.state().answers.bedtimeMinute).isNull()
        assertThat(vm.state().answers.wakeMinute).isNull()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.FITNESS)
    }

    @Test
    fun theTrainingReminderGoesWhenTheLastDayIsRemoved() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.TrainingDayToggled(1))
        vm.onEvent(OnboardingFlowEvent.TrainingReminderChanged(true))
        assertThat(vm.state().answers.trainingReminder).isTrue()
        vm.onEvent(OnboardingFlowEvent.TrainingDayToggled(1))
        assertThat(vm.state().answers.trainingDays).isEmpty()
        assertThat(vm.state().answers.trainingReminder).isFalse()
    }

    @Test
    fun todayIsKnownForTheDayChips() {
        assertThat(viewModel().state().today).isEqualTo(DayOfWeek.TUESDAY)
    }

    // --- Permission screens ---

    @Test
    fun healthConnectAsksForItsPermissionsWhenAvailable() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.HEALTH)
        vm.onEvent(OnboardingFlowEvent.ConnectHealth)
        await { vm.state().healthAction != null }
        assertThat(vm.state().healthAction).isEqualTo(HealthAction.REQUEST_PERMISSIONS)
        assertThat(vm.state().healthPermissions).isEqualTo(perms.healthPermissions)
        vm.onEvent(OnboardingFlowEvent.HealthActionHandled)
        assertThat(vm.state().healthAction).isNull()
    }

    @Test
    fun healthConnectNotInstalledOpensTheStoreAndSaysSo() {
        perms.status = HealthStatus.NOT_INSTALLED
        val vm = viewModel()
        vm.goTo(OnboardingStep.HEALTH)
        vm.onEvent(OnboardingFlowEvent.ConnectHealth)
        await { vm.state().healthAction != null }
        assertThat(vm.state().healthAction).isEqualTo(HealthAction.OPEN_STORE)
        assertThat(vm.state().permissions.healthNotInstalled).isTrue()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.HEALTH)
    }

    @Test
    fun allowingHealthMovesOnAndRecordsIt() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.HEALTH)
        vm.onEvent(OnboardingFlowEvent.HealthPermissionsResult(perms.healthPermissions))
        assertThat(vm.state().answers.healthConnected).isTrue()
        assertThat(vm.state().permissions.health).isTrue()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.USAGE)
    }

    @Test
    fun decliningHealthStaysAndNotNowStillMovesOn() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.HEALTH)
        // Only some of the core permissions were granted.
        vm.onEvent(OnboardingFlowEvent.HealthPermissionsResult(setOf("read.steps")))
        assertThat(vm.state().answers.healthConnected).isFalse()
        assertThat(vm.state().permissions.healthDeclined).isTrue()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.HEALTH)
        vm.onEvent(OnboardingFlowEvent.Skip)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.USAGE)
    }

    @Test
    fun comingBackFromUsageSettingsWithAccessCarriesOn() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.USAGE)
        vm.onEvent(OnboardingFlowEvent.OpenUsageSettings)
        assertThat(vm.state().openUsageSettings).isTrue()
        vm.onEvent(OnboardingFlowEvent.UsageSettingsHandled)
        assertThat(vm.state().openUsageSettings).isFalse()
        perms.usage = true
        vm.onEvent(OnboardingFlowEvent.RecheckPermissions)
        await { vm.state().step == OnboardingStep.NOTIFICATIONS }
        assertThat(vm.state().permissions.usage).isTrue()
    }

    @Test
    fun comingBackFromUsageSettingsWithoutAccessStaysPut() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.USAGE)
        vm.onEvent(OnboardingFlowEvent.OpenUsageSettings)
        vm.onEvent(OnboardingFlowEvent.RecheckPermissions)
        Thread.sleep(150)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.USAGE)
        assertThat(vm.state().permissions.usage).isFalse()
    }

    @Test
    fun aRecheckNeverPushesSomeoneWhoWentBackToAnAllowedScreen() {
        perms.usage = true
        val vm = viewModel()
        vm.goTo(OnboardingStep.USAGE)
        vm.onEvent(OnboardingFlowEvent.RecheckPermissions)
        await { vm.state().permissions.usage }
        // Access was already there and the person never went to settings from this screen: no automatic move.
        assertThat(vm.state().step).isEqualTo(OnboardingStep.USAGE)
        assertThat(vm.state().stepPermissionGranted).isTrue()
    }

    @Test
    fun notificationsAreAskedForAndTheFlowMovesOnWhateverTheAnswer() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.NOTIFICATIONS)
        vm.onEvent(OnboardingFlowEvent.RequestNotifications)
        assertThat(vm.state().requestNotifications).isTrue()
        vm.onEvent(OnboardingFlowEvent.NotificationsHandled)
        assertThat(vm.state().requestNotifications).isFalse()
        vm.onEvent(OnboardingFlowEvent.NotificationsResult(granted = false))
        assertThat(vm.state().permissions.notifications).isFalse()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.BUILDING)
    }

    @Test
    fun theNotificationsScreenIsLeftOutWhenThereIsNothingToAsk() {
        perms.askable = false
        perms.notifications = true
        val vm = viewModel()
        await { !vm.state().permissions.notificationsAskable }
        assertThat(vm.state().visibleSteps).doesNotContain(OnboardingStep.NOTIFICATIONS)
        vm.goTo(OnboardingStep.USAGE)
        vm.onEvent(OnboardingFlowEvent.Skip)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.BUILDING)
    }

    @Test
    fun backFromThePlanSkipsTheBuildingScreenAndAnyHiddenPermissionScreen() {
        perms.askable = false
        runBlocking { store.save(OnboardingDraft(step = OnboardingStep.PLAN.ordinal)) }
        val vm = viewModel()
        await { !vm.state().permissions.notificationsAskable }
        vm.onEvent(OnboardingFlowEvent.Back)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.USAGE)
        assertThat(vm.state().forward).isFalse()
    }

    @Test
    fun allowedHealthIsReadFromThePhoneOnResume() {
        perms.health = true
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.RecheckPermissions)
        await { vm.state().permissions.health }
        assertThat(vm.state().answers.healthConnected).isTrue()
    }

    // --- Building and the plan ---

    /** Answers everything, skips the three permission screens and lands on the building screen. */
    private fun OnboardingFlowViewModel.goToBuilding() {
        goTo(OnboardingStep.HEALTH)
        repeat(3) { onEvent(OnboardingFlowEvent.Skip) }
        assertThat(state().step).isEqualTo(OnboardingStep.BUILDING)
    }

    private fun OnboardingFlowViewModel.goToPlan() {
        goToBuilding()
        await { state().build is PlanBuild.Ready }
        onEvent(OnboardingFlowEvent.BuildFinished)
        assertThat(state().step).isEqualTo(OnboardingStep.PLAN)
    }

    @Test
    fun arrivingAtTheBuildingScreenWorksOutTheFirstQuests() {
        val vm = viewModel()
        vm.goToBuilding()
        await { vm.state().build is PlanBuild.Ready }
        assertThat((vm.state().build as PlanBuild.Ready).quests).isEqualTo(previewQuests)
        // The answers it worked from are the ones given.
        assertThat(previews.last().name).isEqualTo("Sam")
    }

    @Test
    fun theBuildingScreenOnlyMovesOnOnceTheQuestsAreReady() {
        val vm = viewModel()
        vm.goToBuilding()
        await { vm.state().build is PlanBuild.Ready }
        // Before the ring finishes nothing moves; the screen reports it when it does.
        assertThat(vm.state().step).isEqualTo(OnboardingStep.BUILDING)
        vm.onEvent(OnboardingFlowEvent.BuildFinished)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.PLAN)
        assertThat(vm.state().forward).isTrue()
    }

    @Test
    fun aFailedBuildStaysAndRetryWorks() {
        previewFails = true
        val vm = viewModel()
        vm.goToBuilding()
        await { vm.state().build == PlanBuild.Failed }
        vm.onEvent(OnboardingFlowEvent.BuildFinished)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.BUILDING)
        previewFails = false
        vm.onEvent(OnboardingFlowEvent.RetryBuild)
        await { vm.state().build is PlanBuild.Ready }
    }

    @Test
    fun buildingHasNoBack() {
        val vm = viewModel()
        vm.goToBuilding()
        vm.onEvent(OnboardingFlowEvent.Back)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.BUILDING)
    }

    @Test
    fun backFromThePlanGoesToTheLastPermissionScreen() {
        val vm = viewModel()
        vm.goToPlan()
        vm.onEvent(OnboardingFlowEvent.Back)
        assertThat(vm.state().step).isEqualTo(OnboardingStep.NOTIFICATIONS)
    }

    @Test
    fun theQuestsAreWorkedOutAgainWhenTheBuildingScreenIsReachedAgain() {
        val vm = viewModel()
        vm.goToPlan()
        val before = previews.size
        // Back to the fitness screen, change an answer, then forward again to building.
        while (vm.state().step != OnboardingStep.FITNESS) vm.onEvent(OnboardingFlowEvent.Back)
        vm.onEvent(OnboardingFlowEvent.FitnessSelected(FitnessLevel.ADVANCED))
        // Equipment, injuries and pace are already answered, so Next carries on to the permission screens.
        while (vm.state().step != OnboardingStep.HEALTH) vm.onEvent(OnboardingFlowEvent.Next)
        repeat(3) { vm.onEvent(OnboardingFlowEvent.Skip) }
        await { previews.size > before }
        assertThat(previews.last().fitness).isEqualTo(FitnessLevel.ADVANCED)
    }

    @Test
    fun aPersonWhoWasOnThePlanWhenTheAppClosedGetsItBack() {
        runBlocking { store.save(OnboardingDraft(step = OnboardingStep.PLAN.ordinal, answers = OnboardingAnswers(hunterId = "h", name = "Sam"))) }
        val vm = viewModel()
        await { vm.state().build is PlanBuild.Ready }
        assertThat(vm.state().step).isEqualTo(OnboardingStep.PLAN)
    }

    @Test
    fun planEditsChangeTheValuesAtOnceAndAreSavedInTheDraft() {
        val vm = viewModel()
        vm.goToPlan()
        // The walk answered Beginner, which sets 6,000 steps.
        assertThat(vm.state().plan.stepGoal).isEqualTo(6_000)
        vm.onEvent(OnboardingFlowEvent.PlanStepGoalChanged(9_500))
        assertThat(vm.state().plan.stepGoal).isEqualTo(9_500)
        vm.onEvent(OnboardingFlowEvent.PlanFocusGoalChanged(75))
        assertThat(vm.state().plan.focusGoalMinutes).isEqualTo(75)
        vm.onEvent(OnboardingFlowEvent.PlanAppLimitChanged(40))
        assertThat(vm.state().answers.edits.appLimitMinutes).isEqualTo(40)
        // Edits are part of the answers, so they are saved with them.
        awaitDraft { it.answers.edits.stepGoal == 9_500 && it.answers.edits.focusGoalMinutes == 75 }
    }

    @Test
    fun tappingANewFocusAreaReplacesTheOlderOneAndRefreshesTheQuests() {
        val vm = viewModel()
        vm.goToPlan()
        // The walk picked Sleep, so Vitality leads and Strength fills the second place.
        assertThat(vm.state().plan.focusStats).containsExactly(Stat.VIT, Stat.STR).inOrder()
        val before = previews.size
        vm.onEvent(OnboardingFlowEvent.PlanFocusStatTapped(Stat.CHA))
        assertThat(vm.state().plan.focusStats).containsExactly(Stat.STR, Stat.CHA).inOrder()
        await { previews.size > before }
        vm.onEvent(OnboardingFlowEvent.PlanFocusStatTapped(Stat.CHA))
        assertThat(vm.state().plan.focusStats).containsExactly(Stat.STR, Stat.CHA).inOrder()
    }

    @Test
    fun planTrainingDaysCanBeToggledStartingFromTheAnswers() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.TrainingDayToggled(1))
        vm.goToPlan()
        assertThat(vm.state().plan.trainingDays).containsExactly(1)
        vm.onEvent(OnboardingFlowEvent.PlanTrainingDayToggled(3))
        assertThat(vm.state().plan.trainingDays).containsExactly(1, 3)
        vm.onEvent(OnboardingFlowEvent.PlanTrainingDayToggled(1))
        assertThat(vm.state().plan.trainingDays).containsExactly(3)
    }

    @Test
    fun theLimitsAndNightsFollowUsageAccess() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.HEALTH)
        vm.onEvent(OnboardingFlowEvent.RecheckPermissions)
        vm.goToPlan()
        assertThat(vm.state().plan.discipline).isNull()
        assertThat(vm.state().plan.needsUsageAccess).isTrue()
        perms.usage = true
        vm.onEvent(OnboardingFlowEvent.RecheckPermissions)
        await { vm.state().permissions.usage }
        assertThat(vm.state().plan.discipline).isNotNull()
        assertThat(vm.state().plan.needsUsageAccess).isFalse()
    }

    @Test
    fun aTargetIsKeptOnlyWhenItFollowsTheRules() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.AgeChanged(30))
        vm.onEvent(OnboardingFlowEvent.HeightChanged(170.0))
        vm.onEvent(OnboardingFlowEvent.WeightChanged(80.0))
        vm.onEvent(OnboardingFlowEvent.TargetChosen(70.0, 8))
        assertThat(vm.state().answers.target).isEqualTo(WeightTargetRules.target(70.0, 8, today))
        // Below BMI 18.5 for this height (53.5 kg): not kept.
        vm.onEvent(OnboardingFlowEvent.TargetChosen(50.0, 8))
        assertThat(vm.state().answers.target?.weightKg).isEqualTo(70.0)
        vm.onEvent(OnboardingFlowEvent.TargetCleared)
        assertThat(vm.state().answers.target).isNull()
    }

    @Test
    fun noTargetIsKeptForSomeoneUnder18() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.AgeChanged(16))
        vm.onEvent(OnboardingFlowEvent.HeightChanged(170.0))
        vm.onEvent(OnboardingFlowEvent.WeightChanged(60.0))
        vm.onEvent(OnboardingFlowEvent.TargetChosen(55.0, 8))
        assertThat(vm.state().answers.target).isNull()
    }

    @Test
    fun startSavesTheAnswersWithTheGrantsAndWhetherNotificationsWereAsked() {
        val vm = viewModel()
        vm.goTo(OnboardingStep.HEALTH)
        vm.onEvent(OnboardingFlowEvent.HealthPermissionsResult(perms.healthPermissions))
        vm.onEvent(OnboardingFlowEvent.Skip)
        vm.onEvent(OnboardingFlowEvent.NotificationsResult(granted = true))
        await { vm.state().build is PlanBuild.Ready }
        vm.onEvent(OnboardingFlowEvent.BuildFinished)
        vm.onEvent(OnboardingFlowEvent.Start)
        await { completed.isNotEmpty() }
        val (answers, grants, asked) = completed.single()
        assertThat(answers.name).isEqualTo("Sam")
        assertThat(grants.healthConnected).isTrue()
        assertThat(grants.usageAccess).isFalse()
        assertThat(asked).isTrue()
        assertThat(vm.state().commitFailed).isFalse()
    }

    @Test
    fun startIsIgnoredAwayFromThePlanAndWhileSaving() {
        val vm = viewModel()
        vm.onEvent(OnboardingFlowEvent.Start)
        assertThat(completed).isEmpty()
        vm.goToPlan()
        vm.onEvent(OnboardingFlowEvent.Start)
        vm.onEvent(OnboardingFlowEvent.Start)
        await { completed.isNotEmpty() }
        Thread.sleep(100)
        assertThat(completed).hasSize(1)
    }

    @Test
    fun aFailedSaveShowsAnErrorAndCanBeRetriedWithTheAnswersKept() {
        completeThrows = true
        val vm = viewModel()
        vm.goToPlan()
        vm.onEvent(OnboardingFlowEvent.Start)
        await { vm.state().commitFailed }
        assertThat(vm.state().committing).isFalse()
        assertThat(vm.state().step).isEqualTo(OnboardingStep.PLAN)
        assertThat(vm.state().answers.name).isEqualTo("Sam")
        completeThrows = false
        vm.onEvent(OnboardingFlowEvent.Start)
        await { completed.size == 2 }
        assertThat(vm.state().commitFailed).isFalse()
    }

    @Test
    fun aNameThatTheSaveRejectsSendsThePersonBackToTheNameStep() {
        completeResult = OnboardingResult.InvalidName
        val vm = viewModel()
        vm.goToPlan()
        vm.onEvent(OnboardingFlowEvent.Start)
        await { vm.state().step == OnboardingStep.NAME }
        assertThat(vm.state().committing).isFalse()
    }

    @Test
    fun aSavedStepThatNoLongerExistsRestartsAtWelcome() {
        runBlocking { store.save(com.assismohanty.awaken.core.data.repo.OnboardingDraft(step = 999)) }
        assertThat(viewModel().state().step).isEqualTo(OnboardingStep.WELCOME)
    }
}
