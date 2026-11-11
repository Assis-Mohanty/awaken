package com.assismohanty.awaken.feature.diet

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.components.ScanFrame
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.LoadHealthOverviewUseCase
import com.assismohanty.awaken.core.scan.ScannedCode
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.OcrWord
import com.assismohanty.awaken.testing.FakeLabelReader
import com.assismohanty.awaken.testing.FakePhoneStepsGateway
import com.assismohanty.awaken.testing.NoDietReminders
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Photographing a nutrition table: the flow through the ViewModel, the photo screen and the prefilled label form. */
@RunWith(AndroidJUnit4::class)
class DietLabelPhotoTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val graph = TestGraph()
    private val reader = FakeLabelReader()
    private val events = mutableListOf<DietEvent>()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        graph.close()
    }

    private fun viewModel() = DietViewModel(
        graph.diet, graph.foodSearch, graph.foodLibrary, graph.bodyLog, graph.uiPrefs, NoDietReminders, graph.recipeBook,
        LoadHealthOverviewUseCase(graph.db.hunterDao(), graph.health, FakePhoneStepsGateway(), graph.clock), graph.lookUpBarcode, reader,
    )

    private suspend fun DietViewModel.until(condition: (DietUiState) -> Boolean): DietUiState = withTimeout(5_000) { uiState.first(condition) }

    private fun photo(): Bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

    @Test
    fun aReadableTableFillsTheLabelFormAndTheBarcodeCarriesThrough() = runBlocking<Unit> {
        reader.words = FakeLabelReader.biscuitTable()
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenScanner)
        vm.onEvent(DietEvent.BarcodeRead(ScannedCode("5901234123457", upcE = false)))
        vm.until { it.scan is ScanState.NotFound }

        vm.onEvent(DietEvent.ScanLabelPhoto)
        vm.until { it.labelPhoto == LabelPhotoState.Capturing && it.scan == null }
        val taken = photo()
        vm.onEvent(DietEvent.LabelPhotoTaken(taken))

        val form = vm.until { it.search?.showCustomForm == true && it.labelPhoto == null }.search!!
        assertThat(form.prefillBarcode).isEqualTo("5901234123457")
        val prefill = form.prefill!!
        assertThat(prefill.per100[Nutrient.KCAL]).isEqualTo(480.0)
        assertThat(prefill.per100[Nutrient.PROTEIN]).isEqualTo(7.0)
        assertThat(prefill.per100[Nutrient.CARBS]).isEqualTo(64.0)
        assertThat(prefill.per100[Nutrient.FAT]).isEqualTo(21.0)
        assertThat(prefill.per100[Nutrient.SODIUM]).isEqualTo(400.0)
        assertThat(prefill.servingGrams).isEqualTo(30.0)
        assertThat(prefill.complete).isTrue()
        assertThat(prefill.numbersAddUp).isTrue()
        assertThat(prefill.perServingOnly).isFalse()
        // The photo is released as soon as it has been read; it is never kept.
        assertThat(taken.isRecycled).isTrue()
    }

    @Test
    fun eachWayAPhotoCanFailHasItsOwnReasonAndTryAgainGoesBackToTheCamera() = runBlocking<Unit> {
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenSearch("LUNCH"))
        vm.onEvent(DietEvent.OpenLabelPhoto)
        vm.until { it.labelPhoto == LabelPhotoState.Capturing }

        reader.words = null
        vm.onEvent(DietEvent.LabelPhotoTaken(photo()))
        assertThat(vm.until { it.labelPhoto is LabelPhotoState.Failed }.labelPhoto).isEqualTo(LabelPhotoState.Failed(LabelPhotoState.Reason.UNAVAILABLE))

        vm.onEvent(DietEvent.LabelPhotoRetry)
        vm.until { it.labelPhoto == LabelPhotoState.Capturing }
        reader.words = emptyList()
        vm.onEvent(DietEvent.LabelPhotoTaken(photo()))
        assertThat(vm.until { it.labelPhoto is LabelPhotoState.Failed }.labelPhoto).isEqualTo(LabelPhotoState.Failed(LabelPhotoState.Reason.NOTHING_READ))

        vm.onEvent(DietEvent.LabelPhotoRetry)
        vm.until { it.labelPhoto == LabelPhotoState.Capturing }
        reader.words = listOf(OcrWord("Best", 10, 10, 60, 30, 90), OcrWord("before", 70, 10, 130, 30, 90))
        vm.onEvent(DietEvent.LabelPhotoTaken(photo()))
        assertThat(vm.until { it.labelPhoto is LabelPhotoState.Failed }.labelPhoto).isEqualTo(LabelPhotoState.Failed(LabelPhotoState.Reason.NOT_A_TABLE))
        assertThat(vm.uiState.value.search?.prefill).isNull()

        vm.onEvent(DietEvent.LabelPhotoTaken(null))
        assertThat(vm.until { (it.labelPhoto as? LabelPhotoState.Failed)?.reason == LabelPhotoState.Reason.NOTHING_READ }.labelPhoto).isNotNull()
        vm.onEvent(DietEvent.CloseLabelPhoto)
        assertThat(vm.until { it.labelPhoto == null }.labelPhoto).isNull()
    }

    @Test
    fun numbersThatDoNotAddUpAreFlaggedForTheForm() = runBlocking<Unit> {
        // Calories far from what the macros give: the form is still filled in, but it warns.
        reader.words = FakeLabelReader.biscuitTable().map { if (it.text == "480") it.copy(text = "120") else it }
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenSearch("LUNCH"))
        vm.onEvent(DietEvent.OpenLabelPhoto)
        vm.onEvent(DietEvent.LabelPhotoTaken(photo()))

        val prefill = vm.until { it.search?.prefill != null }.search!!.prefill!!
        assertThat(prefill.numbersAddUp).isFalse()
    }

    private fun showForm(prefill: LabelPrefill) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FoodSearchContent(FoodSearch("LUNCH", showCustomForm = true, prefill = prefill), previewDietState().slots, emptyList()) { events += it }
                }
            }
        }
    }

    @Test
    fun theFormShowsWhatWasReadMarksTheUnsureNumbersAndWarnsWhenSomethingIsMissing() {
        showForm(
            LabelPrefill(
                per100 = mapOf(Nutrient.KCAL to 480.0, Nutrient.PROTEIN to 7.0, Nutrient.CARBS to 64.0),
                servingGrams = 30.0, unsure = setOf(Nutrient.PROTEIN), perServingOnly = false, numbersAddUp = false, complete = false,
            ),
        )
        composeRule.onNodeWithText("Read from your photo. Check every number against the pack before you save.").assertExists()
        composeRule.onNodeWithText("These numbers do not add up", substring = true).assertExists()
        composeRule.onNodeWithText("Some numbers were not found", substring = true).assertExists()
        composeRule.onNodeWithText("Check this one").performScrollTo().assertExists()
    }

    @Test
    fun theFilledInFormSavesTheReadNumbersPer100GramsWithTheServing() {
        showForm(
            LabelPrefill(
                per100 = mapOf(Nutrient.KCAL to 480.0, Nutrient.PROTEIN to 7.0, Nutrient.CARBS to 64.0, Nutrient.FAT to 21.0, Nutrient.SODIUM to 400.0),
                servingGrams = 30.0, unsure = emptySet(), perServingOnly = false, numbersAddUp = true, complete = true,
            ),
        )
        composeRule.onNodeWithText("Food name").performScrollTo()
        // A name is needed before the food can be created; the numbers are already in.
        composeRule.onNodeWithText("Create and add").performScrollTo().assertExists()
        composeRule.onNodeWithText("Check this one").assertDoesNotExist()
    }

    @Test
    fun thePhotoScreenGuidesTheShotAndExplainsAFailure() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LabelPhotoScreen(LabelPhotoState.Capturing, CameraAccess.READY, camera = { ScanFrame(it) }, onTakePhoto = { events += DietEvent.LabelPhotoTaken(null) }, onAskPermission = {}, onOpenSettings = {}, onEvent = { events += it })
            }
        }
        composeRule.onNodeWithText("Fill the frame with the nutrition table.", substring = true).assertExists()
        composeRule.onNodeWithText("never saved or sent", substring = true).assertExists()
        composeRule.onNodeWithText("Take photo").performClick()
        assertThat(events.single()).isEqualTo(DietEvent.LabelPhotoTaken(null))
    }

    @Test
    fun aFailureOffersTryAgain() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LabelPhotoScreen(LabelPhotoState.Failed(LabelPhotoState.Reason.NOTHING_READ), CameraAccess.READY, camera = { ScanFrame(it) }, onTakePhoto = {}, onAskPermission = {}, onOpenSettings = {}, onEvent = { events += it })
            }
        }
        composeRule.onNodeWithText("No text could be read.", substring = true).assertExists()
        composeRule.onNodeWithText("Try again").performClick()
        assertThat(events.single()).isEqualTo(DietEvent.LabelPhotoRetry)
    }
}
