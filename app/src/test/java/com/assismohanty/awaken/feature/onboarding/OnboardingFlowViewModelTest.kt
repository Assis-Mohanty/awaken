package com.assismohanty.awaken.feature.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.assismohanty.awaken.core.data.repo.OnboardingDraftStore
import com.assismohanty.awaken.engine.onboarding.IllustrationSet
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
import org.junit.Before
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
    private var ids = 0

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
    }

    /** The draft is read from disk, so the flow is not usable until `loaded`; wait for it as the screen does. */
    private fun viewModel(country: String? = "IN", drafts: OnboardingDraftStore = store): OnboardingFlowViewModel {
        val vm = OnboardingFlowViewModel(drafts, country) { "hunter-${++ids}" }
        runBlocking { withTimeout(5_000) { while (!vm.state().loaded) delay(10) } }
        return vm
    }

    private fun OnboardingFlowViewModel.state() = uiState.value

    /** The draft is written from a background coroutine, so wait for it instead of assuming it is there. */
    private fun awaitDraft(check: (com.assismohanty.awaken.core.data.repo.OnboardingDraft) -> Boolean) = runBlocking {
        withTimeout(5_000) {
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

    @Test
    fun aSavedStepThatNoLongerExistsRestartsAtWelcome() {
        runBlocking { store.save(com.assismohanty.awaken.core.data.repo.OnboardingDraft(step = 999)) }
        assertThat(viewModel().state().step).isEqualTo(OnboardingStep.WELCOME)
    }
}
